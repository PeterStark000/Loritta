package net.perfectdreams.loritta.plugin.loriguildstuff.commands

import com.mrpowergamerbr.loritta.utils.extensions.await
import net.perfectdreams.loritta.common.commands.CommandCategory
import net.perfectdreams.loritta.api.messages.LorittaReply
import net.perfectdreams.loritta.platform.discord.LorittaDiscord
import net.perfectdreams.loritta.platform.discord.legacy.commands.discordCommand

object NotifyHungerGamesCommand {
	fun create(loritta: LorittaDiscord) = discordCommand(loritta, listOf("notify hungergames", "notificar hungergames", "notify hg", "notificar hg"), CommandCategory.MISC) {
		this.hideInHelp = true
		this.commandCheckFilter { lorittaMessageEvent, _, _, _, _ ->
			lorittaMessageEvent.guild?.idLong == 297732013006389252L
		}

		executesDiscord {
			val member = this.member!!
			
			val levelRole = guild.getRoleById(655132411566358548L)!!      
			if (!member.roles.contains(levelRole)) {
				reply(
					LorittaReply(
						"Você não pode usar este comando, você precisa ter ao menos nível 10! Qual ir se enturmar com outros membros ein? ;w;",
						"<:lori_reading:853052040430878750>"
					)
				)
				return@executesDiscord
			}
      
			val roleId = 866871498547527720L
			val role = guild.getRoleById(roleId)!!
     
			if (member.roles.contains(role)) {
				guild.removeRoleFromMember(member, role).await()

				reply(
						LorittaReply(
								"Sério mesmo que você não quer mais receber meus incríveis eventos Hunger Games? E eu pensava que nós eramos amigos...",
								"<:lori_sadcraft:370344565967814659>"
						)
				)
			} else {
				guild.addRoleToMember(member, role).await()

				reply(
						LorittaReply(
								"Agora você irá ser notificado cada vez que um evento Hunger Games estiver sendo realizado!",
								"<:lori_feliz:519546310978830355>"
						)
				)
			}
		}
	}
}
