package com.example.videoshield

import org.junit.Assert.*
import org.junit.Test

class PlaybackReadKeyTest {
    private val key = PlaybackReadKey("testvideo01", 7L)
    private val saved = VideoItem("testvideo01", "Video", "Channel", "https://m.youtube.com/watch?v=testvideo01", 1000L, 120000L, 600000L)

    @Test fun staleReadCannotApplyAfterSeekReloadOrDifferentVideo() {
        assertTrue(key.matches("testvideo01", 7L))
        assertFalse(key.matches("testvideo02", 7L))
        assertFalse(key.matches("testvideo01", 8L))
        assertFalse(key.matches("", 7L))
    }

    @Test fun savedPositionSurvivesAnInitialPlaybackReport() {
        assertEquals(120000L, key.resumeTarget(saved, 1000L, true, 0L))
        assertNull(key.resumeTarget(saved, 6000L, true, 0L))
    }

    @Test fun clearingHistoryOrDisablingResumeInvalidatesTheRead() {
        assertNull(key.resumeTarget(saved, 0L, true, 1001L))
        assertNull(key.resumeTarget(saved, 0L, false, 0L))
        assertNull(key.resumeTarget(saved.copy(videoId = "testvideo02"), 0L, true, 0L))
    }

    @Test fun completedOrVeryShortProgressDoesNotResume() {
        assertNull(key.resumeTarget(saved.copy(positionMs = 580000L), 0L, true, 0L))
        assertNull(key.resumeTarget(saved.copy(positionMs = 5000L), 0L, true, 0L))
        assertNull(key.resumeTarget(null, 0L, true, 0L))
    }
}
