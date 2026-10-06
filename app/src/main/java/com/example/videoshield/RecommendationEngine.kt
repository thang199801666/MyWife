package com.example.videoshield

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln

data class InterestEvidence(val video: VideoItem, val watchedMs: Long, val favorite: Boolean)
data class SuggestedVideo(val video: VideoItem, val reason: String, val score: Double)

/** Local ranking only: no account, network request or media extraction. */
object RecommendationEngine {
    private val stopWords = setOf("the", "and", "for", "with", "you", "youtube", "video", "official", "watch", "full", "nhung", "cua", "voi", "cho", "mot", "cac", "trong", "nhat")
    private val tokenRegex = Regex("[\\p{L}\\p{N}]{3,24}")
    private val combiningMarks = Regex("\\p{M}+")
    fun tokens(text: String): Set<String> = tokenRegex.findAll(
        combiningMarks.replace(Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
    ).map { it.value }.filter { it !in stopWords }.take(24).toSet()

    fun rank(candidates: List<VideoItem>, evidence: List<InterestEvidence>, subscriptions: Set<String>,
             dismissed: Set<String>, seen: Set<String>, now: Long, limit: Int = 50,
             blockedChannels: Set<String> = emptySet(), focus: VideoItem? = null,
             searchQueries: List<String> = emptyList(), maxPerChannel: Int = 4): List<SuggestedVideo> {
        val channels = mutableMapOf<String, Double>()
        val topics = mutableMapOf<String, Double>()
        evidence.forEach { e ->
            val days = ((now - e.video.lastPlayedAt).coerceAtLeast(0) / 86_400_000.0)
            // Accidental opens should not teach an interest. Short videos watched
            // substantially still count; actual viewing time comes from the tracker.
            val meaningful = e.watchedMs >= 30_000L ||
                (e.video.durationMs > 0 && e.watchedMs >= 5_000L && e.watchedMs >= e.video.durationMs * 0.5)
            val completion = if (e.video.durationMs > 0) (e.watchedMs.toDouble() / e.video.durationMs).coerceIn(0.0, 1.0) else 0.0
            val weight = ((if (meaningful) ln(1.0 + e.watchedMs.coerceIn(0, 7_200_000) / 30_000.0) + completion * 2.0 else 0.0) +
                if (e.favorite) 2.0 else 0.0) / (1.0 + days / 14.0)
            if (weight > 0) {
                val channel = e.video.channel.trim().lowercase(Locale.ROOT)
                if (channel.isNotBlank()) channels[channel] = (channels[channel] ?: 0.0) + weight
                tokens(e.video.title).forEach { topics[it] = (topics[it] ?: 0.0) + weight }
            }
        }
        val subscribed = subscriptions.map { it.trim().lowercase(Locale.ROOT) }.toSet()
        // Recent searches are weak, local-only interest evidence. They should influence discovery
        // without overpowering actual watch time, favorites, or subscriptions.
        val searchTopics = mutableMapOf<String, Double>()
        searchQueries.take(24).forEachIndexed { index, query ->
            val weight = 2.4 / (1.0 + index / 4.0)
            tokens(query).forEach { token -> searchTopics[token] = (searchTopics[token] ?: 0.0) + weight }
        }
        val blocked = blockedChannels.map { LibraryPolicy.channelKey(it) }.filter { it.isNotBlank() }.toSet()
        val focusTopics = focus?.let { tokens(it.title) }.orEmpty()
        val focusChannel = focus?.let { LibraryPolicy.channelKey(it.channel) }.orEmpty()
        val ranked = candidates.distinctBy { it.videoId }.filter {
            it.videoId !in dismissed && it.videoId !in seen && it.videoId != focus?.videoId && LibraryPolicy.channelKey(it.channel) !in blocked
        }.map { video ->
            // Tokenizing a title performs Unicode normalization/regex work. Reuse one token
            // set for topic, search and related scoring instead of normalizing every candidate
            // three times on each Home recommendation refresh.
            val videoTokens = tokens(video.title)
            val channel = video.channel.trim().lowercase(Locale.ROOT)
            val channelKey = LibraryPolicy.channelKey(video.channel)
            val channelScore = (channels[channel] ?: 0.0).coerceAtMost(12.0)
            val matches = videoTokens.filter { (topics[it] ?: 0.0) > 0 }.sortedByDescending { topics[it] }
            val topicScore = matches.take(4).sumOf { (topics[it] ?: 0.0).coerceAtMost(3.0) }
            val searchMatches = videoTokens.filter { (searchTopics[it] ?: 0.0) > 0 }.sortedByDescending { searchTopics[it] }
            val searchScore = searchMatches.take(4).sumOf { (searchTopics[it] ?: 0.0).coerceAtMost(2.4) }
            val subscription = channel.isNotBlank() && channel in subscribed
            val relatedTopics = if (focusTopics.isEmpty()) emptySet() else videoTokens.intersect(focusTopics)
            val sameChannel = focusChannel.isNotBlank() && channelKey == focusChannel
            // A single shared name fragment (e.g. Billie Jean / Billie Eilish)
            // is too weak when the seed title contains several meaningful terms.
            val enoughTopics = relatedTopics.size >= minOf(2, focusTopics.size).coerceAtLeast(1)
            val relatedScore = (if (enoughTopics) relatedTopics.size * 6.0 else 0.0) + if (sameChannel) 12.0 else 0.0
            val reason = when {
                focus != null && relatedScore > 0 -> "Similar to: ${focus.title.take(80)}"
                subscription -> "From a channel you follow"
                channelScore > 0 -> "Because you watch ${video.channel}"
                matches.isNotEmpty() -> "Matches your interest: ${matches.take(2).joinToString(", ")}"
                searchMatches.isNotEmpty() -> "Matches a recent search: ${searchMatches.take(2).joinToString(", ")}"
                else -> "New discovery from pages you browsed"
            }
            SuggestedVideo(video, reason, if (focus != null) relatedScore else channelScore * 2 + topicScore + searchScore * 1.35 + if (subscription) 10.0 else 0.0)
        }.filter { focus == null || it.score > 0 }
            .sortedWith(compareByDescending<SuggestedVideo> { it.score }.thenByDescending { it.video.lastPlayedAt }.thenBy { it.video.videoId })
        // Avoid a single channel occupying the whole list. No automatic queue changes.
        val counts = mutableMapOf<String, Int>()
        val diverse = ranked.filter {
            val key = it.video.channel.trim().lowercase(Locale.ROOT)
            if (key.isBlank()) true else { val count = counts[key] ?: 0; counts[key] = count + 1; count < maxPerChannel.coerceIn(1, 20) }
        }.toMutableList()
        val result = mutableListOf<SuggestedVideo>()
        while (diverse.isNotEmpty() && result.size < limit.coerceIn(1, 100)) {
            val last = result.lastOrNull()?.video?.channel?.let(LibraryPolicy::channelKey)
            val index = if (last.isNullOrBlank()) 0 else diverse.indexOfFirst { LibraryPolicy.channelKey(it.video.channel) != last }.coerceAtLeast(0)
            result += diverse.removeAt(index)
        }
        return result
    }
}

/** Count elapsed viewing time, not seek position; discard gaps and seek-like jumps. */
class ViewingHabitTracker {
    private var id = ""
    private var position = 0L
    private var time = 0L
    private var wasPlaying = false
    fun update(videoId: String, playing: Boolean, positionMs: Long, elapsedMs: Long, rate: Double = 1.0): Long {
        val wall = elapsedMs - time
        val movement = positionMs - position
        val safeRate = rate.takeIf { it.isFinite() && it in 0.25..4.0 } ?: 1.0
        val watched = if (videoId == id && wasPlaying && wall in 1..15_000 && movement > 0 && movement <= wall * safeRate + 2_000) minOf(wall, (movement / safeRate).toLong()) else 0L
        id = videoId; position = positionMs; time = elapsedMs; wasPlaying = playing
        return watched
    }
    fun reset() { id = ""; time = 0L; wasPlaying = false }
}
