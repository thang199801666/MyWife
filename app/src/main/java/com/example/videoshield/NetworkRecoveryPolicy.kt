package com.example.videoshield

/** Playback information retained across a transient active-network loss or handoff. */
data class NetworkRecoveryCheckpoint(
    val videoId: String,
    val url: String,
    val positionMs: Long,
    val durationMs: Long,
    val wasPlaying: Boolean,
    val playbackRate: Float,
    val repeatEnabled: Boolean,
    val capturedAtElapsedMs: Long
)

data class NetworkRestoreInstruction(
    val seekToMs: Long?,
    val play: Boolean,
    val playbackRate: Float,
    val repeatEnabled: Boolean
)

/** Pure policy kept separate from Handler/WebView code so handoff/retry rules are stress-testable. */
object NetworkRecoveryPolicy {
    const val MAX_ESCALATIONS = 2
    const val MAX_CHECKPOINT_AGE_MS = 120_000L

    fun reconnectSettleDelayMs(online: Boolean, metered: Boolean, activeLinkChanged: Boolean): Long = when {
        !online -> Long.MAX_VALUE
        activeLinkChanged && metered -> 1_800L
        activeLinkChanged -> 1_200L
        metered -> 1_400L
        else -> 900L
    }

    fun verificationDelayMs(escalation: Int): Long = when (escalation.coerceAtLeast(0)) {
        0 -> 4_500L
        1 -> 8_000L
        else -> 12_000L
    }

    fun restorePositionMs(checkpoint: NetworkRecoveryCheckpoint, currentPositionMs: Long): Long? {
        if (checkpoint.positionMs < 5_000L) return null
        val boundedSaved = if (checkpoint.durationMs > 0L) {
            checkpoint.positionMs.coerceIn(0L, checkpoint.durationMs)
        } else checkpoint.positionMs.coerceAtLeast(0L)
        return boundedSaved.takeIf { it > currentPositionMs.coerceAtLeast(0L) + 3_000L }
    }

    fun healthy(
        checkpoint: NetworkRecoveryCheckpoint,
        videoId: String,
        playing: Boolean,
        positionMs: Long,
        nowElapsedMs: Long
    ): Boolean {
        if (videoId.isBlank() || videoId != checkpoint.videoId) return false
        if (nowElapsedMs - checkpoint.capturedAtElapsedMs > MAX_CHECKPOINT_AGE_MS) return true
        val positionRecovered = positionMs + 3_000L >= checkpoint.positionMs.coerceAtLeast(0L)
        return if (checkpoint.wasPlaying) playing && positionRecovered else positionRecovered
    }

    fun instruction(
        checkpoint: NetworkRecoveryCheckpoint,
        currentPositionMs: Long,
        playing: Boolean
    ): NetworkRestoreInstruction = NetworkRestoreInstruction(
        seekToMs = restorePositionMs(checkpoint, currentPositionMs),
        play = checkpoint.wasPlaying && !playing,
        playbackRate = checkpoint.playbackRate.coerceIn(0.25f, 4f),
        repeatEnabled = checkpoint.repeatEnabled
    )
}
