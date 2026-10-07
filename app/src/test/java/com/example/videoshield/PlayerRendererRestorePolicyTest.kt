package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRendererRestorePolicyTest {
    @Test fun playingIntentStaysBufferingAcrossInitialPausedFrame() {
        assertEquals(
            PlayerRendererBridgeState(playing = true, buffering = true),
            PlayerRendererRestorePolicy.bridgeState(true, true, "abc", "abc", false, false)
        )
    }

    @Test fun pausedIntentDoesNotFlashPlayingWhenRendererAutoplays() {
        assertEquals(
            PlayerRendererBridgeState(playing = false, buffering = false),
            PlayerRendererRestorePolicy.bridgeState(true, false, "abc", "abc", true, false)
        )
    }

    @Test fun unrelatedVideoUsesReportedState() {
        assertEquals(
            PlayerRendererBridgeState(playing = true, buffering = false),
            PlayerRendererRestorePolicy.bridgeState(true, false, "abc", "xyz", true, false)
        )
    }

    @Test fun seekThresholdAvoidsTinyCorrection() {
        assertTrue(PlayerRendererRestorePolicy.shouldSeek(45_000L, 10_000L))
        assertFalse(PlayerRendererRestorePolicy.shouldSeek(45_000L, 43_500L))
        assertFalse(PlayerRendererRestorePolicy.shouldSeek(4_000L, 0L))
    }
}
