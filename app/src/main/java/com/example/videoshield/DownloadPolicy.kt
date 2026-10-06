package com.example.videoshield

import org.json.JSONObject

data class DownloadQuality(val label: String, val selector: String, val audioQuality: String = "")
data class DownloadSource(val title: String, val qualities: List<DownloadQuality>)

object DownloadPolicy {
    fun engineVersion(value: String) = Regex("\\b\\d{4}\\.\\d{2}\\.\\d{2}\\b").find(value)?.value.orEmpty()
    const val TEMPORARY_MS = 30L * 24 * 60 * 60 * 1000
    fun expired(completedAt: Long, now: Long) = completedAt > 0 && now - completedAt >= TEMPORARY_MS
    fun safeName(title: String) = title.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_").trim().trim('.').take(100).ifBlank { "VoTuibe" }
    fun source(json: String): DownloadSource {
        val info = JSONObject(json)
        require(!info.optBoolean("is_live") && info.optString("live_status") != "is_live") { "Chưa hỗ trợ lưu livestream đang phát." }
        val formats = info.optJSONArray("formats") ?: error("Nguồn chưa cung cấp định dạng tải.")
        val heights = mutableSetOf<Int>()
        var hasVideo = false
        var unknownHeight = false
        for (index in 0 until formats.length()) {
            val format = formats.optJSONObject(index) ?: continue
            val codec = format.optString("vcodec", "none")
            if (format.optBoolean("has_drm") || codec in setOf("none", "null", "") ||
                format.optString("protocol") == "mhtml" || format.optString("ext") == "mhtml") continue
            hasVideo = true
            val height = format.optInt("height")
            if(height in 1..16384) heights.add(height) else unknownHeight = true
        }
        require(hasVideo) { "Không có nguồn video tải được." }
        val highest = if(unknownHeight) "Highest source" else "Highest source (${heights.max()}p)"
        return DownloadSource(info.optString("title", "Video"), listOf(DownloadQuality(highest, videoSelector("bestvideo+bestaudio/best"))) +
            heights.sortedDescending().map { DownloadQuality("${it}p", videoSelector("bestvideo[height<=$it]+bestaudio/best[height<=$it]")) })
    }
    /** Upgrade old persisted selectors too, retaining the selected resolution ceiling. */
    fun videoSelector(value: String): String {
        if(value == "bestvideo+bestaudio/best") return "bestvideo*+bestaudio/best[vcodec!=none]/bestvideo"
        val height = Regex("^bestvideo\\[height<=(\\d+)\\]\\+bestaudio/best\\[height<=\\1\\]$")
            .matchEntire(value)?.groupValues?.get(1) ?: return value
        return "bestvideo*[height<=$height]+bestaudio/best[height<=$height][vcodec!=none]/bestvideo[height<=$height]"
    }
    val mp3Qualities = listOf(DownloadQuality("Best source → MP3 (VBR)", "bestaudio/best", "0"),
        DownloadQuality("MP3 320 kbps", "bestaudio/best", "320K"), DownloadQuality("MP3 192 kbps", "bestaudio/best", "192K"),
        DownloadQuality("MP3 128 kbps", "bestaudio/best", "128K"))
}
