package com.example.videoshield

import android.net.Uri

class FilterEngine(
    private val preferences: ShieldPreferences,
    private val rulePackManager: RulePackManager
) {
    @Volatile
    var pageWhitelisted: Boolean = false

    fun shouldBlock(uri: Uri): Boolean {
        if (!preferences.shieldEnabled || preferences.safeMode || pageWhitelisted) return false

        val host = uri.host?.lowercase() ?: return false
        val path = uri.path.orEmpty()
        val rules = rulePackManager.active()

        // Main YouTube media and ads can share googlevideo.com. Never blanket-block it.
        if (NetworkRuleMatcher.isMediaHost(host)) return false

        if (rules.blockedHosts.any { host == it || host.endsWith(".$it") }) return true

        if (rules.blockedPathFragments.any { NetworkRuleMatcher.matches(it, path, uri.encodedQuery) }) return true
        return preferences.blockTrackers && rules.trackerFragments.any { NetworkRuleMatcher.matches(it, path, uri.encodedQuery) }
    }

    fun rewriteNavigation(uri: Uri): Uri? {
        if (!preferences.blockShorts) return null
        return YouTubeAdapter.rewriteShorts(uri)
    }
}
