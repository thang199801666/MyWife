package com.example.videoshield

import android.net.Uri
import java.util.Locale

class FilterEngine(
    private val preferences: ShieldPreferences,
    private val rulePackManager: RulePackManager
) {
    private data class RuntimePolicy(
        val shieldEnabled: Boolean,
        val safeMode: Boolean,
        val blockTrackers: Boolean,
        val blockShorts: Boolean,
        val rules: RulePack
    )

    @Volatile
    private var policy: RuntimePolicy = readPolicy()

    @Volatile
    var pageWhitelisted: Boolean = false

    /**
     * Refresh only at preference/rule boundaries. shouldInterceptRequest() is a very hot
     * Chromium callback and must not hit SharedPreferences several times per subresource.
     */
    fun refresh() {
        policy = readPolicy()
    }

    fun shouldBlock(uri: Uri): Boolean {
        val current = policy
        if (!current.shieldEnabled || current.safeMode || pageWhitelisted) return false

        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        val query = uri.encodedQuery?.lowercase(Locale.ROOT)
        val rules = current.rules

        // Main YouTube media and ads can share googlevideo.com. Never blanket-block it.
        if (NetworkRuleMatcher.isMediaHost(host)) return false

        if (rules.blockedHosts.any { host == it || host.endsWith(".$it") }) return true

        if (rules.blockedPathFragments.any { NetworkRuleMatcher.matchesNormalized(it, path, query) }) return true
        return current.blockTrackers && rules.trackerFragments.any { NetworkRuleMatcher.matchesNormalized(it, path, query) }
    }

    fun rewriteNavigation(uri: Uri): Uri? {
        if (!policy.blockShorts) return null
        return YouTubeAdapter.rewriteShorts(uri)
    }

    private fun readPolicy(): RuntimePolicy = RuntimePolicy(
        shieldEnabled = preferences.shieldEnabled,
        safeMode = preferences.safeMode,
        blockTrackers = preferences.blockTrackers,
        blockShorts = preferences.blockShorts,
        rules = rulePackManager.active()
    )
}
