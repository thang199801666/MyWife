package com.example.videoshield

import kotlin.math.abs
import kotlin.math.max

/**
 * Pure state policy for YouTube-style repeated double-tap seeking.
 *
 * After the initial double tap, additional taps on the same side within a short window extend the
 * seek target by another configured step. Keeping the policy Android-free makes the gesture easy
 * to stress-test without synthesizing MotionEvents.
 */
data class DoubleTapSeekBurstState(
    val direction: Int = 0,
    val basePositionMs: Long = 0L,
    val accumulatedMs: Long = 0L,
    val lastAcceptedTapMs: Long = Long.MIN_VALUE
) {
    val active: Boolean get() = direction == -1 || direction == 1
}

object DoubleTapSeekBurstPolicy {
    const val CONTINUE_WINDOW_MS = 520L
    const val MAX_ACCUMULATED_MS = 180_000L

    fun directionForX(x: Float, width: Float): Int = when {
        width <= 0f -> 0
        x < width * 0.35f -> -1
        x > width * 0.65f -> 1
        else -> 0
    }

    fun expired(state: DoubleTapSeekBurstState, eventTimeMs: Long): Boolean {
        if (!state.active) return true
        val elapsed = eventTimeMs - state.lastAcceptedTapMs
        return elapsed < 0L || elapsed > CONTINUE_WINDOW_MS
    }

    fun canContinue(state: DoubleTapSeekBurstState, direction: Int, eventTimeMs: Long): Boolean {
        if (!state.active || direction == 0 || direction != state.direction) return false
        val elapsed = eventTimeMs - state.lastAcceptedTapMs
        return elapsed in 40L..CONTINUE_WINDOW_MS
    }

    fun start(
        basePositionMs: Long,
        direction: Int,
        stepSeconds: Int,
        eventTimeMs: Long
    ): DoubleTapSeekBurstState {
        if (direction != -1 && direction != 1) return DoubleTapSeekBurstState()
        val step = stepMs(stepSeconds)
        return DoubleTapSeekBurstState(
            direction = direction,
            basePositionMs = basePositionMs.coerceAtLeast(0L),
            accumulatedMs = direction * step,
            lastAcceptedTapMs = eventTimeMs
        )
    }

    fun extend(
        state: DoubleTapSeekBurstState,
        stepSeconds: Int,
        eventTimeMs: Long
    ): DoubleTapSeekBurstState {
        if (!state.active) return state
        val step = stepMs(stepSeconds)
        val magnitude = (abs(state.accumulatedMs) + step).coerceAtMost(MAX_ACCUMULATED_MS)
        return state.copy(
            accumulatedMs = state.direction * magnitude,
            lastAcceptedTapMs = eventTimeMs
        )
    }

    fun targetPositionMs(state: DoubleTapSeekBurstState, durationMs: Long): Long {
        if (!state.active) return state.basePositionMs.coerceAtLeast(0L)
        val upper = max(0L, durationMs - 250L)
        return (state.basePositionMs + state.accumulatedMs).coerceIn(0L, upper)
    }

    private fun stepMs(stepSeconds: Int): Long = stepSeconds.coerceIn(5, 30) * 1_000L
}
