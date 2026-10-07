package com.example.videoshield

/**
 * Rejects stale/duplicate ended callbacks before they can mutate the native queue.
 * JavaScript already de-duplicates the normal ended event; this is the native release-safety
 * boundary for callbacks that arrive late after navigation or renderer recovery.
 */
class PlaybackEndGuard {
    private var acceptedVideoId = ""
    private var acceptedGeneration = Long.MIN_VALUE

    fun shouldAutoAdvance(
        reportedVideoId: String,
        currentVideoId: String,
        routeVideoId: String,
        navigationGeneration: Long,
        repeatEnabled: Boolean,
        autoAdvanceEnabled: Boolean
    ): Boolean {
        val reported = reportedVideoId.trim()
        if (reported.isEmpty() || repeatEnabled || !autoAdvanceEnabled) return false
        if (currentVideoId.isBlank() || reported != currentVideoId) return false
        if (routeVideoId.isNotBlank() && reported != routeVideoId) return false
        if (acceptedVideoId == reported && acceptedGeneration == navigationGeneration) return false
        acceptedVideoId = reported
        acceptedGeneration = navigationGeneration
        return true
    }

    fun reset() {
        acceptedVideoId = ""
        acceptedGeneration = Long.MIN_VALUE
    }
}
