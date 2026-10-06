package com.example.videoshield

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URI
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Small, bounded image cache; reused rows never receive another video's image. */
class VideoThumbnailLoader {
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val worker = ThreadPoolExecutor(2, 2, 15, TimeUnit.SECONDS, ArrayBlockingQueue(40))
    @Volatile private var closed = false
    private val validVideoId = Regex("[A-Za-z0-9_-]{11}")
    private val requests = mutableMapOf<String, MutableList<WeakReference<ImageView>>>()

    fun bind(view: ImageView, videoId: String) {
        val sameVideo = view.tag == videoId
        view.tag = videoId
        if (!sameVideo) view.setImageDrawable(null)
        if (!validVideoId.matches(videoId) || closed) return
        cache.get(videoId)?.let { view.setImageBitmap(it); return }
        // A cover and several recycled rows may request the same image together.
        // Keep one download and weak subscribers, so queued work cannot retain views.
        synchronized(requests) {
            val subscribers = requests[videoId]
            if (subscribers != null) {
                subscribers.removeAll { it.get() == null }
                if (subscribers.none { it.get() === view }) subscribers.add(WeakReference(view))
                return
            }
            requests[videoId] = mutableListOf(WeakReference(view))
        }
        runCatching { worker.execute {
            val needed = synchronized(requests) { requests[videoId]?.any { it.get() != null } == true }
            if (closed || !needed) {
                synchronized(requests) { requests.remove(videoId) }
                return@execute
            }
            val bitmap = runCatching {
                val connection = URI("https://i.ytimg.com/vi/$videoId/mqdefault.jpg").toURL().openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.instanceFollowRedirects = false
                    if (connection.responseCode != 200) null else connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= 512 * 1024) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        val bytes = output.toByteArray()
                        if (bytes.size > 512 * 1024) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } finally { connection.disconnect() }
            }.getOrNull()
            // Cache before releasing the in-flight key so another bind cannot
            // start a duplicate request between completion and UI delivery.
            val subscribers = synchronized(requests) {
                if (bitmap != null && !closed) cache.put(videoId, bitmap)
                requests.remove(videoId).orEmpty()
            }
            if (bitmap != null && !closed) main.post {
                if (!closed) subscribers.forEach { reference ->
                    reference.get()?.takeIf { it.tag == videoId }?.setImageBitmap(bitmap)
                }
            }
        } }.onFailure { synchronized(requests) { requests.remove(videoId) } }
    }

    fun close() {
        closed = true
        worker.shutdownNow()
        synchronized(requests) { requests.clear(); cache.evictAll() }
        main.removeCallbacksAndMessages(null)
    }
}
