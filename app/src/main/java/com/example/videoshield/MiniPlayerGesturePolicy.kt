package com.example.videoshield

import kotlin.math.abs

enum class MiniPlayerReleaseAction { SNAP, EXPAND, DISMISS }

data class MiniPlayerSnapTarget(val x: Float, val y: Float)

/** Pure geometry/velocity policy so floating-player gestures stay deterministic and testable. */
object MiniPlayerGesturePolicy {
    fun releaseAction(vx: Float, vy: Float, minFlingVelocity: Float): MiniPlayerReleaseAction {
        val verticalFling = abs(vy) >= minFlingVelocity && abs(vy) > abs(vx) * 1.12f
        if (!verticalFling) return MiniPlayerReleaseAction.SNAP
        return if (vy < 0f) MiniPlayerReleaseAction.EXPAND else MiniPlayerReleaseAction.DISMISS
    }

    fun nearestCorner(x: Float, y: Float, bounds: FloatArray): MiniPlayerSnapTarget {
        require(bounds.size >= 4)
        val centerX = (bounds[0] + bounds[1]) * 0.5f
        val centerY = (bounds[2] + bounds[3]) * 0.5f
        return MiniPlayerSnapTarget(
            x = if (x <= centerX) bounds[0] else bounds[1],
            y = if (y <= centerY) bounds[2] else bounds[3]
        )
    }
}
