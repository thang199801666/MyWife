package com.example.videoshield

import android.content.Context

class ShieldPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("video_shield_prefs", Context.MODE_PRIVATE)

    var shieldEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHIELD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SHIELD_ENABLED, value).apply()

    var blockTrackers: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_TRACKERS, true)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_TRACKERS, value).apply()

    var blockShorts: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_SHORTS, false)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_SHORTS, value).apply()

    var blockRecommendations: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_RECOMMENDATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_RECOMMENDATIONS, value).apply()

    var blockComments: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_COMMENTS, false)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_COMMENTS, value).apply()

    var blockEndScreen: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_END_SCREEN, true)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_END_SCREEN, value).apply()

    var blockOpenInApp: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_OPEN_IN_APP, true)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_OPEN_IN_APP, value).apply()

    var autoPiP: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PIP, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PIP, value).apply()

    var backgroundControls: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_CONTROLS, true)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_CONTROLS, value).apply()

    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean("notification_permission_asked", false)
        set(value) = prefs.edit().putBoolean("notification_permission_asked", value).apply()

    var fullscreenGestures: Boolean
        get() = prefs.getBoolean(KEY_FULLSCREEN_GESTURES, true)
        set(value) = prefs.edit().putBoolean(KEY_FULLSCREEN_GESTURES, value).apply()

    var playbackRecovery: Boolean
        get() = prefs.getBoolean(KEY_PLAYBACK_RECOVERY, true)
        set(value) = prefs.edit().putBoolean(KEY_PLAYBACK_RECOVERY, value).apply()

    var screenOffPlayback: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_OFF_PLAYBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_SCREEN_OFF_PLAYBACK, value).apply()

    var memoryHardening: Boolean
        get() = prefs.getBoolean(KEY_MEMORY_HARDENING, true)
        set(value) = prefs.edit().putBoolean(KEY_MEMORY_HARDENING, value).apply()

    var gestureSensitivity: Float
        get() = prefs.getFloat(KEY_GESTURE_SENSITIVITY, 1.0f).coerceIn(0.65f, 1.60f)
        set(value) = prefs.edit().putFloat(KEY_GESTURE_SENSITIVITY, value.coerceIn(0.65f, 1.60f)).apply()

    var doubleTapSeekSeconds: Int
        get() = prefs.getInt(KEY_DOUBLE_TAP_SEEK_SECONDS, 10).coerceIn(5, 30)
        set(value) = prefs.edit().putInt(KEY_DOUBLE_TAP_SEEK_SECONDS, value.coerceIn(5, 30)).apply()

    /** Manual app chrome theme. YouTube-like light chrome is the default; black remains selectable. */
    var lightTheme: Boolean
        get() = prefs.getBoolean(KEY_LIGHT_THEME, true)
        set(value) {
            prefs.edit()
                .putBoolean(KEY_LIGHT_THEME, value)
                // Keep the existing WebView AMOLED policy aligned with the black app theme.
                .putBoolean(KEY_AMOLED_THEME, !value)
                .apply()
        }

    /** Compatibility alias used by the injected web theme policy. */
    var amoledTheme: Boolean
        get() = !lightTheme
        set(value) {
            if (value) lightTheme = false
            else prefs.edit().putBoolean(KEY_AMOLED_THEME, false).apply()
        }

    var compactYouTubeChrome: Boolean
        get() = prefs.getBoolean(KEY_COMPACT_YOUTUBE_CHROME, true)
        set(value) = prefs.edit().putBoolean(KEY_COMPACT_YOUTUBE_CHROME, value).apply()

    var autoRepeat: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REPEAT, false)
        set(value) {
            // Repeat is a user-facing playback mode that must survive an immediate
            // activity/process restart. commit() makes the state durable before the
            // UI reports the toggle as enabled.
            prefs.edit().putBoolean(KEY_AUTO_REPEAT, value).commit()
        }

    var preferredQuality: String
        get() = prefs.getString(KEY_PREFERRED_QUALITY, "adaptive")
            ?.takeIf { it in SUPPORTED_QUALITY_VALUES }?.let { if(it==QUALITY_AUTO) "adaptive" else it } ?: "adaptive"
        set(value) = prefs.edit().putString(
            KEY_PREFERRED_QUALITY,
            value.takeIf { it in SUPPORTED_QUALITY_VALUES } ?: QUALITY_AUTO
        ).apply()

    var preferredQualityMobile: String
        get() = prefs.getString(KEY_PREFERRED_QUALITY_MOBILE, "adaptive")
            ?.takeIf { it in SUPPORTED_QUALITY_VALUES }?.let { if(it==QUALITY_AUTO) "adaptive" else it } ?: "adaptive"
        set(value) = prefs.edit().putString(
            KEY_PREFERRED_QUALITY_MOBILE,
            value.takeIf { it in SUPPORTED_QUALITY_VALUES } ?: QUALITY_AUTO
        ).apply()

    fun preferredQualityForNetwork(metered: Boolean): String =
        if (metered) preferredQualityMobile else preferredQuality

    var communitySponsorSkip: Boolean
        get() = prefs.getBoolean(KEY_COMMUNITY_SPONSOR_SKIP, false)
        set(value) = prefs.edit().putBoolean(KEY_COMMUNITY_SPONSOR_SKIP, value).apply()

    var skipIntrosOutros: Boolean
        get() = prefs.getBoolean(KEY_SKIP_INTROS_OUTROS, false)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_INTROS_OUTROS, value).apply()

    fun communitySegmentCategories(): Set<String> = buildSet {
        if (communitySponsorSkip) {
            add(CommunitySegmentClient.CATEGORY_SPONSOR)
            add(CommunitySegmentClient.CATEGORY_SELF_PROMO)
            add(CommunitySegmentClient.CATEGORY_INTERACTION)
        }
        if (skipIntrosOutros) {
            add(CommunitySegmentClient.CATEGORY_INTRO)
            add(CommunitySegmentClient.CATEGORY_OUTRO)
        }
    }

    var rememberHistory: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_HISTORY, true)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER_HISTORY, value).apply()

    var personalizedSuggestions: Boolean
        get() = prefs.getBoolean("personalized_suggestions", true)
        set(value) = prefs.edit().putBoolean("personalized_suggestions", value).apply()

    var recommendationsSince: Long
        get() = prefs.getLong("recommendations_since", 0L)
        set(value) = prefs.edit().putLong("recommendations_since", value).apply()

    var historyClearedAt: Long
        get() = prefs.getLong("history_cleared_at", 0L)
        set(value) = prefs.edit().putLong("history_cleared_at", value).apply()

    var resumePlayback: Boolean
        get() = prefs.getBoolean(KEY_RESUME_PLAYBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_RESUME_PLAYBACK, value).apply()

    var autoAdvanceQueue: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ADVANCE_QUEUE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ADVANCE_QUEUE, value).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f).coerceIn(0.25f, 4.0f)
        set(value) {
            // Keep the selected rate stable across immediate app restarts as well.
            prefs.edit().putFloat(KEY_PLAYBACK_SPEED, value.coerceIn(0.25f, 4.0f)).commit()
        }

    var sleepTimerEndAtMs: Long
        get() = prefs.getLong(KEY_SLEEP_TIMER_END_AT_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_SLEEP_TIMER_END_AT_MS, value.coerceAtLeast(0L)).apply()

    var maxHistoryItems: Int
        get() = prefs.getInt(KEY_MAX_HISTORY_ITEMS, 300).coerceIn(25, 2000)
        set(value) = prefs.edit().putInt(KEY_MAX_HISTORY_ITEMS, value.coerceIn(25, 2000)).apply()

    var safeMode: Boolean
        get() = prefs.getBoolean(KEY_SAFE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_SAFE_MODE, value).apply()

    var safeModeReason: String
        get() = prefs.getString(KEY_SAFE_MODE_REASON, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SAFE_MODE_REASON, value.take(300)).apply()

    var autoRuleUpdates: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RULE_UPDATES, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RULE_UPDATES, value).apply()

    var lastRuleUpdateCheckMs: Long
        get() = prefs.getLong(KEY_LAST_RULE_UPDATE_CHECK_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_RULE_UPDATE_CHECK_MS, value).apply()

    var ruleUpdateUrl: String
        get() = prefs.getString(KEY_RULE_UPDATE_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_RULE_UPDATE_URL, value.trim().take(500)).apply()

    var lastUrl: String
        get() = prefs.getString(KEY_LAST_URL, HOME_URL) ?: HOME_URL
        set(value) = prefs.edit().putString(KEY_LAST_URL, value).apply()

    var lastBrowseUrl: String
        get() {
            val saved = prefs.getString(KEY_LAST_BROWSE_URL, null)
            if (!saved.isNullOrBlank()) return saved
            val legacy = lastUrl
            return if (YouTubeRoute.parse(legacy).isPlayback) HOME_URL else legacy
        }
        set(value) = prefs.edit().putString(KEY_LAST_BROWSE_URL, value).apply()

    val whitelistedChannels: Set<String>
        get() = prefs.getStringSet(KEY_WHITELISTED_CHANNELS, emptySet())?.toSet() ?: emptySet()

    fun isChannelWhitelisted(channel: String): Boolean {
        val key = channelKey(channel)
        return key.isNotEmpty() && whitelistedChannels.contains(key)
    }

    fun toggleChannelWhitelist(channel: String): Boolean {
        val key = channelKey(channel)
        if (key.isEmpty()) return false
        val set = whitelistedChannels.toMutableSet()
        val nowWhitelisted = if (set.contains(key)) {
            set.remove(key)
            false
        } else {
            set.add(key)
            true
        }
        prefs.edit().putStringSet(KEY_WHITELISTED_CHANNELS, set).apply()
        return nowWhitelisted
    }

    fun clearChannelWhitelist() {
        prefs.edit().remove(KEY_WHITELISTED_CHANNELS).apply()
    }

    private fun channelKey(channel: String): String = channel.trim().lowercase().take(160)

    companion object {
        const val HOME_URL = "https://m.youtube.com/"
        private const val KEY_SHIELD_ENABLED = "shield_enabled"
        private const val KEY_BLOCK_TRACKERS = "block_trackers"
        private const val KEY_BLOCK_SHORTS = "block_shorts"
        private const val KEY_BLOCK_RECOMMENDATIONS = "block_recommendations"
        private const val KEY_BLOCK_COMMENTS = "block_comments"
        private const val KEY_BLOCK_END_SCREEN = "block_end_screen"
        private const val KEY_BLOCK_OPEN_IN_APP = "block_open_in_app"
        private const val KEY_AUTO_PIP = "auto_pip"
        private const val KEY_BACKGROUND_CONTROLS = "background_controls"
        private const val KEY_FULLSCREEN_GESTURES = "fullscreen_gestures"
        private const val KEY_PLAYBACK_RECOVERY = "playback_recovery"
        private const val KEY_SCREEN_OFF_PLAYBACK = "screen_off_playback"
        private const val KEY_MEMORY_HARDENING = "memory_hardening"
        private const val KEY_GESTURE_SENSITIVITY = "gesture_sensitivity"
        private const val KEY_DOUBLE_TAP_SEEK_SECONDS = "double_tap_seek_seconds"
        private const val KEY_LIGHT_THEME = "light_theme"
        private const val KEY_AMOLED_THEME = "amoled_theme"
        private const val KEY_COMPACT_YOUTUBE_CHROME = "compact_youtube_chrome"
        private const val KEY_AUTO_REPEAT = "auto_repeat"
        private const val KEY_PREFERRED_QUALITY = "preferred_quality"
        private const val KEY_PREFERRED_QUALITY_MOBILE = "preferred_quality_mobile"
        private const val KEY_COMMUNITY_SPONSOR_SKIP = "community_sponsor_skip"
        private const val KEY_SKIP_INTROS_OUTROS = "skip_intros_outros"
        private const val KEY_REMEMBER_HISTORY = "remember_history"
        private const val KEY_RESUME_PLAYBACK = "resume_playback"
        private const val KEY_AUTO_ADVANCE_QUEUE = "auto_advance_queue"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_SLEEP_TIMER_END_AT_MS = "sleep_timer_end_at_ms"
        private const val KEY_MAX_HISTORY_ITEMS = "max_history_items"
        private const val KEY_SAFE_MODE = "safe_mode"
        private const val KEY_SAFE_MODE_REASON = "safe_mode_reason"
        private const val KEY_AUTO_RULE_UPDATES = "auto_rule_updates"
        private const val KEY_LAST_RULE_UPDATE_CHECK_MS = "last_rule_update_check_ms"
        private const val KEY_RULE_UPDATE_URL = "rule_update_url"
        private const val KEY_WHITELISTED_CHANNELS = "whitelisted_channels"
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_LAST_BROWSE_URL = "last_browse_url"

        const val QUALITY_AUTO = "auto"
        val SUPPORTED_QUALITY_VALUES = listOf(
            "adaptive", QUALITY_AUTO, "highres", "hd2160", "hd1440", "hd1080", "hd720", "large", "medium", "small", "tiny"
        )
    }
}
