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

        val bufferingHealth = PlaybackHealthStateMachine { }
        bufferingHealth.heartbeat(true, "abcdefghijk", buffering = true)
        val bufferingMarkedStalled = bufferingHealth.snapshot.state == PlaybackHealthState.STALLED
        bufferingHealth.heartbeat(true, "abcdefghijk", buffering = false)
        checks["buffering is distinct from pause and recovers cleanly"] =
            bufferingMarkedStalled && bufferingHealth.snapshot.state == PlaybackHealthState.HEALTHY
        checks["buffering freezes extrapolated mini progress"] =
            PlaybackProgressPolicy.predict(100_000L, 300_000L, 1_000L, 9_000L, false, 1.5f) == 100_000L &&
                PlaybackProgressPolicy.predict(100_000L, 300_000L, 1_000L, 9_000L, true, 1.5f) == 112_000L

        val spoofed = listOf(
            "https://youtube.com.evil.example/watch?v=abcdefghijk",
            "https://youtube.com@evil.example/watch?v=abcdefghijk",
            "https://m.youtube.com.evil.tld/watch?v=abcdefghijk"
        )
        checks["host spoof fixtures rejected"] = spoofed.none { YouTubeAdapter.isTrustedBridgeUrl(it) }
        checks["valid watch accepted"] = YouTubeAdapter.isTrustedBridgeUrl("https://m.youtube.com/watch?v=abcdefghijk")
        var promotedRoutes = 0
        var shortsChromeCallbacks = 0
        BrowseNavigationBridge(
            navigate = { promotedRoutes++ },
            shortsChromeChanged = { shortsChromeCallbacks++ }
        ).also { bridge ->
            bridge.openVideo("https://m.youtube.com/watch?v=abcdefghijk")
            bridge.shortsChromeHidden(true)
            bridge.close()
            bridge.openVideo("https://m.youtube.com/watch?v=lmnopqrstuv")
            bridge.shortsChromeHidden(false)
        }
        checks["closed browse bridge stops callback delivery"] = promotedRoutes == 1 && shortsChromeCallbacks == 1
        checks["memory pressure tiers are monotonic"] =
            MemoryPressurePolicy.fromTrimLevel(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) == MemoryPressureTier.MODERATE &&
                MemoryPressurePolicy.fromTrimLevel(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) == MemoryPressureTier.LOW &&
                MemoryPressurePolicy.fromTrimLevel(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) == MemoryPressureTier.CRITICAL &&
                MemoryPressureTier.CRITICAL.atLeast(MemoryPressureTier.LOW)
        checks["WebView history budget tightens under memory pressure"] =
            !WebViewSessionCompactionPolicy.shouldCompactBrowse(16, false, true, false, MemoryPressureTier.MODERATE) &&
                WebViewSessionCompactionPolicy.shouldCompactBrowse(17, false, true, false, MemoryPressureTier.MODERATE) &&
                WebViewSessionCompactionPolicy.shouldCompactBrowse(5, false, true, false, MemoryPressureTier.CRITICAL) &&
                WebViewSessionCompactionPolicy.scrollSnapshotLimit(MemoryPressureTier.CRITICAL) == 2
        checks["critical pressure skips rich WebView serialization"] =
            !WebViewSessionCompactionPolicy.shouldSerializeBrowseState(1, false, false, false, MemoryPressureTier.CRITICAL) &&
                !WebViewSessionCompactionPolicy.shouldSerializePlayerState(1, false, MemoryPressureTier.CRITICAL)
        checks["sleep timer wakes only at visible minute boundary"] =
            WakeSchedulingPolicy.sleepTimerDelayMs(3_570_000L) == 30_000L &&
                WakeSchedulingPolicy.sleepTimerDelayMs(59_000L) == 59_000L
        checks["service stale watchdog uses one exact deadline"] =
            WakeSchedulingPolicy.staleDeadlineDelayMs(1_000L, 31_000L, 75_000L) == 45_000L &&
                WakeSchedulingPolicy.staleDeadlineDelayMs(1_000L, 80_000L, 75_000L) == 0L

        val recreateGate = ActivityRecreationGate()
        checks["renderer recreation requests coalesce"] =
            recreateGate.request() && !recreateGate.request() && recreateGate.isPending
        recreateGate.cancel()
        checks["renderer recreation gate resets on teardown"] = recreateGate.request()

        val endGuard = PlaybackEndGuard()
        checks["stale playback ended callback cannot advance queue"] =
            !endGuard.shouldAutoAdvance(
                reportedVideoId = "oldoldold01",
                currentVideoId = "abcdefghijk",
                routeVideoId = "abcdefghijk",
                navigationGeneration = 10L,
                repeatEnabled = false,
                autoAdvanceEnabled = true
            )
        checks["current playback ended callback advances once"] =
            endGuard.shouldAutoAdvance(
                reportedVideoId = "abcdefghijk",
                currentVideoId = "abcdefghijk",
                routeVideoId = "abcdefghijk",
                navigationGeneration = 10L,
                repeatEnabled = false,
                autoAdvanceEnabled = true
            ) && !endGuard.shouldAutoAdvance(
                reportedVideoId = "abcdefghijk",
                currentVideoId = "abcdefghijk",
                routeVideoId = "abcdefghijk",
                navigationGeneration = 10L,
                repeatEnabled = false,
                autoAdvanceEnabled = true
            )
        checks["repeat mode suppresses native auto advance"] =
            !PlaybackEndGuard().shouldAutoAdvance(
                reportedVideoId = "abcdefghijk",
                currentVideoId = "abcdefghijk",
                routeVideoId = "abcdefghijk",
                navigationGeneration = 11L,
                repeatEnabled = true,
                autoAdvanceEnabled = true
            )
        var endedChurnHealthy = true
        val churnGuard = PlaybackEndGuard()
        repeat(5_000) { index ->
            val generation = index.toLong()
            val current = if (index % 2 == 0) "abcdefghijk" else "lmnopqrstuv"
            val stale = if (current == "abcdefghijk") "lmnopqrstuv" else "abcdefghijk"
            endedChurnHealthy = endedChurnHealthy &&
                !churnGuard.shouldAutoAdvance(stale, current, current, generation, false, true) &&
                churnGuard.shouldAutoAdvance(current, current, current, generation, false, true) &&
                !churnGuard.shouldAutoAdvance(current, current, current, generation, false, true)
        }
        checks["queue end guard survives long callback churn"] = endedChurnHealthy
        checks["renderer crash guard tolerates wall-clock rollback"] =
            RendererCrashLoopPolicy.windowExpired(20_000L, 10_000L, 600_000L) &&
                RendererCrashLoopPolicy.stableWindowReached(20_000L, 10_000L, 900_000L)

        val networkCheckpoint = NetworkRecoveryCheckpoint(
            videoId = "abcdefghijk",
            url = "https://m.youtube.com/watch?v=abcdefghijk",
            positionMs = 90_000L,
            durationMs = 300_000L,
            wasPlaying = true,
            playbackRate = 1.5f,
            repeatEnabled = true,
            capturedAtElapsedMs = 10_000L
        )
        checks["network handoff settle is bounded and metered-aware"] =
            NetworkRecoveryPolicy.reconnectSettleDelayMs(true, false, false) == 900L &&
                NetworkRecoveryPolicy.reconnectSettleDelayMs(true, true, true) == 1_800L &&
                NetworkRecoveryPolicy.reconnectSettleDelayMs(false, true, true) == Long.MAX_VALUE
        checks["network recovery restores only meaningful position regression"] =
            NetworkRecoveryPolicy.restorePositionMs(networkCheckpoint, 20_000L) == 90_000L &&
                NetworkRecoveryPolicy.restorePositionMs(networkCheckpoint, 88_500L) == null
        checks["network recovery heartbeat requires progress and playing intent"] =
            NetworkRecoveryPolicy.healthy(networkCheckpoint, "abcdefghijk", true, 89_000L, 20_000L) &&
                !NetworkRecoveryPolicy.healthy(networkCheckpoint, "abcdefghijk", false, 90_000L, 20_000L) &&
                !NetworkRecoveryPolicy.healthy(networkCheckpoint, "lmnopqrstuv", true, 90_000L, 20_000L)
        checks["network recovery escalation is bounded"] =
            NetworkRecoveryPolicy.MAX_ESCALATIONS == 2 &&
                NetworkRecoveryPolicy.verificationDelayMs(0) < NetworkRecoveryPolicy.verificationDelayMs(1)
        var networkPolicyStressHealthy = true
        repeat(5_000) { index ->
            val metered = index % 2 == 0
            val switched = index % 3 == 0
            val delay = NetworkRecoveryPolicy.reconnectSettleDelayMs(true, metered, switched)
            val instruction = NetworkRecoveryPolicy.instruction(
                networkCheckpoint.copy(positionMs = 30_000L + index),
                currentPositionMs = if (index % 5 == 0) 5_000L else 30_000L + index,
                playing = index % 4 != 0
            )
            networkPolicyStressHealthy = networkPolicyStressHealthy &&
                delay in 900L..1_800L && instruction.playbackRate in 0.25f..4f
        }
        checks["network recovery policy survives long handoff churn"] = networkPolicyStressHealthy

        val feedChrome = FeedChromeMotionPolicy(thresholdPx = 28, topRevealPx = 8, deadbandPx = 2)
        var feedChromeStressHealthy = true
        repeat(5_000) { index ->
            val base = 100 + (index % 40)
            val down = feedChrome.onScroll(base + 18, base, lockedVisible = false)
            val up = feedChrome.onScroll(base - 18, base + 18, lockedVisible = false)
            feedChromeStressHealthy = feedChromeStressHealthy &&
                down in FeedChromeMotionPolicy.Action.values() &&
                up in FeedChromeMotionPolicy.Action.values()
        }
        checks["feed chrome hysteresis survives long scroll churn"] = feedChromeStressHealthy
        checks["feed chrome is always visible at top"] =
            feedChrome.onScroll(scrollY = 4, oldScrollY = 90, lockedVisible = false) ==
                FeedChromeMotionPolicy.Action.SHOW

        checks["short link normalized"] = YouTubeAdapter.normalizeIncomingUrl("https://youtu.be/abcdefghijk?t=90")
            ?.contains("watch?v=abcdefghijk") == true

        checks["address host promoted to HTTPS"] =
            NavigationTargetResolver.resolveAddressInput("youtube.com/watch?v=abcdefghijk") ==
                "https://youtube.com/watch?v=abcdefghijk"
        checks["address text becomes YouTube search"] =
            NavigationTargetResolver.resolveAddressInput("finite element tutorial")
                ?.startsWith("https://m.youtube.com/results?search_query=") == true
        checks["default search stays unfiltered like YouTube"] =
            NavigationTargetResolver.resolveAddressInput("finite element tutorial")
                ?.contains("&sp=") == false
        checks["search type filters keep known YouTube tokens"] =
            SearchFilterPolicy.encode(SearchFilterState(type = SearchResultType.VIDEOS)) == "EgIQAQ==" &&
                SearchFilterPolicy.encode(SearchFilterState(type = SearchResultType.SHORTS)) == "EgIQCQ==" &&
                SearchFilterPolicy.encode(SearchFilterState(type = SearchResultType.CHANNELS)) == "EgIQAg==" &&
                SearchFilterPolicy.encode(SearchFilterState(type = SearchResultType.PLAYLISTS)) == "EgIQAw=="
        val combinedSearchFilter = SearchFilterState(
            type = SearchResultType.VIDEOS,
            prioritize = SearchPrioritize.POPULARITY,
            uploadDate = SearchUploadDate.THIS_WEEK,
            duration = SearchDuration.THREE_TO_20_MIN
        )
        checks["combined search filter round-trips"] =
            SearchFilterPolicy.decode(SearchFilterPolicy.encode(combinedSearchFilter)) == combinedSearchFilter
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
        checks["client chrome makes Shorts top immersive"] =
            !ClientChromePolicy.forRoute(YouTubeRoute.parse("https://m.youtube.com/shorts/abcdefghijk"), false, false).showAppBar
        checks["client chrome hides in PiP"] =
            !ClientChromePolicy.forRoute(YouTubeRoute.parse("https://m.youtube.com/watch?v=abcdefghijk"), false, true).showAppBar

        val watchSurfaceScript = ClientSurfaceScript.player("Download", "More", lightTheme = false)
        checks["watch surface owns app action supplement"] =
            watchSurfaceScript.contains("__voTuibeInstallWatchActions") &&
                watchSurfaceScript.contains("requestPlayerOptions") &&
                watchSurfaceScript.contains("requestDownload")
        checks["watch recommendations can skip offscreen paint"] =
            watchSurfaceScript.contains("content-visibility:auto") &&
                watchSurfaceScript.contains("contain-intrinsic-size")

        val homeSurfaceScript = HomeRecommendationsScript.build(
            enabled = true,
            rows = listOf(SuggestedVideo(saved.copy(videoId = "lmnopqrstuv"), "fixture reason", 1.0)),
            continueRows = listOf(saved),
            continueHeading = "Continue watching",
            heading = "",
            hint = "",
            lightTheme = false
        )
        checks["home resume shelf exposes watched progress"] =
            homeSurfaceScript.contains("votuibe-home-resume-shelf") &&
                homeSurfaceScript.contains("background:#ff0033") &&
                homeSurfaceScript.contains("formatDuration")
        checks["home thumbnails use browser lazy loading"] =
            homeSurfaceScript.contains("image.loading=index===0?'eager':'lazy'") &&
                homeSurfaceScript.contains("fetchPriority")
        checks["home shelf uses snap without polling"] =
            homeSurfaceScript.contains("scroll-snap-type:x proximity") &&
                !homeSurfaceScript.contains("setInterval(")
        val homeLoadingScript = HomeRecommendationsScript.loading(enabled = true, lightTheme = false)
        checks["home skeleton is static"] =
            homeLoadingScript.contains("dataset.loading='1'") &&
                !homeLoadingScript.contains("animation:") &&
                !homeLoadingScript.contains("setInterval(")
        val searchPreviewScript = SearchPreviewScript.build()
        checks["search results can skip offscreen paint"] =
            searchPreviewScript.contains("content-visibility:auto") &&
                searchPreviewScript.contains("contain-intrinsic-size")
        checks["search preview avoids repeating poll timers"] = !searchPreviewScript.contains("setInterval(")
        checks["search preview avoids document-wide mutation observation"] =
            searchPreviewScript.contains("IntersectionObserver") &&
                searchPreviewScript.contains("requestIdleCallback") &&
                !searchPreviewScript.contains("MutationObserver")
        checks["search preview ages its deep-feed observation window"] =
            searchPreviewScript.contains("windowStart") &&
                searchPreviewScript.contains("ageObserver") &&
                searchPreviewScript.contains("observer.unobserve(link)") &&
                searchPreviewScript.contains("observerAgingBudget") &&
                searchPreviewScript.contains("currentRouteKey") &&
                searchPreviewScript.contains("requestBudget") &&
                searchPreviewScript.contains("deferredRepair")

        val browseGuardScript = BrowseResourceGuardScript.install()
        checks["browse feed virtualizes offscreen cards without polling"] =
            browseGuardScript.contains("contentVisibility='auto'") &&
                browseGuardScript.contains("IntersectionObserver") &&
                browseGuardScript.contains("requestIdleCallback") &&
                browseGuardScript.contains("velocityBudget") &&
                browseGuardScript.contains("decodeDeferrals") &&
                !browseGuardScript.contains("setInterval(")
        checks["browse feed bounds detached observer retention"] =
            browseGuardScript.contains("compactFeedObserver") &&
                browseGuardScript.contains("ageFeedObserver") &&
                browseGuardScript.contains("observer.unobserve(card)") &&
                browseGuardScript.contains("observerAgingBudget") &&
                browseGuardScript.contains("card.isConnected")
        checks["browse feed scroll restore is route keyed and bounded"] =
            browseGuardScript.contains("sessionStorage") &&
                browseGuardScript.contains("slice(0,8)") &&
                browseGuardScript.contains("const delays=[0,90,220,460,760]") &&
                browseGuardScript.contains("__votuibeRememberFeedScroll")

        val discoveryScript = DiscoveryScript.build(enabled = true)
        checks["home discovery backs off while feed is stable"] =
            discoveryScript.contains("Math.min(180000") && discoveryScript.contains("fallbackDelay*1.6") &&
                !discoveryScript.contains("setInterval(")
        checks["shield exposes low-power runtime switch"] =
            AdBlockScript.setPowerConstrained(true).contains("__videoShieldSetPowerConstrained(true)")

        val browseSurfaceScript = ClientSurfaceScript.browse(lightTheme = false)
        checks["home feed can skip offscreen card paint"] =
            browseSurfaceScript.contains("content-visibility: auto") &&
                browseSurfaceScript.contains("contain-intrinsic-size: 330px")
        checks["home shorts shelf can skip offscreen lockups"] =
            browseSurfaceScript.contains("ytm-shorts-lockup-view-model") &&
                browseSurfaceScript.contains("contain-intrinsic-size: 220px 390px")

        val shortsNormal = ShortsRuntimePolicyResolver.resolve(
            online = true, metered = false, powerConstrained = false, lowRamDevice = false,
            memoryPressure = ShortsMemoryPressure.NORMAL, memoryHardening = true
        )
        val shortsMetered = ShortsRuntimePolicyResolver.resolve(
            online = true, metered = true, powerConstrained = false, lowRamDevice = false,
            memoryPressure = ShortsMemoryPressure.NORMAL, memoryHardening = true
        )
        val shortsLowMemory = ShortsRuntimePolicyResolver.resolve(
            online = true, metered = false, powerConstrained = false, lowRamDevice = false,
            memoryPressure = ShortsMemoryPressure.LOW, memoryHardening = true
        )
        val shortsOffline = ShortsRuntimePolicyResolver.resolve(
            online = false, metered = false, powerConstrained = false, lowRamDevice = false,
            memoryPressure = ShortsMemoryPressure.NORMAL, memoryHardening = true
        )
        checks["shorts healthy network preloads only next aggressively"] =
            shortsNormal.previousPreload == ShortsPreloadMode.METADATA &&
                shortsNormal.nextPreload == ShortsPreloadMode.AUTO && !shortsNormal.hardReleaseDistant
        checks["shorts metered network reduces preload"] =
            shortsMetered.nextPreload == ShortsPreloadMode.METADATA
        checks["shorts low memory enables hard release"] =
            shortsLowMemory.hardReleaseDistant && shortsLowMemory.trimImages &&
                shortsLowMemory.retainedVideoPressure <= 6
        checks["shorts offline disables next preload"] = shortsOffline.nextPreload == ShortsPreloadMode.NONE

        val seekBurstStart = DoubleTapSeekBurstPolicy.start(
            basePositionMs = 90_000L,
            direction = 1,
            stepSeconds = 10,
            eventTimeMs = 1_000L
        )
        val seekBurstSecond = DoubleTapSeekBurstPolicy.extend(seekBurstStart, 10, 1_220L)
        val seekBurstThird = DoubleTapSeekBurstPolicy.extend(seekBurstSecond, 10, 1_430L)
        checks["double tap seek burst accumulates like YouTube"] =
            DoubleTapSeekBurstPolicy.canContinue(seekBurstStart, 1, 1_220L) &&
                seekBurstSecond.accumulatedMs == 20_000L && seekBurstThird.accumulatedMs == 30_000L &&
                DoubleTapSeekBurstPolicy.targetPositionMs(seekBurstThird, 300_000L) == 120_000L
        checks["double tap seek burst rejects side switch and stale taps"] =
            !DoubleTapSeekBurstPolicy.canContinue(seekBurstThird, -1, 1_600L) &&
                !DoubleTapSeekBurstPolicy.canContinue(seekBurstThird, 1, 2_200L)
        checks["double tap seek clamps at media boundaries"] =
            DoubleTapSeekBurstPolicy.targetPositionMs(
                DoubleTapSeekBurstPolicy.start(4_000L, -1, 10, 1_000L),
                300_000L
            ) == 0L &&
                DoubleTapSeekBurstPolicy.targetPositionMs(
                    DoubleTapSeekBurstPolicy.start(298_000L, 1, 10, 1_000L),
                    300_000L
                ) == 299_750L
        var burstStress = DoubleTapSeekBurstPolicy.start(90_000L, 1, 30, 1_000L)
        repeat(5_000) { index ->
            burstStress = DoubleTapSeekBurstPolicy.extend(burstStress, 30, 1_100L + index)
        }
        checks["double tap seek burst remains bounded under long tap churn"] =
            burstStress.accumulatedMs == DoubleTapSeekBurstPolicy.MAX_ACCUMULATED_MS &&
                DoubleTapSeekBurstPolicy.targetPositionMs(burstStress, 600_000L) <= 599_750L

        checks["watch swipe release commits by distance or downward fling"] =
            VideoSwipePolicy.shouldCommit(12f, 64f, 0f, 1f) &&
                VideoSwipePolicy.shouldCommit(8f, 24f, 950f, 1f) &&
                !VideoSwipePolicy.shouldCommit(90f, 64f, 2_000f, 1f)
        val miniPreview = PlayerSurfaceTransitionPolicy.preview(
            distancePx = 200f, travelPx = 200f, sourceWidth = 1000f, sourceHeight = 1600f,
            targetWidth = 250f, targetHeight = 140f, targetDeltaX = 700f, targetDeltaY = 1200f
        )
        checks["watch minimize preview stays uniform and bounded"] =
            miniPreview.progress == 1f && miniPreview.scaleX == 0.82f &&
                miniPreview.scaleY == miniPreview.scaleX &&
                miniPreview.translationX == 168f && miniPreview.translationY == 288f

        checks["mini player upward flick expands"] =
            MiniPlayerGesturePolicy.releaseAction(120f, -2_400f, 1_000f) == MiniPlayerReleaseAction.EXPAND
        checks["mini player downward flick dismisses"] =
            MiniPlayerGesturePolicy.releaseAction(100f, 2_200f, 1_000f) == MiniPlayerReleaseAction.DISMISS
        checks["mini player horizontal fling still snaps"] =
            MiniPlayerGesturePolicy.releaseAction(2_500f, 900f, 1_000f) == MiniPlayerReleaseAction.SNAP
        checks["mini player snaps to all four corners"] = listOf(
            MiniPlayerGesturePolicy.nearestCorner(-80f, -120f, floatArrayOf(-100f, 100f, -150f, 150f)),
            MiniPlayerGesturePolicy.nearestCorner(80f, -120f, floatArrayOf(-100f, 100f, -150f, 150f)),
            MiniPlayerGesturePolicy.nearestCorner(-80f, 120f, floatArrayOf(-100f, 100f, -150f, 150f)),
            MiniPlayerGesturePolicy.nearestCorner(80f, 120f, floatArrayOf(-100f, 100f, -150f, 150f))
        ).toSet().size == 4

        checks["mini surface restore survives activity recreation"] =
            PlayerSurfaceStatePolicy.restore("MINI") == PlayerSurfaceState.MINI &&
                PlayerSurfaceStatePolicy.restore("EXPANDED") == PlayerSurfaceState.EXPANDED &&
                PlayerSurfaceStatePolicy.restore("invalid") == PlayerSurfaceState.HIDDEN
        var rapidSurfaceState = PlayerSurfaceState.HIDDEN
        repeat(5_000) { index ->
            val action = when (index % 5) {
                0 -> PlayerSurfaceAction.EXPAND
                1 -> PlayerSurfaceAction.MINIMIZE
                2 -> PlayerSurfaceAction.EXPAND
                3 -> PlayerSurfaceAction.HIDE
                else -> PlayerSurfaceAction.MINIMIZE
            }
            rapidSurfaceState = PlayerSurfaceStatePolicy.next(rapidSurfaceState, action)
        }
        checks["rapid player surface transitions settle deterministically"] =
            rapidSurfaceState == PlayerSurfaceState.HIDDEN

        var routeChurnHealthy = true
        repeat(5_000) { index ->
            val browse = YouTubeRoute.parse(if (index % 2 == 0) ShieldPreferences.HOME_URL else YouTubeRoute.SHORTS_URL)
            routeChurnHealthy = routeChurnHealthy && if (index % 2 == 0) {
                browse.destination == YouTubeDestination.HOME
            } else {
                browse.destination == YouTubeDestination.SHORTS
            }
            val watch = YouTubeRoute.parse("https://m.youtube.com/watch?v=abcdefghijk")
            routeChurnHealthy = routeChurnHealthy && watch.isNativePlayback && watch.videoId == "abcdefghijk"
        }
        checks["home shorts watch route churn remains stable"] = routeChurnHealthy

        val shortsGuardScript = ShortsResourceGuardScript.install()
        checks["shorts guard keeps exact neighbour window"] =
            shortsGuardScript.contains("Math.abs(index-activeIndex)===1") &&
                !shortsGuardScript.contains("keepRadius") && !shortsGuardScript.contains("trimRadius")
        checks["shorts guard avoids background polling"] =
            !shortsGuardScript.contains("setInterval(") && !shortsGuardScript.contains("new MutationObserver")
        checks["shorts guard can release distant decoder buffers"] =
            shortsGuardScript.contains("releaseDistant") && shortsGuardScript.contains("video.load()")
        checks["shorts transition trim is frame-coalesced"] =
            shortsGuardScript.contains("requestAnimationFrame") && shortsGuardScript.contains("scheduleTransitionTrim")
        checks["shorts periodic hard trim is idle-deferred"] =
            shortsGuardScript.contains("requestIdleCallback") && shortsGuardScript.contains("__votuibeHardTrimShorts")
        checks["shorts overlay polish preserves native feed structure"] =
            shortsGuardScript.contains("votuibe-shorts-polish-style") &&
                shortsGuardScript.contains("ytm-reel-player-overlay-renderer")
        checks["shorts hold 2x fallback preserves normal swipe ownership"] =
            shortsGuardScript.contains("playbackRate=2") &&
                shortsGuardScript.contains("passive:true") &&
                shortsGuardScript.contains("attachLockMove") &&
                shortsGuardScript.contains("passive:false")
        checks["shorts hold 2x can lock with downward swipe"] =
            shortsGuardScript.contains("2\\u00d7 locked") &&
                shortsGuardScript.contains("dy>52") &&
                shortsGuardScript.contains("releaseLockedSpeed")
        checks["shorts auto hide is opt in and one shot"] =
            shortsGuardScript.contains("autoHideChrome=false") &&
                shortsGuardScript.contains("chromeHideTimer=setTimeout") &&
                shortsGuardScript.contains("data-votuibe-shorts-chrome-hidden") &&
                !shortsGuardScript.contains("setInterval(")
        checks["shorts auto hide re-arms after swipe transition"] =
            shortsGuardScript.contains("trim(false,false);") &&
                shortsGuardScript.contains("transition() deliberately reveals") &&
                shortsGuardScript.contains("scheduleChromeHide();")
        checks["shorts auto hide uses cached active video fast path"] =
            shortsGuardScript.contains("const currentActiveVideo=()") &&
                shortsGuardScript.contains("activeVideo && activeVideo.isConnected!==false")
        checks["shorts clear screen compatibility does not replace YouTube menu"] =
            !shortsGuardScript.contains("votuibe-shorts-clear-screen-menu-item") &&
                shortsGuardScript.contains("shortsChromeHidden")

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
