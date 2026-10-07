package com.example.videoshield

import org.junit.Assert.*
import org.junit.Test

class PlayerSurfaceTransitionPolicyTest {
    @Test fun previewStartsExpandedAndUsesSafeUniformScale() {
        val start = PlayerSurfaceTransitionPolicy.preview(
            distancePx = 0f, travelPx = 200f, sourceWidth = 1080f, sourceHeight = 1920f,
            targetWidth = 228f, targetHeight = 128.25f, targetDeltaX = 840f, targetDeltaY = 1500f
        )
        assertEquals(0f, start.progress, 0.0001f)
        assertEquals(1f, start.scaleX, 0.0001f)
        assertEquals(1f, start.scaleY, 0.0001f)
        assertEquals(0f, start.translationX, 0.0001f)
        assertEquals(0f, start.translationY, 0.0001f)

        val end = PlayerSurfaceTransitionPolicy.preview(
            distancePx = 250f, travelPx = 200f, sourceWidth = 1080f, sourceHeight = 1920f,
            targetWidth = 228f, targetHeight = 128.25f, targetDeltaX = 840f, targetDeltaY = 1500f
        )
        assertEquals(1f, end.progress, 0.0001f)
        assertEquals(0.82f, end.scaleX, 0.0001f)
        assertEquals(end.scaleX, end.scaleY, 0.0001f)
        assertEquals(201.6f, end.translationX, 0.0001f)
        assertEquals(360f, end.translationY, 0.0001f)
    }

    @Test fun previewIsMonotonicAndClamped() {
        val half = PlayerSurfaceTransitionPolicy.preview(
            distancePx = 100f, travelPx = 200f, sourceWidth = 1000f, sourceHeight = 1000f,
            targetWidth = 250f, targetHeight = 250f, targetDeltaX = 500f, targetDeltaY = 500f
        )
        assertEquals(0.5f, half.progress, 0.0001f)
        assertEquals(0.91f, half.scaleX, 0.0001f)
        assertEquals(60f, half.translationX, 0.0001f)
        assertEquals(0f, PlayerSurfaceTransitionPolicy.dragProgress(Float.NaN, 200f), 0.0001f)
        assertEquals(0f, PlayerSurfaceTransitionPolicy.dragProgress(100f, 0f), 0.0001f)
    }
}
