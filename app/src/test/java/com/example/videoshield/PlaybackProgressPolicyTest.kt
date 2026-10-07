package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressPolicyTest {
    @Test fun playingPositionExtrapolatesForSparseBridgeReports() {
        assertEquals(
            112_000L,
            PlaybackProgressPolicy.predict(
                positionMs = 100_000L,
                durationMs = 300_000L,
                reportedAtMs = 1_000L,
                nowMs = 9_000L,
                playing = true,
                playbackRate = 1.5f
            )
        )
    }

    @Test fun extrapolationIsBoundedAndPausedStateDoesNotAdvance() {
        assertEquals(
            115_000L,
            PlaybackProgressPolicy.predict(
                positionMs = 100_000L,
                durationMs = 300_000L,
                reportedAtMs = 1_000L,
                nowMs = 120_000L,
                playing = true,
                playbackRate = 1f
            )
        )
        assertEquals(
            100_000L,
            PlaybackProgressPolicy.predict(
                positionMs = 100_000L,
                durationMs = 300_000L,
                reportedAtMs = 1_000L,
                nowMs = 9_000L,
                playing = false,
                playbackRate = 2f
            )
        )
    }
}
