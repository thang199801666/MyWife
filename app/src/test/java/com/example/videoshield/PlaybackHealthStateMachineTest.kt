package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHealthStateMachineTest {
    @Test fun bufferingIsStalledWithoutBeingReportedAsPause() {
        val machine = PlaybackHealthStateMachine { }
        machine.heartbeat(playing = true, videoId = "abcdefghijk", buffering = true)
        assertEquals(PlaybackHealthState.STALLED, machine.snapshot.state)
        machine.heartbeat(playing = true, videoId = "abcdefghijk", buffering = false)
        assertEquals(PlaybackHealthState.HEALTHY, machine.snapshot.state)
    }
}
