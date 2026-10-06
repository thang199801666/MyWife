package com.example.videoshield

import org.json.JSONArray
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

data class CommunitySegment(
    val startMs: Long,
    val endMs: Long,
    val category: String
)

/**
 * Privacy-conscious SponsorBlock-compatible segment lookup.
 *
 * Requests use the documented SHA-256 prefix endpoint rather than sending the
 * complete YouTube video id. The returned candidate set is filtered locally by
 * full hash before any segment is exposed to playback code.
 */
class CommunitySegmentClient {
    private val executor = ThreadPoolExecutor(
        1, 1, 20L, TimeUnit.SECONDS, ArrayBlockingQueue(2),
        { runnable -> Thread(runnable, "YouTooBee-SegmentLookup").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy()
    ).apply { allowCoreThreadTimeOut(true) }
    private val cache = Collections.synchronizedMap(object : LinkedHashMap<String, List<CommunitySegment>>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<CommunitySegment>>?): Boolean = size > 24
    })

    fun load(videoId: String, categories: Set<String>, callback: (List<CommunitySegment>) -> Unit) {
        val normalizedId = videoId.trim()
        val normalizedCategories = categories
            .map { it.trim().lowercase() }
            .filter { it in SUPPORTED_CATEGORIES }
            .distinct()
            .sorted()
        if (!VIDEO_ID.matches(normalizedId) || normalizedCategories.isEmpty()) {
            callback(emptyList())
            return
        }

        val cacheKey = normalizedId + "|" + normalizedCategories.joinToString(",")
        cache[cacheKey]?.let {
            callback(it)
            return
        }

        executor.execute {
            val segments = runCatching { fetch(normalizedId, normalizedCategories) }.getOrDefault(emptyList())
            cache[cacheKey] = segments
            callback(segments)
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun fetch(videoId: String, categories: List<String>): List<CommunitySegment> {
        val fullHash = sha256(videoId)
        val prefix = fullHash.take(HASH_PREFIX_LENGTH)
        val categoryJson = JSONArray(categories).toString()
        val encodedCategories = URLEncoder.encode(categoryJson, Charsets.UTF_8.name())
        val encodedActions = URLEncoder.encode("[\"skip\"]", Charsets.UTF_8.name())
        val connection = (URI.create("$BASE_URL/api/skipSegments/$prefix?categories=$encodedCategories&actionTypes=$encodedActions").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "YouTooBee/1.1")
        }

        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return emptyList()
            if (code !in 200..299) return emptyList()
            val payload = BufferedInputStream(connection.inputStream).use { stream ->
                val output = StringBuilder()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_RESPONSE_BYTES) return emptyList()
                    output.append(String(buffer, 0, read, Charsets.UTF_8))
                }
                output.toString()
            }
            return parseCandidates(payload, videoId, fullHash, categories.toSet())
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseCandidates(
        payload: String,
        videoId: String,
        fullHash: String = sha256(videoId),
        requestedCategories: Set<String> = SUPPORTED_CATEGORIES
    ): List<CommunitySegment> {
        val root = runCatching { JSONArray(payload) }.getOrNull() ?: return emptyList()
        val result = ArrayList<CommunitySegment>()
        for (i in 0 until minOf(root.length(), MAX_CANDIDATES)) {
            val candidate = root.optJSONObject(i) ?: continue
            val candidateId = candidate.optString("videoID", "")
            if (candidateId != videoId) continue
            // Current prefix responses may omit hash. Derive it locally from the returned ID;
            // continue rejecting a supplied hash that disagrees with the requested video.
            val candidateHash = candidate.optString("hash", "").ifBlank { sha256(candidateId) }
            if (!candidateHash.equals(fullHash, ignoreCase = true)) continue
            val segments = candidate.optJSONArray("segments") ?: continue
            for (j in 0 until minOf(segments.length(), MAX_SEGMENTS)) {
                val item = segments.optJSONObject(j) ?: continue
                val category = item.optString("category", "").lowercase()
                val actionType = item.optString("actionType", "skip").lowercase()
                if (actionType != "skip") continue
                if (category !in requestedCategories || category !in SUPPORTED_CATEGORIES) continue
                val pair = item.optJSONArray("segment") ?: continue
                if (pair.length() < 2) continue
                val startSeconds = pair.optDouble(0, Double.NaN)
                val endSeconds = pair.optDouble(1, Double.NaN)
                if (!startSeconds.isFinite() || !endSeconds.isFinite()) continue
                if (startSeconds < 0.0 || endSeconds <= startSeconds || endSeconds > MAX_SEGMENT_END_SECONDS) continue
                result += CommunitySegment(
                    startMs = (startSeconds * 1000.0).toLong().coerceAtLeast(0L),
                    endMs = (endSeconds * 1000.0).toLong().coerceAtLeast(1L),
                    category = category
                )
            }
            break
        }
        return result.sortedWith(compareBy<CommunitySegment> { it.startMs }.thenBy { it.endMs })
    }

    companion object {
        const val CATEGORY_SPONSOR = "sponsor"
        const val CATEGORY_SELF_PROMO = "selfpromo"
        const val CATEGORY_INTERACTION = "interaction"
        const val CATEGORY_INTRO = "intro"
        const val CATEGORY_OUTRO = "outro"
        const val CATEGORY_PREVIEW = "preview"
        const val CATEGORY_MUSIC_OFFTOPIC = "music_offtopic"

        val SUPPORTED_CATEGORIES = setOf(
            CATEGORY_SPONSOR,
            CATEGORY_SELF_PROMO,
            CATEGORY_INTERACTION,
            CATEGORY_INTRO,
            CATEGORY_OUTRO,
            CATEGORY_PREVIEW,
            CATEGORY_MUSIC_OFFTOPIC
        )

        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
        private const val BASE_URL = "https://sponsor.ajay.app"
        private const val HASH_PREFIX_LENGTH = 4
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 6_000
        private const val MAX_RESPONSE_BYTES = 512 * 1024
        private const val MAX_CANDIDATES = 256
        private const val MAX_SEGMENTS = 256
        private const val MAX_SEGMENT_END_SECONDS = 24.0 * 60.0 * 60.0

        internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
