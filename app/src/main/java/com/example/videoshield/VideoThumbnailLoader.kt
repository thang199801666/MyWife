package com.example.videoshield

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ComponentCallbacks2
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URI
import java.lang.ref.WeakReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Small, bounded image cache; reused rows never receive another video's image. */
class VideoThumbnailLoader {
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    // One decoder avoids parallel bitmap allocations and an extra native thread stack;
    // the queue keeps scrolling non-blocking while memory use stays bounded.
    private val worker = ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, ArrayBlockingQueue(32)).apply {
        allowCoreThreadTimeOut(true)
    }
    @Volatile private var closed = false
    @Volatile private var generation = 0
    private val validVideoId = Regex("[A-Za-z0-9_-]{11}")
    private val requests = mutableMapOf<String, MutableList<WeakReference<ImageView>>>()

    fun bind(view: ImageView, videoId: String) {
        val sameVideo = view.tag == videoId
        view.tag = videoId
        if (!sameVideo) view.setImageDrawable(null)
        if (!validVideoId.matches(videoId) || closed) return
        synchronized(cache) { cache.get(videoId) }?.let { view.setImageBitmap(it); return }
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
        val requestGeneration = generation
        runCatching { worker.execute {
            val needed = synchronized(requests) { requests[videoId]?.any { it.get() != null } == true }
            if (closed || requestGeneration != generation || !needed) {
                synchronized(requests) { requests.remove(videoId) }
                return@execute
            }
            val bitmap = runCatching {
                val connection = URI("https://i.ytimg.com/vi/$videoId/mqdefault.jpg").toURL().openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.instanceFollowRedirects = false
                    if (connection.responseCode != 200) null else {
                        val length = connection.contentLengthLong
                        if (length > 512L * 1024L) null else connection.inputStream.use { input ->
                            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                                // List thumbnails do not need 32-bit pixels. RGB_565 halves the
                                // retained native bitmap allocation and decodeStream avoids a
                                // second full compressed-byte copy in memory.
                                inPreferredConfig = Bitmap.Config.RGB_565
                                inDither = true
                            })
                        }
                    }
                } finally { connection.disconnect() }
            }.getOrNull()
            // Cache before releasing the in-flight key so another bind cannot
            // start a duplicate request between completion and UI delivery.
            val subscribers = synchronized(requests) {
                if (bitmap != null && !closed && requestGeneration == generation) synchronized(cache) { cache.put(videoId, bitmap) }
                requests.remove(videoId).orEmpty()
            }
            if (bitmap != null && !closed && requestGeneration == generation) main.post {
                if (!closed && requestGeneration == generation) subscribers.forEach { reference ->
                    reference.get()?.takeIf { it.tag == videoId }?.setImageBitmap(bitmap)
                }
            }
        } }.onFailure { synchronized(requests) { requests.remove(videoId) } }
    }

    fun trimMemory(level: Int) {
        if (closed) return
        // Thumbnail bitmaps are disposable. Drop them aggressively once this UI is hidden
        // and shrink the cache under foreground memory pressure. Incrementing generation
        // prevents an already-running decode from repopulating the cache after a trim.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            generation++
            worker.queue.clear()
            worker.purge()
            synchronized(requests) { requests.clear() }
            synchronized(cache) { cache.evictAll() }
            return
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            generation++
            worker.queue.clear()
            worker.purge()
            synchronized(requests) { requests.clear() }
            synchronized(cache) { cache.trimToSize(512 * 1024) }
        }
    }

    fun close() {
        closed = true
        generation++
        worker.shutdownNow()
        synchronized(requests) { requests.clear() }
        synchronized(cache) { cache.evictAll() }
        main.removeCallbacksAndMessages(null)
    }
}
