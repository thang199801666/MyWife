package com.example.videoshield

import java.text.Normalizer
import java.util.Locale

/** Presentation rules shared by the native library and local recommendation controls. */
object LibraryPolicy {
    fun collectionQueue(items: List<VideoItem>, existing: List<VideoItem>): List<VideoItem> {
        val selected = items.distinctBy { it.videoId }
        val ids = selected.map { it.videoId }.toSet()
        return selected.drop(1) + existing.filterNot { it.videoId in ids }.distinctBy { it.videoId }
    }
    fun searchable(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('đ', 'd')

    fun matches(query: String, title: String, channel: String): Boolean {
        val words = searchable(query).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val haystack = searchable("$title $channel")
        return words.all { it in haystack }
    }

    fun canResume(video: VideoItem): Boolean = video.durationMs > 0 && video.positionMs >= 30_000 &&
        video.positionMs < video.durationMs && video.durationMs - video.positionMs > 30_000 &&
        video.positionMs.toDouble() / video.durationMs < 0.95

    fun channelKey(channel: String): String = channel.trim().lowercase(Locale.ROOT).take(180)

    fun placeholderTitle(title: String): Boolean = title.trim().lowercase(Locale.ROOT) in
        setOf("", "youtube", "youtube video", "youtube…", "youtube...")

    fun preserveMetadata(incoming: VideoItem, previous: VideoItem?): VideoItem {
        if (previous == null || previous.videoId != incoming.videoId) return incoming
        return incoming.copy(
            title = if (placeholderTitle(incoming.title) && !placeholderTitle(previous.title)) previous.title else incoming.title,
            channel = incoming.channel.ifBlank { previous.channel }
        )
    }
}
