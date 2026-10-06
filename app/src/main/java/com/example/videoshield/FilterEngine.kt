package com.example.videoshield

import android.net.Uri
import java.util.Locale

class FilterEngine(
    private val preferences: ShieldPreferences,
    private val rulePackManager: RulePackManager
) {
    @Volatile
    var pageWhitelisted: Boolean = false

    fun shouldBlock(uri: Uri): Boolean {
        if (!preferences.shieldEnabled || preferences.safeMode || pageWhitelisted) return false

        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        val query = uri.encodedQuery?.lowercase(Locale.ROOT)
        val rules = rulePackManager.active()

        // Main YouTube media and ads can share googlevideo.com. Never blanket-block it.
        if (NetworkRuleMatcher.isMediaHost(host)) return false

        if (rules.blockedHosts.any { host == it || host.endsWith(".$it") }) return true

        if (rules.blockedPathFragments.any { NetworkRuleMatcher.matchesNormalized(it, path, query) }) return true
        return preferences.blockTrackers && rules.trackerFragments.any { NetworkRuleMatcher.matchesNormalized(it, path, query) }
    }

    fun rewriteNavigation(uri: Uri): Uri? {
        if (!preferences.blockShorts) return null
        return YouTubeAdapter.rewriteShorts(uri)
    }
}
