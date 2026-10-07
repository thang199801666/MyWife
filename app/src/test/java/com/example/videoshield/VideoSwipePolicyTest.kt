package com.example.videoshield

import org.junit.Assert.*
import org.junit.Test

class VideoSwipePolicyTest {
    @Test fun downwardSwipeMustBeLongEnoughAndPredominantlyVertical() {
        assertTrue(VideoSwipePolicy.downward(15f, 120f, 100f))
        assertFalse(VideoSwipePolicy.downward(15f, 99f, 100f))
        assertFalse(VideoSwipePolicy.downward(100f, 120f, 100f))
        assertFalse(VideoSwipePolicy.downward(0f, -120f, 100f))
    }
    @Test fun reversalAndInvalidCoordinatesDoNotMinimize() {
        assertFalse(VideoSwipePolicy.downward(0f, 10f, 100f))
        assertFalse(VideoSwipePolicy.downward(Float.NaN, 120f, 100f))
        assertFalse(VideoSwipePolicy.downward(0f, Float.POSITIVE_INFINITY, 100f))
    }

    @Test fun minimizeReleaseUsesDistanceOrIntentionalDownwardFling() {
        assertTrue(VideoSwipePolicy.shouldCommit(10f, 64f, 0f, 1f))
        assertTrue(VideoSwipePolicy.shouldCommit(8f, 24f, 950f, 1f))
        assertFalse(VideoSwipePolicy.shouldCommit(80f, 70f, 2_000f, 1f))
        assertFalse(VideoSwipePolicy.shouldCommit(0f, 23f, 2_000f, 1f))
        assertFalse(VideoSwipePolicy.shouldCommit(0f, 80f, -1_000f, 1f))
    }

    @Test fun minimizeReleaseScalesThresholdsWithDensity() {
        assertFalse(VideoSwipePolicy.shouldCommit(0f, 127f, 0f, 2f))
        assertTrue(VideoSwipePolicy.shouldCommit(0f, 128f, 0f, 2f))
        assertFalse(VideoSwipePolicy.shouldCommit(0f, 47f, 1_900f, 2f))
        assertTrue(VideoSwipePolicy.shouldCommit(0f, 48f, 1_900f, 2f))
    }

    @Test fun transportStripIsReservedForYoutubeControls() {
        assertTrue(PlayerTouchPolicy.inTransportStrip(950f, 1000f, 80f))
        assertTrue(PlayerTouchPolicy.inTransportStrip(920f, 1000f, 80f))
        assertFalse(PlayerTouchPolicy.inTransportStrip(919f, 1000f, 80f))
        assertFalse(PlayerTouchPolicy.inTransportStrip(Float.NaN, 1000f, 80f))
    }
}
