package com.example.videoshield

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.net.ssl.HttpsURLConnection

/** The packaged engine is usable immediately; network updates never hold up a download. */
internal object DownloadEngineUpdate {
    const val BUNDLED_VERSION = "2026.08.19"
    private val checking = AtomicBoolean(false)
    private val worker = ThreadPoolExecutor(1, 1, 20L, TimeUnit.SECONDS, LinkedBlockingQueue()).apply {
        allowCoreThreadTimeOut(true)
    }
    private fun binary(context: Context) = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")

    fun installBundle(context: Context) {
        val prefs = context.getSharedPreferences("download_engine", Context.MODE_PRIVATE)
        val installed = DownloadPolicy.engineVersion(prefs.getString("version", null)
            ?: com.yausername.youtubedl_android.YoutubeDL.getInstance().versionName(context).orEmpty())
        if (installed < BUNDLED_VERSION || !binary(context).isFile) {
            context.resources.openRawResource(R.raw.ytdlp).use { input ->
                replace(binary(context)) { output -> input.copyTo(output) }
            }
            prefs.edit().putString("version", BUNDLED_VERSION).apply()
        } else prefs.edit().putString("version", installed).apply()
    }

    fun check(context: Context, lock: ReentrantReadWriteLock) {
        val prefs = context.getSharedPreferences("download_engine", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("checked", 0) < 86_400_000 ||
            now - prefs.getLong("attempted", 0) < 3_600_000 || !checking.compareAndSet(false, true)) return
        worker.execute {
            var staged: File? = null
            try {
                prefs.edit().putLong("attempted", now).apply()
                val deadline = android.os.SystemClock.elapsedRealtime() + 30_000
                val release = JSONObject(fetch("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest", 1_048_576, deadline).toString(Charsets.UTF_8))
                val version = release.getString("tag_name")
                require(Regex("\\d{4}\\.\\d{2}\\.\\d{2}").matches(version))
                if (version > prefs.getString("version", BUNDLED_VERSION).orEmpty()) {
                    val assets = release.getJSONArray("assets")
                    val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.first { it.optString("name") == "yt-dlp" }
                    val url = asset.getString("browser_download_url")
                    require(url.startsWith("https://github.com/yt-dlp/yt-dlp/releases/download/$version/"))
                    val expected = asset.getString("digest").removePrefix("sha256:")
                    require(Regex("[0-9a-f]{64}").matches(expected))
                    val bytes = fetch(url, 8 * 1_048_576, deadline)
                    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    require(digest == expected) { "Engine checksum mismatch" }
                    staged = File.createTempFile("ytdlp-update-", ".tmp", context.cacheDir).apply { writeBytes(bytes) }
                    // A running Python process may still import from its zip: never replace it mid-download.
                    if (!lock.writeLock().tryLock()) return@execute
                    try {
                        val candidate = requireNotNull(staged)
                        candidate.inputStream().use { input -> replace(binary(context)) { output -> input.copyTo(output) } }
                        prefs.edit().putString("version", version).apply()
                    } finally { lock.writeLock().unlock() }
                }
                prefs.edit().putLong("checked", System.currentTimeMillis()).remove("update_error").apply()
            } catch (error: Exception) {
                prefs.edit().putString("update_error", error.message.orEmpty().take(500)).apply()
                Log.w("VoTuibeDownload", "Engine update unavailable; keeping installed engine", error)
            } finally { staged?.delete(); checking.set(false) }
        }
    }

    private fun fetch(url: String, limit: Int, deadline: Long): ByteArray {
        val remaining = (deadline - android.os.SystemClock.elapsedRealtime()).coerceAtMost(10_000).toInt()
        require(remaining > 0) { "Engine update timeout" }
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = minOf(5000, remaining)
        connection.readTimeout = remaining
        connection.setRequestProperty("User-Agent", "VoTuibe-downloader")
        try {
            check(connection.responseCode == 200) { "Engine update HTTP ${connection.responseCode}" }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    check(android.os.SystemClock.elapsedRealtime() < deadline) { "Engine update timeout" }
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= limit) { "Engine update too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private fun replace(file: File, write: (java.io.OutputStream) -> Unit) {
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { write(output); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
    }
}
