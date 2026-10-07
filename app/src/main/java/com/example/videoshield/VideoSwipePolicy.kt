package com.example.videoshield

import kotlin.math.abs

/** Shared touch exclusion for YouTube's native transport/progress controls. */
object PlayerTouchPolicy {
    fun inTransportStrip(y: Float, playerBottom: Float, guardHeight: Float): Boolean {
        if (!y.isFinite() || !playerBottom.isFinite() || !guardHeight.isFinite()) return false
        if (playerBottom <= 0f || guardHeight <= 0f) return false
        val top = (playerBottom - guardHeight).coerceAtLeast(0f)
        return y in top..playerBottom
    }
}

/** Pure gesture policy so swipe-to-mini thresholds stay deterministic and unit-testable. */
object VideoSwipePolicy {
    fun downward(dx: Float, dy: Float, threshold: Float): Boolean =
        dx.isFinite() && dy.isFinite() && dy >= threshold && dy > abs(dx) * 1.20f

    /**
     * A normal drag needs enough travel to be deliberate; a fast downward flick can commit
     * earlier. Horizontal scrubbing and a clear upward reversal never minimize the player.
     */
    fun shouldCommit(dx: Float, dy: Float, yVelocity: Float, density: Float): Boolean {
        if (!dx.isFinite() || !dy.isFinite() || !yVelocity.isFinite() || !density.isFinite() || density <= 0f) return false
        if (dy <= 0f) return false
        if (yVelocity <= -350f * density) return false
        val vertical = dy > abs(dx) * 1.08f
        if (!vertical) return false
        val distanceCommit = dy >= 64f * density
        val flingCommit = dy >= 24f * density && yVelocity >= 900f * density
        return distanceCommit || flingCommit
    }
}
