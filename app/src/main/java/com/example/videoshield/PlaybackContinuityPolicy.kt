package com.example.videoshield

/**
 * Decides whether a main-frame watch navigation can keep the current playback session alive
 * until the next media element reports its real state.  Clearing an actively playing session
 * creates an artificial pause in MediaSession/mini-player even though Chromium is simply
 * replacing the watch document.
 */
enum class PlaybackNavigationDisposition {
    CLEAR,
    HOLD_SESSION
}

object PlaybackContinuityPolicy {
    fun onNavigationStarted(
        hasSession: Boolean,
        playing: Boolean,
        currentVideoId: String,
        targetVideoId: String
    ): PlaybackNavigationDisposition {
        if (!hasSession || targetVideoId.isBlank()) return PlaybackNavigationDisposition.CLEAR
        // A reload/canonical URL change for the same video should never tear down the session,
        // even when paused. For a different video, preserve only an active play intent.
        if (targetVideoId == currentVideoId) return PlaybackNavigationDisposition.HOLD_SESSION
        return if (playing) PlaybackNavigationDisposition.HOLD_SESSION else PlaybackNavigationDisposition.CLEAR
    }
}
