package net.perfectdreams.loritta.morenitta.platform.discord.utils

import com.neovisionaries.ws.client.OpeningHandshakeException
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import io.ktor.http.userAgent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.utils.SessionController
import net.dv8tion.jda.api.utils.SessionController.SessionConnectNode
import net.dv8tion.jda.api.utils.SessionControllerAdapter
import net.perfectdreams.loritta.morenitta.LorittaBot
import net.perfectdreams.loritta.morenitta.listeners.PreStartGatewayEventReplayListener
import net.perfectdreams.loritta.morenitta.utils.NetAddressUtils
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Session Controller for bots migrated to the "Very Large Bots" sharding system
 *
 * This controller asks to the master shard controller if a specific shard can login.
 *
 * Thanks Nik#1234 and Xavinlol#0001 for the help!
 */
class LoriMasterShardControllerSessionControllerAdapter(val loritta: LorittaBot) : SessionControllerAdapter() {
	override fun runWorker() {
		synchronized(lock) {
			if (workerHandle == null) {
				workerHandle = QueueWorker()
				workerHandle!!.start()
			}
		}
	}

	/**
	 * Creates a QueueWorker
	 *
	 * @param delay
	 * delay (in milliseconds) to wait between starting sessions
	 */
	private inner class QueueWorker(
		/** Delay (in milliseconds) to sleep between connecting sessions  */
		protected val delay: Long
	) : Thread("SessionControllerAdapter-Worker") {
		/**
		 * Creates a QueueWorker
		 *
		 * @param delay
		 * delay (in seconds) to wait between starting sessions
		 */
		@JvmOverloads
		constructor(delay: Int = SessionController.IDENTIFY_DELAY) : this(TimeUnit.SECONDS.toMillis(delay.toLong()))

		protected fun handleFailure(thread: Thread?, exception: Throwable?) {
			log.error("Worker has failed with throwable!", exception)
		}

		override fun run() {
			try {
				if (delay > 0) {
					val interval = System.currentTimeMillis() - lastConnect
					if (interval < delay) sleep(delay - interval)
				}
			} catch (ex: InterruptedException) {
				log.error("Unable to backoff", ex)
			}
			processQueue()
			synchronized(lock) {
				workerHandle = null
				if (!connectQueue.isEmpty()) runWorker()
			}
		}

		protected fun processQueue() {
			val reconnectingShards = LinkedBlockingQueue<SessionConnectNode>()
			val startingFromScratchShards = LinkedBlockingQueue<SessionConnectNode>()

			// Prioritize shards that are reconnecting
			while (connectQueue.isNotEmpty()) {
				val node = connectQueue.poll()
				if (node.isReconnect || loritta.preLoginStates[node.shardInfo.shardId]?.value == PreStartGatewayEventReplayListener.ProcessorState.WAITING_FOR_WEBSOCKET_CONNECTION) {
					reconnectingShards.add(node)
				} else {
					startingFromScratchShards.add(node)
				}
			}

			while (reconnectingShards.isNotEmpty()) {
				val node = reconnectingShards.poll()

				// Just a shard resuming
				try {
					node.run(false)
					lastConnect = System.currentTimeMillis()
				} catch (e: IllegalStateException) {
					val t = e.cause
					if (t is OpeningHandshakeException) log.error("Failed opening handshake, appending to queue. Message: {}", e.message) else log.error("Failed to establish connection for a node, appending to queue", e)
					appendSession(node)
				} catch (e: InterruptedException) {
					log.error("Failed to run node", e)
					appendSession(node)
					return  // caller should start a new thread
				}
			}

			while (startingFromScratchShards.isNotEmpty()) {
				val node = reconnectingShards.poll()

				fun setLoginPoolLockToShardController(): ControllerResponseType {
					return runBlocking {
						try {
							val status = loritta.http.put("http://${NetAddressUtils.fixIp(loritta, loritta.config.loritta.discord.shardController.url)}/api/v1/shard/${node.shardInfo.shardId}") {
								userAgent(loritta.lorittaCluster.getUserAgent(loritta))
							}.status

							if (status == HttpStatusCode.OK)
								ControllerResponseType.OK
							else if (status == HttpStatusCode.Conflict)
								ControllerResponseType.CONFLICT
							else {
								log.error("Weird status code while fetching shard ${node.shardInfo.shardId} login pool status, status code: ${status}")
								ControllerResponseType.OFFLINE
							}
						} catch (e: Exception) {
							log.error("Exception while checking if shard ${node.shardInfo.shardId} can login", e)
							ControllerResponseType.OFFLINE
						}
					}
				}

				fun removeLoginPoolLockFromShardController() {
					runBlocking {
						try {
							loritta.http.delete("http://${NetAddressUtils.fixIp(loritta, loritta.config.loritta.discord.shardController.url)}/api/v1/shard/${node.shardInfo.shardId}") {
								userAgent(loritta.lorittaCluster.getUserAgent(loritta))
							}
						} catch (e: Exception) {
							log.error("Exception while telling master shard controller that shard ${node.shardInfo.shardId} already logged in! Other clusters may have temporary issues while logging in...", e)
						}
					}
				}

				val canLogin = setLoginPoolLockToShardController()

				if (canLogin == ControllerResponseType.CONFLICT) {
					log.info("Shard ${node.shardInfo.shardId} (login pool: ${node.shardInfo.shardId % 16}) can't login! Another cluster is logging in that shard, delaying login...")
					if (delay > 0) sleep(delay)
					appendSession(node)
					continue
				}

				try {
					node.run(false)

					lastConnect = System.currentTimeMillis()
					if (delay > 0) sleep(delay)
					removeLoginPoolLockFromShardController()
				} catch (e: IllegalStateException) {
					val t = e.cause
					if (t is OpeningHandshakeException) log.error("Failed opening handshake, appending to queue. Message: {}", e.message) else log.error("Failed to establish connection for a node, appending to queue", e)
					appendSession(node)
					removeLoginPoolLockFromShardController()
				} catch (e: InterruptedException) {
					log.error("Failed to run node", e)
					appendSession(node)
					removeLoginPoolLockFromShardController()
					return  // caller should start a new thread
				}
			}

			// Then we do this all over again!
			if (connectQueue.isNotEmpty())
				processQueue()
		}

		init {
			super.setUncaughtExceptionHandler { thread: Thread?, exception: Throwable? -> handleFailure(thread, exception) }
		}
	}

	enum class ControllerResponseType {
		OK,
		CONFLICT,
		OFFLINE
	}
}