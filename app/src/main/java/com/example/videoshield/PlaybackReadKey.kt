package com.example.videoshield

/** Identity of a database read; a seek or reload invalidates it even for the same video. */
data class PlaybackReadKey(val videoId: String, val navigationGeneration: Long) {
    fun matches(currentVideoId: String, currentGeneration: Long): Boolean =
        videoId == currentVideoId && navigationGeneration == currentGeneration

    fun resumeTarget(saved: VideoItem?, positionMs: Long, enabled: Boolean, historyClearedAt: Long): Long? {
        if (!enabled || saved == null || saved.videoId != videoId || saved.lastPlayedAt < historyClearedAt) return null
        return saved.positionMs.takeIf { PlaybackSessionCoordinator.shouldResume(saved, positionMs) }
    }
}
