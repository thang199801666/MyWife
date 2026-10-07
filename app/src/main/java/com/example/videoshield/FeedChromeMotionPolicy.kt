package com.example.videoshield

/**
 * Allocation-free scroll hysteresis for the browse chrome. The WebView can dispatch many scroll
 * callbacks per second, so this policy keeps only primitive state and emits a transition when the
 * accumulated movement is meaningful. Tiny direction changes are ignored instead of repeatedly
 * animating the bottom navigation.
 */
class FeedChromeMotionPolicy(
    private val thresholdPx: Int,
    private val topRevealPx: Int,
    private val deadbandPx: Int
) {
    enum class Action { NONE, HIDE, SHOW }

    private var accumulatorPx = 0
    private var direction = 0

    fun reset() {
        accumulatorPx = 0
        direction = 0
    }

    fun onScroll(scrollY: Int, oldScrollY: Int, lockedVisible: Boolean): Action {
        if (lockedVisible || scrollY <= topRevealPx) {
            reset()
            return Action.SHOW
        }

        val delta = scrollY - oldScrollY
        if (kotlin.math.abs(delta) < deadbandPx) return Action.NONE

        val nextDirection = if (delta > 0) 1 else -1
        if (nextDirection != direction) {
            direction = nextDirection
            accumulatorPx = 0
        }
        accumulatorPx += delta

        return when {
            accumulatorPx >= thresholdPx -> {
                reset()
                Action.HIDE
            }
            accumulatorPx <= -thresholdPx -> {
                reset()
                Action.SHOW
            }
            else -> Action.NONE
        }
    }
}
