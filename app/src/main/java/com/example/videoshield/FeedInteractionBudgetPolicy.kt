package com.example.videoshield

/**
 * Small, platform-independent policy for keeping non-critical feed work off the user's active
 * scroll/touch window. It is intentionally event-driven: callers update the last interaction time
 * from real input/scroll callbacks and schedule at most one delayed retry for the remaining quiet
 * window. There is no polling cadence.
 */
object FeedInteractionBudgetPolicy {
    const val STARTUP_QUIET_WINDOW_MS = 320L
    const val HOME_PUBLISH_QUIET_WINDOW_MS = 220L

    fun remainingQuietMs(nowMs: Long, lastInteractionMs: Long, quietWindowMs: Long): Long {
        if (quietWindowMs <= 0L || lastInteractionMs <= 0L) return 0L
        return (lastInteractionMs + quietWindowMs - nowMs).coerceAtLeast(0L)
    }
}
