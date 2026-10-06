package com.example.videoshield

/** Release-safe, side-effect-free regression fixtures for session restoration and state transitions. */
data class RegressionFixtureResult(val passed: Boolean, val summary: String)

object RegressionFixtureHarness {
    fun run(iterations: Int = 2_000): RegressionFixtureResult {
        val checks = linkedMapOf<String, Boolean>()

        val saved = VideoItem(
            videoId = "abcdefghijk",
            title = "fixture",
            channel = "fixture channel",
            url = "https://m.youtube.com/watch?v=abcdefghijk",
            lastPlayedAt = 1L,
            positionMs = 75_000L,
            durationMs = 300_000L
        )
        checks["resume normal progress"] = PlaybackSessionCoordinator.shouldResume(saved, 0L)
        checks["resume rejects nonzero current"] = !PlaybackSessionCoordinator.shouldResume(saved, 7_000L)
        checks["resume rejects near start"] = !PlaybackSessionCoordinator.shouldResume(saved.copy(positionMs = 8_000L), 0L)
        checks["resume rejects nearly complete"] = !PlaybackSessionCoordinator.shouldResume(saved.copy(positionMs = 290_000L), 0L)

        val machine = PlaybackHealthStateMachine { }
        val bounded = iterations.coerceIn(250, 20_000)
        repeat(bounded) { index ->
            when (index % 8) {
                0 -> machine.navigationStarted()
                1 -> machine.pageReady(true)
                2 -> machine.heartbeat(true, "abcdefghijk")
                3 -> machine.setOnline(false, true)
                4 -> machine.setOnline(true, true)
                5 -> machine.recoveryStarted("fixture", 1)
                6 -> machine.heartbeat(false, "abcdefghijk")
                else -> machine.reset()
            }
        }
        checks["state-machine bounded"] = machine.snapshot.state in PlaybackHealthState.values().toSet()

        val spoofed = listOf(
            "https://youtube.com.evil.example/watch?v=abcdefghijk",
            "https://youtube.com@evil.example/watch?v=abcdefghijk",
            "https://m.youtube.com.evil.tld/watch?v=abcdefghijk"
        )
        checks["host spoof fixtures rejected"] = spoofed.none { YouTubeAdapter.isTrustedBridgeUrl(it) }
        checks["valid watch accepted"] = YouTubeAdapter.isTrustedBridgeUrl("https://m.youtube.com/watch?v=abcdefghijk")
        checks["short link normalized"] = YouTubeAdapter.normalizeIncomingUrl("https://youtu.be/abcdefghijk?t=90")
            ?.contains("watch?v=abcdefghijk") == true

        checks["address host promoted to HTTPS"] =
            NavigationTargetResolver.resolveAddressInput("youtube.com/watch?v=abcdefghijk") ==
                "https://youtube.com/watch?v=abcdefghijk"
        checks["address text becomes YouTube search"] =
            NavigationTargetResolver.resolveAddressInput("finite element tutorial")
                ?.startsWith("https://m.youtube.com/results?search_query=") == true
        checks["shared URL trims prose punctuation"] =
            NavigationTargetResolver.extractFirstHttpUrl("Watch https://youtu.be/abcdefghijk).") ==
                "https://youtu.be/abcdefghijk"

        checks["client route detects watch"] =
            YouTubeRoute.parse("https://m.youtube.com/watch?v=abcdefghijk").let {
                it.destination == YouTubeDestination.WATCH && it.videoId == "abcdefghijk" &&
                    it.isPlayback && it.isNativePlayback
            }
        checks["client route detects shorts"] =
            YouTubeRoute.parse("https://m.youtube.com/shorts/abcdefghijk").let {
                it.destination == YouTubeDestination.SHORTS && it.videoId == "abcdefghijk" &&
                    it.isPlayback && !it.isNativePlayback
            }
        checks["shorts feed is browse route"] =
            YouTubeRoute.parse(YouTubeRoute.SHORTS_URL).let {
                it.destination == YouTubeDestination.SHORTS && it.videoId.isBlank() && !it.isPlayback
            }
        checks["client route detects subscriptions"] =
            YouTubeRoute.parse(YouTubeRoute.SUBSCRIPTIONS_URL).destination == YouTubeDestination.SUBSCRIPTIONS
        checks["client chrome hides actions off playback"] =
            !ClientChromePolicy.forRoute(YouTubeRoute.parse(YouTubeRoute.HOME_URL), false, false).showPlaybackActions
        checks["client chrome exposes actions on watch"] =
            ClientChromePolicy.forRoute(YouTubeRoute.parse("https://m.youtube.com/watch?v=abcdefghijk"), false, false).showPlaybackActions
        checks["client chrome leaves Shorts controls to feed"] =
            !ClientChromePolicy.forRoute(YouTubeRoute.parse("https://m.youtube.com/shorts/abcdefghijk"), false, false).showPlaybackActions
        checks["client chrome hides in PiP"] =
            !ClientChromePolicy.forRoute(YouTubeRoute.parse("https://m.youtube.com/watch?v=abcdefghijk"), false, true).showAppBar

        checks["javascript navigation blocked"] =
            NavigationSecurityPolicy.decide("javascript:alert(1)", sourceTrusted = true).action == NavigationAction.BLOCK
        checks["untrusted intent navigation blocked"] =
            NavigationSecurityPolicy.decide("intent://watch/#Intent;scheme=vnd.youtube;end", sourceTrusted = false).action == NavigationAction.BLOCK
        checks["trusted intent navigation delegated"] =
            NavigationSecurityPolicy.decide("intent://watch/#Intent;scheme=vnd.youtube;end", sourceTrusted = true).action == NavigationAction.OPEN_EXTERNAL
        checks["trusted YouTube app scheme delegated"] =
            NavigationSecurityPolicy.decide("vnd.youtube:abcdefghijk", sourceTrusted = true).action == NavigationAction.OPEN_EXTERNAL
        checks["HTTPS navigation remains internal"] =
            NavigationSecurityPolicy.decide("https://m.youtube.com/watch?v=abcdefghijk", sourceTrusted = false).action == NavigationAction.ALLOW_IN_WEBVIEW

        checks["progress clamps to duration"] = PlaybackProgressPolicy.normalize(90_000L, 60_000L) == 60_000L
        checks["progress honors playback speed"] = PlaybackProgressPolicy.predict(
            positionMs = 10_000L,
            durationMs = 60_000L,
            reportedAtMs = 1_000L,
            nowMs = 3_000L,
            playing = true,
            playbackRate = 2f
        ) == 14_000L

        val segmentFixtureId = "abcdefghijk"
        val segmentHash = CommunitySegmentClient.sha256(segmentFixtureId)
        val otherSegmentHash = CommunitySegmentClient.sha256("zzzzzzzzzzz")
        val segmentPayload = """
            [
              {
                "videoID":"$segmentFixtureId",
                "hash":"$segmentHash",
                "segments":[
                  {"category":"sponsor","actionType":"skip","segment":[10.5,20.25]},
                  {"category":"intro","actionType":"skip","segment":[0.0,4.0]},
                  {"category":"sponsor","actionType":"mute","segment":[5.0,6.0]},
                  {"category":"unknown","actionType":"skip","segment":[21.0,24.0]}
                ]
              },
              {
                "videoID":"zzzzzzzzzzz",
                "hash":"$otherSegmentHash",
                "segments":[{"category":"sponsor","segment":[1.0,2.0]}]
              }
            ]
        """.trimIndent()
        val segmentClient = CommunitySegmentClient()
        val parsedSegments = segmentClient.parseCandidates(
            payload = segmentPayload,
            videoId = segmentFixtureId,
            fullHash = segmentHash,
            requestedCategories = setOf(CommunitySegmentClient.CATEGORY_SPONSOR, CommunitySegmentClient.CATEGORY_INTRO)
        )
        segmentClient.close()
        checks["community segments filter candidate hash"] = parsedSegments.size == 2
        checks["community segments convert seconds to ms"] = parsedSegments.any {
            it.category == CommunitySegmentClient.CATEGORY_SPONSOR && it.startMs == 10_500L && it.endMs == 20_250L
        }

        val failed = checks.filterValues { !it }.keys
        return RegressionFixtureResult(
            passed = failed.isEmpty(),
            summary = if (failed.isEmpty()) {
                "${checks.size}/${checks.size} regression fixtures passed • iterations=$bounded"
            } else {
                "${checks.size - failed.size}/${checks.size} passed • failed: ${failed.joinToString(", ")}"
            }
        )
    }
}
