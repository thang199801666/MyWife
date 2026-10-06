package com.example.videoshield

import org.junit.Assert.*
import org.junit.Test

class PersonalizationTest {
    private fun video(id: String, title: String, channel: String = "Music") = VideoItem(id, title, channel, "https://m.youtube.com/watch?v=$id", 100000L)

    @Test fun watchingCountsElapsedTimeButNotSeekingPausesOrLongGaps() {
        val tracker = ViewingHabitTracker()
        assertEquals(0L, tracker.update("a", true, 0, 1000))
        assertEquals(10000L, tracker.update("a", true, 10000, 11000))
        assertEquals(0L, tracker.update("a", true, 90000, 12000))
        assertEquals(1000L, tracker.update("a", false, 91000, 13000))
        assertEquals(0L, tracker.update("a", true, 91000, 14000))
        assertEquals(0L, tracker.update("a", true, 120000, 50000))
        assertEquals(0L, tracker.update("b", true, 30000, 51000))
    }
    @Test fun speedAndBufferingDoNotInflateViewingTime() {
        val tracker = ViewingHabitTracker()
        tracker.update("a", true, 0, 1000, 2.0)
        assertEquals(10000L, tracker.update("a", true, 20000, 11000, 2.0))
        assertEquals(1000L, tracker.update("a", true, 22000, 21000, 2.0))
    }
    @Test fun favoritesAndWatchedTopicsRankUnseenVideosAndRespectDismissal() {
        val candidates = listOf(video("new", "Guitar acoustic lesson"), video("other", "Car engine", "Cars"), video("hidden", "Guitar chords"), video("seen", "Guitar song"))
        val ranked = RecommendationEngine.rank(candidates, listOf(InterestEvidence(video("old", "Guitar acoustic concert"), 120000, true)), emptySet(), setOf("hidden"), setOf("seen"), 100000)
        assertEquals(listOf("new", "other"), ranked.map { it.video.videoId })
        assertTrue(ranked.first().reason.startsWith("Because"))
    }
    @Test fun followSignalsAndChannelDiversityWorkWithoutHistory() {
        val candidates = (1..8).map { video("music$it", "Song") } + video("cars", "Car", "Cars")
        val ranked = RecommendationEngine.rank(candidates, emptyList(), setOf("Music"), emptySet(), emptySet(), 100000)
        assertEquals(4, ranked.count { it.video.channel == "Music" })
        assertTrue(ranked.first().reason.contains("follow"))
        assertTrue(ranked.any { it.video.channel == "Cars" })
    }
    @Test fun candidatePayloadCannotSupplyExternalNavigationOrInvalidIds() {
        val rows = DiscoveryBridge.parse("""[{"id":"dQw4w9WgXcQ","title":"Example","url":"https://evil.example"},{"id":"javascript:1","title":"Bad"}]""", 100000)
        assertEquals(1, rows.size)
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ", rows.first().url)
    }
    @Test fun networkRulesMatchEndpointsNotSearchValuesAndHandleQueryOrder() {
        assertFalse(NetworkRuleMatcher.matches("ad_break", "/results", "search_query=ad_break"))
        assertFalse(NetworkRuleMatcher.matches("/pagead/", "/thumbnail", "url=/pagead/"))
        assertTrue(NetworkRuleMatcher.matches("/pagead/", "/pagead/id", null))
        assertTrue(NetworkRuleMatcher.matches("/api/stats/qoe?adformat=", "/api/stats/qoe", "v=abc&adformat=preroll"))
        assertFalse(NetworkRuleMatcher.matches("/api/stats/qoe?adformat=", "/api/stats/qoe", "q=adformat=preroll"))
        assertTrue(NetworkRuleMatcher.isMediaHost("r1.googlevideo.com"))
        assertFalse(NetworkRuleMatcher.isMediaHost("evilgooglevideo.com"))
    }
}
