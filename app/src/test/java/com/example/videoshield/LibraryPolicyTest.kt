package com.example.videoshield

import org.junit.Assert.*
import org.junit.Test

class LibraryPolicyTest {
    private fun video(position: Long, duration: Long) = VideoItem("dQw4w9WgXcQ", "Music", "Channel", "https://m.youtube.com/watch?v=dQw4w9WgXcQ", 1, position, duration)

    @Test fun transientPageMetadataKeepsKnownTitleWithoutLosingNewProgressOrCrossingVideos() {
        val saved = video(60_000, 300_000)
        val incoming = saved.copy(title = "YouTube", channel = "", positionMs = 65_000)
        val merged = LibraryPolicy.preserveMetadata(incoming, saved)
        assertEquals("Music", merged.title)
        assertEquals("Channel", merged.channel)
        assertEquals(65_000L, merged.positionMs)
        assertEquals("New title", LibraryPolicy.preserveMetadata(incoming.copy(title = "New title"), saved).title)
        assertEquals("YouTube", LibraryPolicy.preserveMetadata(incoming.copy(videoId = "other"), saved).title)
        assertTrue(LibraryPolicy.placeholderTitle(" YouTube… "))
    }

    @Test fun resumeExcludesUnstartedFinishedLiveAndNearlyCompletedVideos() {
        assertTrue(LibraryPolicy.canResume(video(60_000, 300_000)))
        assertFalse(LibraryPolicy.canResume(video(29_999, 300_000)))
        assertFalse(LibraryPolicy.canResume(video(280_000, 300_000)))
        assertFalse(LibraryPolicy.canResume(video(960_000, 1_000_000)))
        assertFalse(LibraryPolicy.canResume(video(300_000, 300_000)))
        assertFalse(LibraryPolicy.canResume(video(60_000, 0)))
        assertFalse(LibraryPolicy.canResume(video(Long.MAX_VALUE, Long.MAX_VALUE)))
        assertTrue(LibraryPolicy.canResume(video(60_000, Long.MAX_VALUE)))
    }

    @Test fun searchMatchesVietnameseAccentsAndAllTermsAcrossTitleAndChannel() {
        assertTrue(LibraryPolicy.matches("duong nhac", "Đường về nhà", "Âm nhạc Việt"))
        assertTrue(LibraryPolicy.matches("  GUITAR   live ", "Guitar lesson", "Live Music"))
        assertFalse(LibraryPolicy.matches("guitar cars", "Guitar lesson", "Live Music"))
        assertTrue(LibraryPolicy.matches("", "Anything", ""))
    }

    @Test fun hiddenChannelOverridesFollowAndViewingSignalsButNotUnrelatedChannels() {
        val hidden = video(0, 0).copy(channel = "  MUSIC ")
        val other = hidden.copy(videoId = "other", channel = "Music Live")
        val ranked = RecommendationEngine.rank(listOf(hidden, other), listOf(InterestEvidence(hidden, 120_000, true)),
            setOf("music"), emptySet(), emptySet(), 1, blockedChannels = setOf("Music"))
        assertEquals(listOf("other"), ranked.map { it.video.videoId })
        assertEquals(2, RecommendationEngine.rank(listOf(hidden, other), emptyList(), emptySet(), emptySet(), emptySet(), 1).size)
    }
}
