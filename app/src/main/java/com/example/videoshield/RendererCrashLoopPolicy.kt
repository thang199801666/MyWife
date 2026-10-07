package com.example.videoshield

/** Pure time-window/weighting policy so wall-clock rollback cannot pin crash-loop protection forever. */
object RendererCrashLoopPolicy {
    const val CRASH_THRESHOLD = 3
    const val TERMINATION_THRESHOLD = 5
    const val SCORE_THRESHOLD = 6

    fun windowExpired(firstAt: Long, now: Long, windowMs: Long): Boolean =
        firstAt <= 0L || now < firstAt || now - firstAt > windowMs.coerceAtLeast(0L)

    fun stableWindowReached(lastAt: Long, now: Long, stableMs: Long): Boolean =
        lastAt <= 0L || now < lastAt || now - lastAt >= stableMs.coerceAtLeast(0L)

    /**
     * A real Chromium crash is stronger evidence than an Android renderer reclaim. Reclaims are
     * expected under memory pressure, so they need a larger burst before safe mode is enabled.
     */
    fun guardActive(crashes: Int, terminations: Int): Boolean {
        val crashCount = crashes.coerceAtLeast(0)
        val terminationCount = terminations.coerceAtLeast(0)
        val weightedScore = crashCount * 2 + terminationCount
        return crashCount >= CRASH_THRESHOLD ||
            terminationCount >= TERMINATION_THRESHOLD ||
            weightedScore >= SCORE_THRESHOLD
    }
}
