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

class OfflineStore(context: Context) {
    private val root = File(context.filesDir, "offline").apply { mkdirs() }
    private val records = File(root, "records").apply { mkdirs() }
    private val files = File(root, "files").apply { mkdirs() }
    fun directory(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(files,id).apply { mkdirs() }
    }
    fun put(job: OfflineDownload) = synchronized(lock) {
        require(job.id.matches(Regex("[a-f0-9-]{36}")))
        val json = JSONObject().apply {
            put("id",job.id); put("url",job.url); put("title",job.title); put("temporary",job.temporary)
            put("mp3",job.mp3); put("selector",job.selector); put("audioQuality",job.audioQuality)
            put("quality",job.quality); put("createdAt",job.createdAt); put("status",job.status)
            put("progress",job.progress); put("completedAt",job.completedAt); put("uri",job.uri); put("error",job.error)
        }
        val file = android.util.AtomicFile(File(records,"${job.id}.json"))
        val output = file.startWrite()
        try { output.write(json.toString().toByteArray()); file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }
    fun all(): List<OfflineDownload> = synchronized(lock) {
        records.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { file -> runCatching {
            val j=JSONObject(file.readText())
            OfflineDownload(j.getString("id"),j.getString("url"),j.getString("title"),j.getBoolean("temporary"),j.getBoolean("mp3"),
                j.getString("selector"),j.optString("audioQuality"),j.getString("quality"),j.getLong("createdAt"),
                j.getString("status"),j.optInt("progress"),j.optLong("completedAt"),j.optString("uri"),j.optString("error"))
        }.getOrNull() }.sortedByDescending { it.createdAt }
    }
    fun get(id: String) = all().find { it.id == id }
    fun clearFiles(id: String) = synchronized(lock) {
        val directory = directory(id)
        require(directory.canonicalPath.startsWith(files.canonicalPath + File.separator))
        directory.deleteRecursively()
    }
    fun remove(id: String) = synchronized(lock) {
        clearFiles(id); File(records,"$id.json").delete(); Unit
    }
    fun cleanup(now: Long = System.currentTimeMillis()) = synchronized(lock) {
        all().filter { it.temporary && it.status == "completed" && DownloadPolicy.expired(it.completedAt,now) }.forEach {
            clearFiles(it.id); put(it.copy(status="expired",uri=""))
        }
    }
    companion object { private val lock = Any() }
}
