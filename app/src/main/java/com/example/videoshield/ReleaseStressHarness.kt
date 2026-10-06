package com.example.videoshield

/** Deterministic, non-destructive stress checks intended for release diagnostics. */
data class ReleaseStressResult(val passed: Boolean, val summary: String)

object ReleaseStressHarness {
    fun run(library: LibraryStore, iterations: Int = 1_000): ReleaseStressResult {
        val checks = linkedMapOf<String, Boolean>()
        var latest = PlaybackHealthSnapshot(PlaybackHealthState.IDLE)
        val seenStates = linkedSetOf(PlaybackHealthState.IDLE)
        val machine = PlaybackHealthStateMachine {
            latest = it
            seenStates += it.state
        }

        val boundedIterations = iterations.coerceIn(100, 5_000)
        repeat(boundedIterations) { index ->
            machine.setOnline(true, index % 2 == 0)
            machine.navigationStarted()
            when {
                index % 13 == 0 -> machine.recoveryExhausted("fixture exhausted")
                index % 11 == 0 -> machine.recoveryStarted("fixture recovery", (index % 2) + 1)
                index % 5 == 0 -> machine.mainFrameError("fixture-$index", recoverable = true)
                else -> machine.heartbeat(index % 3 != 0, "abcdefghijk")
            }
            if (index % 7 == 0) {
                machine.setOnline(false, true)
                machine.setOnline(true, true)
            }
            machine.userRetry()
        }
        checks["state machine terminates deterministically"] = latest.state == PlaybackHealthState.NAVIGATING
        checks["all recovery states exercised"] = setOf(
            PlaybackHealthState.NAVIGATING,
            PlaybackHealthState.HEALTHY,
            PlaybackHealthState.OFFLINE,
            PlaybackHealthState.STALLED,
            PlaybackHealthState.RECOVERING,
            PlaybackHealthState.FAILED
        ).all { it in seenStates }

        val trustedFixtures = listOf(
            "https://www.youtube.com/watch?v=abcdefghijk",
            "https://m.youtube.com/shorts/abcdefghijk",
            "https://youtu.be/abcdefghijk?t=42"
        )
        val hostileFixtures = listOf(
            "https://youtube.com.evil.example/watch?v=abcdefghijk",
            "https://evil.example/?next=https://youtube.com/watch?v=abcdefghijk",
            "javascript:alert(1)"
        )
        checks["trusted fixtures accepted"] = trustedFixtures.all { YouTubeAdapter.isTrustedBridgeUrl(it) }
        checks["incoming normalization survives"] = trustedFixtures.all { YouTubeAdapter.normalizeIncomingUrl(it) != null }
        checks["hostile fixtures rejected"] = hostileFixtures.all { !YouTubeAdapter.isTrustedBridgeUrl(it) }

        val queue = library.queue(1_000)
        checks["queue IDs unique"] = queue.map { it.videoId }.filter { it.isNotBlank() }.distinct().size == queue.size
        checks["queue URLs populated"] = queue.all { it.videoId.isNotBlank() && it.url.isNotBlank() }

        val integrity = library.quickIntegrityCheck()
        checks["database quick_check"] = integrity.healthy

        val regression = RegressionFixtureHarness.run(2_000)
        checks["session/lifecycle regression fixtures"] = regression.passed

        val snapshot = PlaybackSnapshot(
            playing = true,
            title = "stress",
            channel = "stress",
            url = "https://m.youtube.com/watch?v=abcdefghijk",
            videoId = "abcdefghijk",
            positionMs = 59_000L,
            durationMs = 60_000L,
            updatedAt = System.currentTimeMillis() - 30_000L
        )
        checks["snapshot prediction clamps"] = snapshot.predictedPositionMs() == 60_000L

        val failed = checks.filterValues { !it }.keys
        val total = checks.size
        val summary = if (failed.isEmpty()) {
            "$total/$total release stress checks passed • iterations=$boundedIterations • states=${seenStates.size} • ${regression.summary} • ${integrity.summary}"
        } else {
            "${total - failed.size}/$total passed • failed: ${failed.joinToString(", ")} • ${regression.summary} • ${integrity.summary}"
        }
        return ReleaseStressResult(failed.isEmpty(), summary)
    }
}
