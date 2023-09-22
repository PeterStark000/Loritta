package net.perfectdreams.loritta.cinnamon.dashboard.frontend.utils

import io.ktor.client.request.*
import io.ktor.client.statement.*
import js.typedarrays.Uint8Array
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import net.perfectdreams.loritta.cinnamon.dashboard.frontend.LorittaDashboardFrontend
import org.w3c.dom.Audio
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

class LazySoundEffect(val m: LorittaDashboardFrontend, val url: String) {
    private var objUrl: String? = null
    private val mutex = Mutex()

    fun play(
        volume: Double,
        playbackRate: Double = 1.0
    ) {
        GlobalScope.launch {
            mutex.lock()

            val cachedObjUrl = objUrl

            // Already cached, just unlock and play!
            if (cachedObjUrl != null) {
                mutex.unlock()
                println("Playing cached objUrl for $url...")
                Audio(cachedObjUrl).apply {
                    this.volume = volume
                    this.playbackRate = playbackRate
                    this.asDynamic().preservesPitch = false
                }.play()
                return@launch
            } else {
                println("Cached objUrl is not present for $url, downloading...")
                // Oof, not cached yet!
                val errorAsByteArray = m.http.get(url)
                    .readBytes()

                val blob = Blob(arrayOf(Uint8Array(errorAsByteArray.toTypedArray()).buffer), BlobPropertyBag(type = "audio/ogg"))
                val objUrl = URL.createObjectURL(blob)
                println("Successfully cached objUrl for $url!")
                this@LazySoundEffect.objUrl = objUrl
                mutex.unlock()

                // Play!
                Audio(objUrl).apply {
                    this.volume = volume
                    this.playbackRate = playbackRate
                    this.asDynamic().preservesPitch = false
                }.play()
            }
        }
    }
}