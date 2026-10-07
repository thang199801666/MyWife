package com.example.videoshield

import kotlin.math.ceil

/** Pure scheduling policy for user-visible deadlines and watchdogs.
 *
 * These helpers intentionally return the next meaningful deadline rather than a polling cadence,
 * so callers can keep the main looper asleep during steady-state playback.
 */
object WakeSchedulingPolicy {
    fun sleepTimerDelayMs(remainingMs: Long, minimumUiWakeMs: Long = 250L): Long {
        if (remainingMs <= 0L) return 0L
        val visibleMinutes = ceil(remainingMs / 60_000.0).toLong().coerceAtLeast(1L)
        val nextBoundaryRemaining = (visibleMinutes - 1L) * 60_000L
        return (remainingMs - nextBoundaryRemaining)
            .coerceAtLeast(minimumUiWakeMs.coerceAtLeast(1L))
            .coerceAtMost(remainingMs)
    }

    fun staleDeadlineDelayMs(
        lastUpdateElapsedMs: Long,
        nowElapsedMs: Long,
        timeoutMs: Long
    ): Long? {
        if (lastUpdateElapsedMs <= 0L || timeoutMs <= 0L) return null
        val elapsed = (nowElapsedMs - lastUpdateElapsedMs).coerceAtLeast(0L)
        return (timeoutMs - elapsed).coerceAtLeast(0L)
    }
}
