package com.example.videoshield

/**
 * One-tap baseline for the enhanced-client experience.
 * Community-backed network features stay opt-in and are intentionally not enabled here.
 */
object EnhancedClientDefaults {
    fun apply(preferences: ShieldPreferences) {
        preferences.shieldEnabled = true
        preferences.blockTrackers = true
        preferences.blockOpenInApp = true
        preferences.autoPiP = true
        preferences.backgroundControls = true
        preferences.fullscreenGestures = true
        preferences.playbackRecovery = true
        preferences.screenOffPlayback = true
        preferences.memoryHardening = true
        preferences.amoledTheme = true
        preferences.compactYouTubeChrome = true
        preferences.autoRepeat = false
        preferences.preferredQuality = ShieldPreferences.QUALITY_AUTO
        preferences.preferredQualityMobile = ShieldPreferences.QUALITY_AUTO
        preferences.rememberHistory = true
        preferences.resumePlayback = true
        preferences.autoAdvanceQueue = true
        preferences.playbackSpeed = 1.0f
        preferences.gestureSensitivity = 1.0f
        preferences.doubleTapSeekSeconds = 10
    }
}
