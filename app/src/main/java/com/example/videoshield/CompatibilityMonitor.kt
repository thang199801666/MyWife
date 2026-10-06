package com.example.videoshield

class CompatibilityMonitor(
    private val preferences: ShieldPreferences,
    private val rulePackManager: RulePackManager,
    private val onSafeMode: (reason: String, rolledBack: Boolean) -> Unit
) {
    private var consecutiveFailures = 0
    private var lastReportedUrl = ""

    fun onNavigationStarted(url: String?) {
        if (url != lastReportedUrl) {
            lastReportedUrl = ""
        }
    }

    fun onReport(url: String?, playerFound: Boolean, videoFound: Boolean, scriptErrors: Int, ruleVersion: Int) {
        if (!preferences.shieldEnabled || preferences.safeMode) return
        if (!YouTubeAdapter.isWatchUrl(url)) return
        val key = url.orEmpty()
        if (key == lastReportedUrl) return
        lastReportedUrl = key

        val versionMatches = ruleVersion == rulePackManager.active().ruleVersion
        val healthy = playerFound && videoFound && scriptErrors <= 1 && versionMatches
        if (healthy) {
            consecutiveFailures = 0
            return
        }

        consecutiveFailures++
        if (consecutiveFailures < 3) return
        consecutiveFailures = 0

        val rolledBack = if (rulePackManager.hasRollback()) rulePackManager.rollback() else false
        preferences.safeMode = true
        preferences.safeModeReason = buildString {
            append("Compatibility guard triggered")
            if (!versionMatches) append(" (rule version mismatch)")
            else if (!videoFound) append(" (video element not detected)")
            else if (!playerFound) append(" (player not detected)")
            else if (scriptErrors > 1) append(" (script errors: $scriptErrors)")
        }
        onSafeMode(preferences.safeModeReason, rolledBack)
    }

    fun reset() {
        consecutiveFailures = 0
        lastReportedUrl = ""
    }
}
