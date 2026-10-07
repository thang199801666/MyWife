package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMediaRetentionPolicyTest {
    private fun context(
        foreground: Boolean = true,
        pip: Boolean = false,
        visible: Boolean = true,
        minimized: Boolean = false,
        playing: Boolean = false,
        buffering: Boolean = false,
        pressure: MemoryPressureTier = MemoryPressureTier.NORMAL
    ) = PlayerMediaRetentionContext(
        foreground = foreground,
        pictureInPicture = pip,
        surfaceVisible = visible,
        minimized = minimized,
        playing = playing,
        buffering = buffering,
        memoryPressure = pressure
    )

    @Test fun activePlaybackIsNeverTrimmed() {
        val d = PlayerMediaRetentionPolicy.immediate(context(foreground = false, playing = true, pressure = MemoryPressureTier.CRITICAL))
        assertEquals(PlayerMediaRetentionMode.ACTIVE, d.mode)
        assertFalse(d.pauseRenderer)
        assertFalse(d.clearMemoryCache)
        assertTrue(d.compactSession)
    }

    @Test fun backgroundPausedSessionGetsGraceThenColdTrim() {
        val c = context(foreground = false)
        val first = PlayerMediaRetentionPolicy.immediate(c)
        assertEquals(PlayerMediaRetentionMode.WARM_PAUSED, first.mode)
        assertEquals(45_000L, first.graceDelayMs)
        val settled = PlayerMediaRetentionPolicy.afterGrace(c)
        assertEquals(PlayerMediaRetentionMode.COLD_PAUSED, settled.mode)
        assertTrue(settled.pauseRenderer)
        assertTrue(settled.clearMemoryCache)
    }

    @Test fun minimizedPausedSessionKeepsRendererButDropsFuturePreloadAfterGrace() {
        val c = context(minimized = true)
        assertEquals(30_000L, PlayerMediaRetentionPolicy.immediate(c).graceDelayMs)
        val settled = PlayerMediaRetentionPolicy.afterGrace(c)
        assertEquals(PlayerMediaRetentionMode.LEAN_PAUSED, settled.mode)
        assertFalse(settled.pauseRenderer)
    }

    @Test fun lowMemoryCanParkPausedMiniRendererImmediately() {
        val d = PlayerMediaRetentionPolicy.immediate(context(minimized = true, pressure = MemoryPressureTier.LOW))
        assertEquals(PlayerMediaRetentionMode.COLD_PAUSED, d.mode)
        assertTrue(d.pauseRenderer)
        assertTrue(d.compactSession)
    }

    @Test fun pipAlwaysStaysActiveEvenWhenPausedAndCritical() {
        val d = PlayerMediaRetentionPolicy.immediate(context(pip = true, pressure = MemoryPressureTier.CRITICAL))
        assertEquals(PlayerMediaRetentionMode.ACTIVE, d.mode)
        assertFalse(d.pauseRenderer)
    }

    @Test fun transportAndControlCommandsWakeRenderer() {
        assertTrue(PlayerMediaRetentionPolicy.shouldWakeRenderer(PlaybackCommand.Play))
        assertTrue(PlayerMediaRetentionPolicy.shouldWakeRenderer(PlaybackCommand.SeekForward))
        assertTrue(PlayerMediaRetentionPolicy.shouldWakeRenderer(PlaybackCommand.SetRate(1.5f)))
        assertFalse(PlayerMediaRetentionPolicy.shouldWakeRenderer(PlaybackCommand.Pause))
        assertFalse(PlayerMediaRetentionPolicy.shouldWakeRenderer(PlaybackCommand.Stop()))
    }
}
