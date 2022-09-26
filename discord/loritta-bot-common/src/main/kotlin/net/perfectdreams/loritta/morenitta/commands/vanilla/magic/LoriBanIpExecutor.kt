package net.perfectdreams.loritta.morenitta.commands.vanilla.magic

import net.perfectdreams.loritta.morenitta.network.Databases
import net.perfectdreams.loritta.morenitta.api.commands.CommandContext
import net.perfectdreams.loritta.morenitta.messages.LorittaReply
import net.perfectdreams.loritta.morenitta.tables.BannedIps
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

object LoriBanIpExecutor : LoriToolsCommand.LoriToolsExecutor {
	override val args = "ban ip <ip> <reason>"

	override fun executes(): suspend CommandContext.() -> Boolean = task@{
		if (args.getOrNull(0) != "ban")
			return@task false
		if (args.getOrNull(1) != "ip")
			return@task false
		val ip = args[2]
		val reason = args.drop(3).joinToString(" ")

		transaction(Databases.loritta) {
			BannedIps.insert {
				it[BannedIps.ip] = ip
				it[bannedAt] = System.currentTimeMillis()
				it[BannedIps.reason] = reason
			}
		}

		reply(
				LorittaReply(
						"IP banido!"
				)
		)
		return@task true
	}
}