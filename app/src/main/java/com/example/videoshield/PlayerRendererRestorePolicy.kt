package com.example.videoshield

data class PlayerRendererBridgeState(
    val playing: Boolean,
    val buffering: Boolean
)

/** Pure rules for holding native playback intent while a replacement renderer warms up. */
object PlayerRendererRestorePolicy {
    fun bridgeState(
        pending: Boolean,
        desiredPlaying: Boolean,
        expectedVideoId: String,
        reportedVideoId: String,
        reportedPlaying: Boolean,
        reportedBuffering: Boolean
    ): PlayerRendererBridgeState {
        val sameTarget = expectedVideoId.isBlank() || reportedVideoId.isBlank() || expectedVideoId == reportedVideoId
        if (!pending || !sameTarget) return PlayerRendererBridgeState(reportedPlaying, reportedBuffering)
        return PlayerRendererBridgeState(
            playing = desiredPlaying,
            buffering = desiredPlaying && (!reportedPlaying || reportedBuffering)
        )
    }

    fun canApply(expectedVideoId: String, reportedVideoId: String): Boolean =
        reportedVideoId.isNotBlank() && (expectedVideoId.isBlank() || expectedVideoId == reportedVideoId)

    fun shouldSeek(targetMs: Long, reportedMs: Long): Boolean =
        targetMs >= 5_000L && kotlin.math.abs(targetMs - reportedMs) >= 2_500L
}
