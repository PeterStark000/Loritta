package net.perfectdreams.loritta.cinnamon.pudding.tables.transactions

import net.perfectdreams.loritta.cinnamon.pudding.tables.SonhosTransactionsLog
import org.jetbrains.exposed.dao.id.LongIdTable

object PowerStreamClaimedFirstSonhosRewardSonhosTransactionsLog : LongIdTable() {
    val timestampLog = reference("timestamp_log", SonhosTransactionsLog).index()
    val sonhos = long("sonhos")
    val streamId = long("stream")
    val liveId = text("live").nullable()
}