package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RebindablePlaybackBackendTest {
    @Test fun commandsDuringRecoveryAreCoalescedAndUserIntentWins() {
        val dead = RecordingBackend()
        val backend = RebindablePlaybackBackend(dead)
        backend.beginRecovery(PlaybackRecoveryHandoff(true, 42_000L, 1.0f, false))

        backend.pause()
        backend.seekForward()
        backend.seekForward()
        backend.setPlaybackRate(1.5f)
        backend.setRepeatEnabled(true)

        val replacement = RecordingBackend()
        backend.rebind(replacement, ready = false)
        assertTrue(replacement.events.isEmpty())
        assertEquals(false, backend.recoveryDesiredPlaying)

        val activation = backend.activateRecovery(3_000L)!!
        assertFalse(activation.desiredPlaying)
        assertEquals(62_000L, activation.targetPositionMs)
        assertTrue(activation.seekApplied)
        assertEquals(
            listOf("rate:1.5", "repeat:true", "seek:62000", "pause"),
            replacement.events
        )
    }

    @Test fun tinySnapshotCorrectionDoesNotSeekAgain() {
        val backend = RebindablePlaybackBackend(RecordingBackend())
        backend.beginRecovery(PlaybackRecoveryHandoff(true, 45_000L, 1.0f, false))
        val replacement = RecordingBackend()
        backend.rebind(replacement, ready = false)

        val activation = backend.activateRecovery(43_500L)!!
        assertFalse(activation.seekApplied)
        assertEquals(listOf("rate:1.0", "repeat:false", "play"), replacement.events)
    }

    @Test fun selectingNewRecoveryTargetCanReplaceOldSeed() {
        val backend = RebindablePlaybackBackend(RecordingBackend())
        backend.beginRecovery(PlaybackRecoveryHandoff(true, 80_000L, 1.0f, false))
        backend.pause()
        backend.beginRecovery(PlaybackRecoveryHandoff(true, 0L, 1.25f, true))
        val replacement = RecordingBackend()
        backend.rebind(replacement, ready = false)

        val activation = backend.activateRecovery(0L)!!
        assertTrue(activation.desiredPlaying)
        assertEquals(0L, activation.targetPositionMs)
        assertFalse(activation.seekApplied)
        assertEquals(listOf("rate:1.25", "repeat:true", "play"), replacement.events)
    }

    private class RecordingBackend : PlaybackBackend {
        val events = mutableListOf<String>()
        override fun play() { events += "play" }
        override fun pause() { events += "pause" }
        override fun toggle() { events += "toggle" }
        override fun seekBack() { events += "back" }
        override fun seekForward() { events += "forward" }
        override fun seekToMs(positionMs: Long) { events += "seek:$positionMs" }
        override fun setRepeatEnabled(enabled: Boolean) { events += "repeat:$enabled" }
        override fun setPlaybackRate(rate: Float) { events += "rate:$rate" }
        override fun setCommunitySegments(videoId: String, segments: List<CommunitySegment>) = Unit
        override fun clearCommunitySegments() = Unit
    }
}
