package net.perfectdreams.loritta.morenitta.website.routes

import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.set
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.perfectdreams.loritta.morenitta.utils.Constants
import net.perfectdreams.loritta.morenitta.utils.encodeToUrl
import net.perfectdreams.loritta.morenitta.utils.lorittaShards
import net.perfectdreams.loritta.morenitta.website.LorittaWebsite
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.delay
import mu.KotlinLogging
import net.dv8tion.jda.api.Permission
import net.perfectdreams.loritta.common.locale.BaseLocale
import net.perfectdreams.loritta.morenitta.LorittaBot
import net.perfectdreams.loritta.morenitta.tables.BannedUsers
import net.perfectdreams.loritta.morenitta.tables.BlacklistedGuilds
import net.perfectdreams.loritta.morenitta.utils.DiscordUtils
import net.perfectdreams.loritta.common.utils.Emotes
import net.perfectdreams.loritta.morenitta.website.session.LorittaJsonWebSession
import net.perfectdreams.loritta.morenitta.website.utils.WebsiteUtils
import net.perfectdreams.loritta.morenitta.website.utils.extensions.hostFromHeader
import net.perfectdreams.loritta.morenitta.website.utils.extensions.lorittaSession
import net.perfectdreams.loritta.morenitta.website.utils.extensions.redirect
import net.perfectdreams.loritta.morenitta.website.utils.extensions.respondHtml
import net.perfectdreams.loritta.morenitta.website.utils.extensions.toJson
import net.perfectdreams.loritta.morenitta.website.utils.extensions.toWebSessionIdentification
import net.perfectdreams.loritta.morenitta.website.views.UserBannedView
import net.perfectdreams.temmiediscordauth.TemmieDiscordAuth
import org.jetbrains.exposed.sql.select
import java.util.*

abstract class RequiresDiscordLoginLocalizedRoute(loritta: LorittaBot, path: String) : LocalizedRoute(loritta, path) {
	companion object {
		private val logger = KotlinLogging.logger {}
	}

	abstract suspend fun onAuthenticatedRequest(call: ApplicationCall, locale: BaseLocale, discordAuth: TemmieDiscordAuth, userIdentification: LorittaJsonWebSession.UserIdentification)

