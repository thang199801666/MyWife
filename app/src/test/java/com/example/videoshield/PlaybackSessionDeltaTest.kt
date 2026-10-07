package com.example.videoshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionDeltaTest {
    private val base = PlaybackSessionState(
        playing = true,
        buffering = false,
        hasSession = true,
        title = "Video",
        channel = "Channel",
        videoId = "abcdefghijk",
        positionMs = 10_000L,
        durationMs = 100_000L,
        positionReportedAt = 1_000L
    )

    @Test
    fun positionOnlyHeartbeatDoesNotRequestPresentationWork() {
        val delta = PlaybackSessionDelta(
            previous = base,
            current = base.copy(positionMs = 28_000L, positionReportedAt = 19_000L),
            videoChanged = false,
            channelChanged = false
        )

        assertFalse(delta.playingChanged)
        assertFalse(delta.bufferingChanged)
        assertFalse(delta.playbackStateChanged)
        assertFalse(delta.presentationChanged)
    }

    @Test
    fun bufferingTransitionStillRequestsPresentationWork() {
        val delta = PlaybackSessionDelta(
            previous = base,
            current = base.copy(buffering = true),
            videoChanged = false,
            channelChanged = false
        )

        assertTrue(delta.bufferingChanged)
        assertTrue(delta.playbackStateChanged)
        assertTrue(delta.presentationChanged)
    }
}
