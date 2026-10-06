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
}
