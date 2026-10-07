package com.example.videoshield

import kotlin.math.max

/**
 * Pure geometry for the expanded -> mini-player drag preview.
 *
 * MOVE events only transform the existing renderer. The preview intentionally keeps a uniform,
 * bounded scale because the expanded WebView contains the entire watch page, not only the 16:9
 * video. On release the mini CSS/layout is installed once and FLIP completes the remaining travel.
 */
data class PlayerSurfacePreviewTransform(
    val progress: Float,
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float
)

object PlayerSurfaceTransitionPolicy {
    private const val MIN_PREVIEW_SCALE = 0.82f
    private const val DESTINATION_TRAVEL_FRACTION = 0.24f

    fun dragProgress(distancePx: Float, travelPx: Float): Float {
        if (!distancePx.isFinite() || !travelPx.isFinite() || distancePx <= 0f || travelPx <= 0f) return 0f
        return (distancePx / travelPx).coerceIn(0f, 1f)
    }

    fun preview(
        distancePx: Float,
        travelPx: Float,
        sourceWidth: Float,
        sourceHeight: Float,
        targetWidth: Float,
        targetHeight: Float,
        targetDeltaX: Float,
        targetDeltaY: Float
    ): PlayerSurfacePreviewTransform {
        val safeSourceWidth = max(1f, sourceWidth)
        val safeSourceHeight = max(1f, sourceHeight)
        val p = dragProgress(distancePx, travelPx)
        // Preserve page aspect during the drag. The actual mini surface becomes 16:9 only after
        // ClientSurfaceScript.mini() is installed at commit time.
        val targetUniformScale = max(targetWidth / safeSourceWidth, targetHeight / safeSourceHeight)
            .coerceIn(MIN_PREVIEW_SCALE, 1f)
        val scale = 1f + (targetUniformScale - 1f) * p
        val travelFraction = DESTINATION_TRAVEL_FRACTION * p
        return PlayerSurfacePreviewTransform(
            progress = p,
            scaleX = scale,
            scaleY = scale,
            translationX = targetDeltaX * travelFraction,
            translationY = targetDeltaY * travelFraction
        )
    }
}
