package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCommandDispatcherTest {
    private class FakeSink : PlaybackCommandSink {
        val calls = mutableListOf<String>()
        override fun play() { calls += "play" }
        override fun pause() { calls += "pause" }
        override fun toggle() { calls += "toggle" }
        override fun seekBack() { calls += "back" }
        override fun seekForward() { calls += "forward" }
        override fun seekToMs(positionMs: Long) { calls += "seek:$positionMs" }
        override fun setRepeatEnabled(enabled: Boolean) { calls += "repeat:$enabled" }
        override fun setPlaybackRate(rate: Float) { calls += "rate:$rate" }
    }

    @Test fun serviceCodecUsesCanonicalTypedCommands() {
        assertTrue(PlaybackServiceCommandCodec.decode(PlaybackCommandIds.PLAY) is PlaybackCommand.Play)
        assertTrue(PlaybackServiceCommandCodec.decode(PlaybackCommandIds.PLAY_PAUSE) is PlaybackCommand.Toggle)
        assertEquals(1234L, (PlaybackServiceCommandCodec.decode(PlaybackCommandIds.SEEK_TO, 1234L) as PlaybackCommand.SeekTo).positionMs)
        assertEquals(null, PlaybackServiceCommandCodec.decode(PlaybackCommandIds.SEEK_TO, null))
        assertEquals(null, PlaybackServiceCommandCodec.decode("unknown"))
    }

    @Test fun dispatcherRoutesAndNormalizesCommands() {
        val sink = FakeSink()
        var queueNext = 0
        var stopped: Boolean? = null
        val stateChanges = mutableListOf<PlaybackCommand>()
        val dispatcher = PlaybackCommandDispatcher(
            sink = sink,
            onQueueNext = { queueNext++ },
            onStop = { stopped = it },
            onControlStateChanged = { stateChanges += it }
        )

        dispatcher.dispatch(PlaybackCommand.Play)
        dispatcher.dispatch(PlaybackCommand.SeekTo(-50L))
        dispatcher.dispatch(PlaybackCommand.SetRate(9f))
        dispatcher.dispatch(PlaybackCommand.SetRepeat(true))
        dispatcher.dispatch(PlaybackCommand.QueueNext)
        dispatcher.dispatch(PlaybackCommand.Stop(clearSnapshot = false))

        assertEquals(listOf("play", "seek:0", "rate:4.0", "repeat:true", "pause"), sink.calls)
        assertEquals(1, queueNext)
        assertEquals(false, stopped)
        assertEquals(2, stateChanges.size)
    }
}
