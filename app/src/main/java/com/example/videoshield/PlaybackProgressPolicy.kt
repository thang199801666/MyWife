package com.example.videoshield

/** Pure playback-position normalization shared by live session and persisted snapshot logic. */
object PlaybackProgressPolicy {
    private const val MAX_EXTRAPOLATION_MS = 15_000L

    fun normalize(positionMs: Long, durationMs: Long): Long {
        val safePosition = positionMs.coerceAtLeast(0L)
        val safeDuration = durationMs.coerceAtLeast(0L)
        return if (safeDuration > 0L) safePosition.coerceAtMost(safeDuration) else safePosition
    }

    fun predict(
        positionMs: Long,
        durationMs: Long,
        reportedAtMs: Long,
        nowMs: Long,
        playing: Boolean,
        playbackRate: Float = 1f
    ): Long {
        val base = normalize(positionMs, durationMs)
        if (!playing || reportedAtMs <= 0L || nowMs <= reportedAtMs) return base

        val elapsed = (nowMs - reportedAtMs).coerceIn(0L, MAX_EXTRAPOLATION_MS)
        val rate = playbackRate.coerceIn(0.25f, 4f)
        val advanced = (elapsed.toDouble() * rate.toDouble()).toLong()
        return normalize(base + advanced, durationMs)
    }
}
