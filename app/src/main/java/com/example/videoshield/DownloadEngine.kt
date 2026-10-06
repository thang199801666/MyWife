package com.example.videoshield

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.read
import kotlin.concurrent.write

object DownloadEngine {
    private val engineLock = ReentrantReadWriteLock()
    @Volatile private var initialized = false
    private val operations = ConcurrentHashMap<String, AtomicBoolean>()
    fun init(context: Context) {
        val app = context.applicationContext
        if (!initialized) engineLock.write {
            if (!initialized) {
                try {
                    YoutubeDL.getInstance().init(app)
                    FFmpeg.getInstance().init(app)
                    DownloadEngineUpdate.installBundle(app)
                } catch (error: Throwable) {
                    android.util.Log.e("VoTuiDownload", "Download runtime initialization failed", error)
                    throw error
                }
                initialized = true
            }
        }
        DownloadEngineUpdate.check(app, engineLock)
    }
    private fun request(url: String): YoutubeDLRequest {
        require(YouTubeAdapter.isTrustedBridgeUrl(url) && !YouTubeAdapter.videoIdFromUrl(url).isNullOrBlank())
        val videoId = requireNotNull(YouTubeAdapter.videoIdFromUrl(url))
        return YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId").apply {
            addOption("--no-playlist"); addOption("--socket-timeout", "15"); addOption("--retries", "2")
            addOption("--extractor-retries", "1"); addOption("--fragment-retries", "3")
        }
    }
    fun inspect(context: Context, url: String, id: String): DownloadSource {
        return operation(context,id) { cancelled ->
            DownloadSourceRetry.run(cancelled) { alternate ->
                val request = request(url).apply {
                    addOption("--dump-single-json"); addOption("--skip-download")
                    // Inspection must not require a mergeable audio/video pair.
                    addOption("--ignore-no-formats-error")
                    if (alternate != null) addOption("--extractor-args",alternate)
                }
                engineLock.read {
                    if(cancelled()) throw InterruptedException("Cancelled")
                    DownloadPolicy.source(YoutubeDL.getInstance().execute(request, id, null).out)
                }
            }
        }
    }
    fun download(context: Context, job: OfflineDownload, directory: File, progress: (Float) -> Unit): File {
        operation(context,job.id) { cancelled ->
            DownloadSourceRetry.run(cancelled) { alternate ->
                if (alternate != null) {
                    // A new client can select different formats; do not resume the old fragments.
                    directory.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("media.") }
                        .forEach { check(it.delete()) { "Cannot reset partial download" } }
                }
                val request = request(job.url).apply {
                    if (alternate != null) addOption("--extractor-args",alternate)
                    addOption("-f", if(job.mp3) job.selector else DownloadPolicy.videoSelector(job.selector))
                    addOption("-o", File(directory, "media.%(ext)s").absolutePath)
                    addOption("--no-mtime"); addOption("--newline")
                    if (job.mp3) {
                        addOption("-x"); addOption("--audio-format", "mp3"); addOption("--audio-quality", job.audioQuality)
                    } else { addOption("--merge-output-format", "mkv"); addOption("--remux-video", "mkv") }
                }
                engineLock.read {
                    if(cancelled()) throw InterruptedException("Cancelled")
                    YoutubeDL.getInstance().execute(request, job.id) { percent, _, _ -> progress(percent) }
                }
            }
        }
        return File(directory, if (job.mp3) "media.mp3" else "media.mkv").also { require(it.isFile && it.length() > 0) { "Không tìm thấy file hoàn tất." } }
    }
    private fun <T> operation(context: Context, id: String, block: (() -> Boolean) -> T): T {
        val cancelled = AtomicBoolean(false)
        check(operations.putIfAbsent(id,cancelled) == null) { "Download already running" }
        try {
            init(context)
            return block { cancelled.get() || Thread.currentThread().isInterrupted }
        } finally { operations.remove(id,cancelled) }
    }
    fun cancel(id: String) {
        operations[id]?.set(true)
        YoutubeDL.getInstance().destroyProcessById(id)
    }
}
