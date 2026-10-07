package com.example.videoshield

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID


data class OfflineDownload(
    val id: String = UUID.randomUUID().toString(), val url: String, val title: String,
    val temporary: Boolean, val mp3: Boolean, val selector: String, val audioQuality: String,
    val quality: String, val createdAt: Long = System.currentTimeMillis(),
    val status: String = "queued", val progress: Int = 0, val completedAt: Long = 0,
    val uri: String = "", val error: String = ""
)

/**
 * Small durable download store backed by one AtomicFile per job.
 *
 * Download progress is updated frequently. Keep a process-local snapshot and notify in-process
 * observers only after a durable metadata change; this lets the Downloads screen remain fully
 * event-driven instead of polling storage. A process restart naturally rebuilds the snapshot once
 * from disk, so persistence semantics stay unchanged.
 */
class OfflineStore(context: Context) {
    private val root = File(context.filesDir, "offline").apply { mkdirs() }
    private val records = File(root, "records").apply { mkdirs() }
    private val files = File(root, "files").apply { mkdirs() }
    private val recordsPath = records.absolutePath

    fun directory(id: String): File {
        require(ID_PATTERN.matches(id))
        return File(files, id).apply { mkdirs() }
    }

    fun put(job: OfflineDownload) {
        synchronized(lock) {
            require(ID_PATTERN.matches(job.id))
            putLocked(job)
        }
        notifyObservers()
    }

    fun all(): List<OfflineDownload> = synchronized(lock) {
        snapshotLocked().values.sortedByDescending { it.createdAt }
    }

    /** Count without sorting rows or parsing every record when Downloads has not been opened yet. */
    fun count(): Int = synchronized(lock) {
        if (cachedRecordsPath == recordsPath && cacheLoaded) processCache.size
        else records.listFiles().orEmpty().count { it.extension == "json" }
    }

    fun get(id: String): OfflineDownload? = synchronized(lock) {
        if (!ID_PATTERN.matches(id)) return@synchronized null
        snapshotLocked()[id]
    }

    fun clearFiles(id: String) = synchronized(lock) {
        clearFilesLocked(id)
    }

    fun remove(id: String) {
        val changed = synchronized(lock) {
            if (!ID_PATTERN.matches(id)) return@synchronized false
            clearFilesLocked(id)
            val deleted = File(records, "$id.json").delete()
            val removed = snapshotLocked().remove(id) != null
            deleted || removed
        }
        if (changed) notifyObservers()
    }

    fun cleanup(now: Long = System.currentTimeMillis()) {
        val changed = synchronized(lock) {
            val expired = snapshotLocked().values.filter {
                it.temporary && it.status == "completed" && DownloadPolicy.expired(it.completedAt, now)
            }
            expired.forEach { job ->
                clearFilesLocked(job.id)
                putLocked(job.copy(status = "expired", uri = ""))
            }
            expired.isNotEmpty()
        }
        if (changed) notifyObservers()
    }

    /** Process-local change observation. DownloadService and DownloadsActivity share the same
     * process, so UI refreshes can be event-driven instead of polling storage every few seconds. */
    fun observe(onChanged: () -> Unit): AutoCloseable {
        observers.add(onChanged)
        return AutoCloseable { observers.remove(onChanged) }
    }

    private fun putLocked(job: OfflineDownload) {
        val json = JSONObject().apply {
            put("id", job.id); put("url", job.url); put("title", job.title); put("temporary", job.temporary)
            put("mp3", job.mp3); put("selector", job.selector); put("audioQuality", job.audioQuality)
            put("quality", job.quality); put("createdAt", job.createdAt); put("status", job.status)
            put("progress", job.progress); put("completedAt", job.completedAt); put("uri", job.uri); put("error", job.error)
        }
        val file = android.util.AtomicFile(File(records, "${job.id}.json"))
        val output = file.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
            snapshotLocked()[job.id] = job
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }

    private fun read(file: File): OfflineDownload? = runCatching {
        val j = JSONObject(file.readText(Charsets.UTF_8))
        OfflineDownload(
            j.getString("id"), j.getString("url"), j.getString("title"), j.getBoolean("temporary"), j.getBoolean("mp3"),
            j.getString("selector"), j.optString("audioQuality"), j.getString("quality"), j.getLong("createdAt"),
            j.getString("status"), j.optInt("progress"), j.optLong("completedAt"), j.optString("uri"), j.optString("error")
        )
    }.getOrNull()

    private fun snapshotLocked(): MutableMap<String, OfflineDownload> {
        if (cachedRecordsPath == recordsPath && cacheLoaded) return processCache
        processCache.clear()
        records.listFiles().orEmpty().asSequence()
            .filter { it.extension == "json" }
            .mapNotNull(::read)
            .forEach { processCache[it.id] = it }
        cachedRecordsPath = recordsPath
        cacheLoaded = true
        return processCache
    }

    private fun notifyObservers() {
        observers.forEach { observer -> runCatching { observer() } }
    }

    private fun clearFilesLocked(id: String) {
        require(ID_PATTERN.matches(id))
        val directory = File(files, id)
        require(directory.canonicalPath.startsWith(files.canonicalPath + File.separator))
        directory.deleteRecursively()
    }

    companion object {
        private val lock = Any()
        private val ID_PATTERN = Regex("[a-f0-9-]{36}")
        private val processCache = LinkedHashMap<String, OfflineDownload>()
        private val observers = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()
        private var cachedRecordsPath: String? = null
        private var cacheLoaded = false
    }
}
