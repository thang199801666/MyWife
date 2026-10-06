package com.example.videoshield

import org.junit.Assert.*
import org.junit.Test

class RecommendationRankingTest {
    private val now = 2_000_000_000L
    private fun video(id: String, title: String, channel: String, duration: Long = 300_000L, at: Long = now) =
        VideoItem(id, title, channel, "https://m.youtube.com/watch?v=$id", at, durationMs = duration)

    @Test fun accidentalOpensDoNotTeachInterestsButShortVideosCan() {
        val seed = video("seed", "Guitar lesson", "Music", 20_000)
        val candidate = video("new", "Guitar chords", "Music")
        val noise = RecommendationEngine.rank(listOf(candidate), listOf(InterestEvidence(seed, 2_000, false)), emptySet(), emptySet(), emptySet(), now)
        assertEquals(0.0, noise.single().score, 0.0)
        val meaningful = RecommendationEngine.rank(listOf(candidate), listOf(InterestEvidence(seed, 15_000, false)), emptySet(), emptySet(), emptySet(), now)
        assertTrue(meaningful.single().score > 0)
    }

    @Test fun recentlyWatchedInterestsOutrankOlderEqualEvidence() {
        val current = video("current", "Guitar", "Music")
        val old = video("old", "Cooking", "Food", at = now - 60L * 86_400_000)
        val ranked = RecommendationEngine.rank(listOf(video("food", "Cooking recipes", "Food"), video("music", "Guitar chords", "Music")),
            listOf(InterestEvidence(old, 120_000, false), InterestEvidence(current, 120_000, false)), emptySet(), emptySet(), emptySet(), now)
        assertEquals("music", ranked.first().video.videoId)
    }

    @Test fun similarVideosRequireRelationAndRespectHiddenSeenAndBlockedItems() {
        val seed = video("seed", "Acoustic guitar lesson", "Music")
        val candidates = listOf(seed, video("related", "Acoustic guitar chords", "Teacher"),
            video("hidden", "Guitar", "Music"), video("seen", "Guitar", "Music"),
            video("blocked", "Guitar", "Blocked"), video("unrelated", "Car repair", "Cars"))
        val ranked = RecommendationEngine.rank(candidates, emptyList(), emptySet(), setOf("hidden"), setOf("seen"), now,
            blockedChannels = setOf("Blocked"), focus = seed)
        assertEquals(listOf("related"), ranked.map { it.video.videoId })
        assertTrue(ranked.single().reason.contains(seed.title))
    }

    @Test fun channelsAreInterleavedWhenAlternativesExist() {
        val candidates = (1..5).map { video("music$it", "Guitar", "Music") } + (1..3).map { video("food$it", "Cooking", "Food") }
        val ranked = RecommendationEngine.rank(candidates, emptyList(), setOf("Music"), emptySet(), emptySet(), now)
        assertEquals("Music", ranked[0].video.channel)
        assertEquals("Food", ranked[1].video.channel)
        assertEquals("Music", ranked[2].video.channel)
        assertEquals(4, ranked.count { it.video.channel == "Music" })
    }

    @Test fun oneSharedNameFragmentDoesNotMakeUnrelatedArtistsSimilar() {
        val seed = video("seed", "Michael Jackson Billie Jean", "")
        val candidates = listOf(video("related", "Michael Jackson Thriller", ""), video("other", "Billie Eilish pop hits", ""))
        val ranked = RecommendationEngine.rank(candidates, emptyList(), emptySet(), emptySet(), emptySet(), now, focus = seed)
        assertEquals(listOf("related"), ranked.map { it.video.videoId })
    }
    @Test fun recentSearchesCanGuideDiscoveryWithoutWatchHistory() {
        val candidates = listOf(
            video("coffee", "Vietnamese coffee brewing guide", "Coffee Lab"),
            video("cars", "Car suspension repair", "Garage")
        )
        val ranked = RecommendationEngine.rank(
            candidates, emptyList(), emptySet(), emptySet(), emptySet(), now,
            searchQueries = listOf("cà phê brewing")
        )
        assertEquals("coffee", ranked.first().video.videoId)
        assertTrue(ranked.first().score > ranked.last().score)
        assertTrue(ranked.first().reason.startsWith("Matches a recent search:"))
    }

    @Test fun homeCanUseADeeperPerChannelPoolWithoutChangingTheDefaultCap() {
        val candidates = (1..6).map { video("music$it", "Guitar lesson $it", "Music") }
        val defaults = RecommendationEngine.rank(candidates, emptyList(), setOf("Music"), emptySet(), emptySet(), now)
        val home = RecommendationEngine.rank(candidates, emptyList(), setOf("Music"), emptySet(), emptySet(), now, maxPerChannel = 6)
        assertEquals(4, defaults.size)
        assertEquals(6, home.size)
    }

}
