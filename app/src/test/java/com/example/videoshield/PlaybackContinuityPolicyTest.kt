package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackContinuityPolicyTest {
    @Test fun sameVideoReloadKeepsPausedOrPlayingSession() {
        assertEquals(PlaybackNavigationDisposition.HOLD_SESSION, PlaybackContinuityPolicy.onNavigationStarted(true, false, "abc", "abc"))
        assertEquals(PlaybackNavigationDisposition.HOLD_SESSION, PlaybackContinuityPolicy.onNavigationStarted(true, true, "abc", "abc"))
    }

    @Test fun playingSessionBridgesToNextVideoAsBuffering() {
        assertEquals(PlaybackNavigationDisposition.HOLD_SESSION, PlaybackContinuityPolicy.onNavigationStarted(true, true, "abc", "next"))
    }

    @Test fun pausedSessionDoesNotLeakAcrossDifferentVideo() {
        assertEquals(PlaybackNavigationDisposition.CLEAR, PlaybackContinuityPolicy.onNavigationStarted(true, false, "abc", "next"))
    }

    @Test fun nonPlaybackNavigationClearsSession() {
        assertEquals(PlaybackNavigationDisposition.CLEAR, PlaybackContinuityPolicy.onNavigationStarted(true, true, "abc", ""))
        assertEquals(PlaybackNavigationDisposition.CLEAR, PlaybackContinuityPolicy.onNavigationStarted(false, false, "", "abc"))
    }
}