	override suspend fun onLocalizedRequest(call: ApplicationCall, locale: BaseLocale) {
		loritta as LorittaBot

		if (call.request.path().endsWith("/dashboard")) {
			val hostHeader = call.request.hostFromHeader()
			val scheme = LorittaWebsite.WEBSITE_URL.split(":").first()

			val state = call.parameters["state"]
			val guildId = call.parameters["guild_id"]
			val code = call.parameters["code"]

			println("Dashboard Auth Route")
			val session: LorittaJsonWebSession = call.sessions.get<LorittaJsonWebSession>() ?: LorittaJsonWebSession.empty()
			val discordAuth = session.getDiscordAuthFromJson()

			// Caso o usuário utilizou o invite link que adiciona a Lori no servidor, terá o parâmetro "guild_id" na URL
			// Se o parâmetro exista, vamos redirecionar!
			if (code == null) {
				if (discordAuth == null) {
					if (call.request.header("User-Agent") == Constants.DISCORD_CRAWLER_USER_AGENT) {
						call.respondHtml(WebsiteUtils.getDiscordCrawlerAuthenticationPage())
					} else {
						val state = JsonObject()
						state["redirectUrl"] = "$scheme://$hostHeader" + call.request.path()
						redirect(net.perfectdreams.loritta.morenitta.utils.loritta.discordInstanceConfig.discord.authorizationUrl + "&state=${Base64.getEncoder().encodeToString(state.toString().toByteArray()).encodeToUrl()}", false)
					}
				}
			} else {
				val storedUserIdentification = session.getUserIdentification(call)

				val userIdentification = if (code == "from_master") {
					// Veio do master cluster, vamos apenas tentar autenticar com os dados existentes!
					storedUserIdentification ?: run {
						// Okay... mas e se for nulo? Veio do master mas não tem session cache? Como pode??
						// Iremos apenas pedir para o usuário reautenticar, porque alguma coisa deu super errado!
						val state = JsonObject()
						state["redirectUrl"] = "$scheme://$hostHeader" + call.request.path()
						redirect(net.perfectdreams.loritta.morenitta.utils.loritta.discordInstanceConfig.discord.authorizationUrl + "&state=${Base64.getEncoder().encodeToString(state.toString().toByteArray()).encodeToUrl()}", false)
					}
				} else {
					val auth = TemmieDiscordAuth(
						net.perfectdreams.loritta.morenitta.utils.loritta.discordConfig.discord.clientId,
						net.perfectdreams.loritta.morenitta.utils.loritta.discordConfig.discord.clientSecret,
						code,
						"$scheme://$hostHeader/dashboard",
						listOf("identify", "guilds", "email")
					)

					auth.doTokenExchange()
					val userIdentification = auth.getUserIdentification()
					val forCache = userIdentification.toWebSessionIdentification()
					call.sessions.set(
						session.copy(
							cachedIdentification = forCache.toJson(),
							storedDiscordAuthTokens = auth.toJson()
						)
					)

					forCache
				}

				// Verificar se o usuário é (possivelmente) alguém que foi banido de usar a Loritta
				/* val trueIp = call.request.trueIp
				val dailiesWithSameIp = loritta.newSuspendedTransaction {
					Dailies.select {
						(Dailies.ip eq trueIp)
					}.toMutableList()
				}

				val userIds = dailiesWithSameIp.map { it[Dailies.id] }.distinct()

				val bannedProfiles = loritta.newSuspendedTransaction {
					Profiles.select { Profiles.id inList userIds and Profiles.isBanned }
							.toMutableList()
				}

				if (bannedProfiles.isNotEmpty())
					logger.warn { "User ${userIdentification.id} has banned accounts in ${trueIp}! IDs: ${bannedProfiles.joinToString(transform = { it[Profiles.id].toString() })}" } */

				if (state != null) {
					// state = base 64 encoded JSON
					val decodedState = Base64.getDecoder().decode(state).toString(Charsets.UTF_8)
					val jsonState = JsonParser.parseString(decodedState).obj
					val redirectUrl = jsonState["redirectUrl"].nullString

					if (redirectUrl != null) {
						// Check if we are redirecting to Loritta's trusted URLs
						val lorittaDomain = loritta.connectionManager.getDomainFromUrl(loritta.instanceConfig.loritta.website.url)
						val redirectDomain = loritta.connectionManager.getDomainFromUrl(redirectUrl)

						if (lorittaDomain == redirectDomain)
							redirect(redirectUrl, false)
						else
							logger.warn { "Someone tried to make me redirect to somewhere that isn't my website domain! Tried to redirect to $redirectDomain" }
					}
				}

				if (guildId != null) {
					if (code != "from_master") {
						val cluster = DiscordUtils.getLorittaClusterForGuildId(guildId.toLong())

						if (cluster.getUrl() != hostHeader) {
							logger.info { "Received guild $guildId via OAuth2 scope, but the guild isn't in this cluster! Redirecting to where the user should be... $cluster" }

							// Vamos redirecionar!
							redirect("$scheme://${cluster.getUrl()}/dashboard?guild_id=${guildId}&code=from_master", true)
						}
					}

					logger.info { "Received guild $guildId via OAuth2 scope, sending DM to the guild owner..." }
					var guildFound = false
					var tries = 0
					val maxGuildTries = net.perfectdreams.loritta.morenitta.utils.loritta.config.loritta.website.maxGuildTries

					while (!guildFound && maxGuildTries > tries) {
						val guild = lorittaShards.getGuildById(guildId)

						if (guild != null) {
							logger.info { "Guild ${guild} was successfully found after $tries tries! Yay!!" }

							val serverConfig = net.perfectdreams.loritta.morenitta.utils.loritta.getOrCreateServerConfig(guild.idLong)

							// Now we are going to save the server's new locale ID, based on the user's locale
							// This fixes issues because Discord doesn't provide the voice channel server anymore
							// (which, well, was already a huge workaround anyway)
							loritta.newSuspendedTransaction {
								serverConfig.localeId = locale.id
							}

							val userId = userIdentification.id

							val user = lorittaShards.retrieveUserById(userId)

							if (user != null) {
								val member = guild.getMember(user)

								if (member != null) {
									// E, se o membro não for um bot e possui permissão de gerenciar o servidor ou permissão de administrador...
									if (!user.isBot && (member.hasPermission(Permission.MANAGE_SERVER) || member.hasPermission(Permission.ADMINISTRATOR))) {
										// Verificar coisas antes de adicionar a Lori
										val blacklisted = loritta.newSuspendedTransaction {
											BlacklistedGuilds.select {
												BlacklistedGuilds.id eq guild.idLong
											}.firstOrNull()
										}

										if (blacklisted != null) {
											val blacklistedReason = blacklisted[BlacklistedGuilds.reason]

											// Envie via DM uma mensagem falando sobre o motivo do ban
											val message = locale.getList("website.router.blacklistedServer", blacklistedReason)

											user.openPrivateChannel().queue {
												it.sendMessage(message.joinToString("\n")).queue({
													guild.leave().queue()
												}, {
													guild.leave().queue()
												})
											}
											return
										}

										val guildOwner = guild.owner

										// Sometimes the guild owner can be null, that's why we need to check if it is null or not!
										if (guildOwner != null) {
											val profile = loritta.getLorittaProfile(guildOwner.user.id)
											val bannedState = profile?.getBannedState()
											if (bannedState != null) { // Dono blacklisted
												// Envie via DM uma mensagem falando sobre a Loritta!
												val message = locale.getList("website.router.ownerLorittaBanned", guild.owner?.user?.asMention, bannedState[BannedUsers.reason]
													?: "???").joinToString("\n")

												user.openPrivateChannel().queue {
													it.sendMessage(message).queue({
														guild.leave().queue()
													}, {
														guild.leave().queue()
													})
												}
												return
											}

											// Envie via DM uma mensagem falando sobre a Loritta!
											val message = locale.getList(
												"website.router.addedOnServer",
												user.asMention,
												guild.name,
												net.perfectdreams.loritta.morenitta.utils.loritta.instanceConfig.loritta.website.url + "commands",
												net.perfectdreams.loritta.morenitta.utils.loritta.instanceConfig.loritta.website.url + "guild/${guild.id}/configure/",
												net.perfectdreams.loritta.morenitta.utils.loritta.instanceConfig.loritta.website.url + "guidelines",
												net.perfectdreams.loritta.morenitta.utils.loritta.instanceConfig.loritta.website.url + "donate",
												net.perfectdreams.loritta.morenitta.utils.loritta.instanceConfig.loritta.website.url + "support",
												Emotes.LORI_PAT,
												Emotes.LORI_NICE,
												Emotes.LORI_HEART,
												Emotes.LORI_COFFEE,
												Emotes.LORI_SMILE,
												Emotes.LORI_PRAY,
												Emotes.LORI_BAN_HAMMER,
												Emotes.LORI_RICH,
												Emotes.LORI_HEART1.toString() + Emotes.LORI_HEART2.toString()
											).joinToString("\n")

											user.openPrivateChannel().queue {
												it.sendMessage(message).queue()
											}
										}
									}
								}
							}
							guildFound = true // Servidor detectado, saia do loop!
						} else {
							tries++
							logger.warn { "Received guild $guildId via OAuth2 scope, but I'm not in that guild yet! Waiting for 1s... Tries: ${tries}" }
							delay(1_000)
						}
					}

					if (tries == maxGuildTries) {
						// oof
						logger.warn { "Received guild $guildId via OAuth2 scope, we tried ${maxGuildTries} times, but I'm not in that guild yet! Telling the user about the issue..." }

						call.respondHtml(
							"""
							|<p>Parece que você tentou me adicionar no seu servidor, mas mesmo assim eu não estou nele!</p>
							|<ul>
							|<li>Tente me readicionar, as vezes isto acontece devido a um delay entre o tempo até o Discord atualizar os servidores que eu estou. <a href="$scheme://loritta.website/dashboard">$scheme://loritta.website/dashboard</a></li>
							|<li>
							|Verifique o registro de auditoria do seu servidor, alguns bots expulsam/banem ao adicionar novos bots. Caso isto tenha acontecido, expulse o bot que me puniu e me readicione!
							|<ul>
							|<li>
							|<b>Em vez de confiar em um bot para "proteger" o seu servidor:</b> Veja quem possui permissão de administrador ou de gerenciar servidores no seu servidor, eles são os únicos que conseguem adicionar bots no seu servidor. Existem boatos que existem "bugs que permitem adicionar bots sem permissão", mas isto é mentira.
							|</li>
							|</ul>
							|</li>
							|</ul>
							|<p>Desculpe pela inconveniência ;w;</p>
						""".trimMargin())
						return
					}

					redirect("$scheme://$hostHeader/guild/${guildId}/configure", false)
					return
				}

				redirect("$scheme://$hostHeader/dashboard", false) // Redirecionar para a dashboard, mesmo que nós já estejamos lá... (remove o "code" da URL)
			}
		}

		var start = System.currentTimeMillis()
		val session = call.lorittaSession
		logger.info { "Time to get session: ${System.currentTimeMillis() - start}" }
		start = System.currentTimeMillis()

		val discordAuth = session.getDiscordAuthFromJson()
		logger.info { "Time to get Discord Auth: ${System.currentTimeMillis() - start}" }
		start = System.currentTimeMillis()
		val userIdentification = session.getUserIdentification(call)
		logger.info { "Time to get User Identification: ${System.currentTimeMillis() - start}" }

		if (discordAuth == null || userIdentification == null) {
			onUnauthenticatedRequest(call, locale)
			return
		}

		val profile = net.perfectdreams.loritta.morenitta.utils.loritta.getOrCreateLorittaProfile(userIdentification.id)
		val bannedState = profile.getBannedState()
		if (bannedState != null) {
			call.respondHtml(
				UserBannedView(
					locale,
					getPathWithoutLocale(call),
					profile,
					bannedState
				).generateHtml()
			)
			return
		}

		onAuthenticatedRequest(call, locale, discordAuth, userIdentification)
	}

	open suspend fun onUnauthenticatedRequest(call: ApplicationCall, locale: BaseLocale) {
		// redirect to authentication owo
		val state = JsonObject()
		state["redirectUrl"] = LorittaWebsite.WEBSITE_URL.substring(0, LorittaWebsite.Companion.WEBSITE_URL.length - 1) + call.request.path()
		redirect(net.perfectdreams.loritta.morenitta.utils.loritta.discordInstanceConfig.discord.authorizationUrl + "&state=${Base64.getEncoder().encodeToString(state.toString().toByteArray()).encodeToUrl()}", false)
	}
}