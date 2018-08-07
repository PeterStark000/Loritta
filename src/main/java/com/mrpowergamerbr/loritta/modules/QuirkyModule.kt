package com.mrpowergamerbr.loritta.modules

import com.mrpowergamerbr.loritta.Loritta.Companion.RANDOM
import com.mrpowergamerbr.loritta.events.LorittaMessageEvent
import com.mrpowergamerbr.loritta.userdata.LorittaProfile
import com.mrpowergamerbr.loritta.userdata.ServerConfig
import com.mrpowergamerbr.loritta.utils.LorittaUser
import com.mrpowergamerbr.loritta.utils.locale.BaseLocale
import net.dv8tion.jda.core.Permission

class QuirkyModule : MessageReceivedModule {
	override fun matches(event: LorittaMessageEvent, lorittaUser: LorittaUser, lorittaProfile: LorittaProfile, serverConfig: ServerConfig, locale: BaseLocale): Boolean {
		return serverConfig.miscellaneousConfig.enableQuirky && event.guild?.selfMember?.hasPermission(event.textChannel, Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_EXT_EMOJI) == true
	}

	override fun handle(event: LorittaMessageEvent, lorittaUser: LorittaUser, lorittaProfile: LorittaProfile, serverConfig: ServerConfig, locale: BaseLocale): Boolean {
		// uwu u are sooo quirky
		val message = event.message

		val random = RANDOM.nextInt(0, 250)

		when (random) {
			0 -> message.addReaction("ata:339904769139146755").queue()
			1 -> message.addReaction("daora:375321168632086529").queue()
			2 -> message.addReaction("wow:432531424671694849").queue()
			3 -> message.addReaction("rip:473621981619552267").queue()
			4 -> message.addReaction("osama:325332212255948802").queue()
			5 -> message.addReaction("a:revolving_think:417382964364836864").queue()
			6 -> message.addReaction("thonk:413425726369431552").queue()
			7 -> message.addReaction("pepe_morre_diabo:473622072770297857").queue()
			8 -> message.addReaction("MEGATHINK:413425816500699136").queue()
			9 -> message.addReaction("lori_ok_hand:426183783008698391").queue()
			10 -> message.addReaction("demencia:302228166314033152").queue()
			11 -> message.addReaction("eu_te_moido:366047906689581085").queue()
			12 -> message.addReaction("faustao_thinking:334378424091017218").queue()
			13 -> message.addReaction("hyper_NOSA:450476856303419432").queue()
			14 -> message.addReaction("idai:334709223588102144").queue()
		}

		if (random in 0 until 75 && message.contentRaw.contains("sparklypower", true)) {
			event.channel.sendMessage(when (random) {
				in 0 until 25 -> "${event.author.asMention} sabia que o dono do SparklyPower foi quem me criou? <:wow:432531424671694849>"
				in 25 until 50 -> "${event.author.asMention} melhor servidor que eu já conheci... <:lori_triste:370344565967814659> Pelo ou menos o PerfectDreams está por vir, que é um servidor que é parecido com o SparklyPower (e é do mesmo criador!), mas ainda não tá pronto... mas quem sabe em breve https://perfectdreams.net/ <:lori_owo:417813932380520448>"
				else -> "${event.author.asMention} #sdds SparklyPower <:lori_triste:370344565967814659>"
			}).queue()
		}

		return false
	}
}