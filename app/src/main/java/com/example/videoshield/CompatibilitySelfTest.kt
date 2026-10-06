package com.example.videoshield

import android.net.Uri

data class CompatibilitySelfTestResult(val passed: Boolean, val summary: String)

object CompatibilitySelfTest {
    fun run(rules: RulePackManager): CompatibilitySelfTestResult {
        val checks = linkedMapOf<String, Boolean>()
        val active = rules.active()
        checks["rule schema"] = active.schemaVersion == RulePack.SUPPORTED_SCHEMA
        checks["rule version"] = active.ruleVersion > 0
        checks["network rules"] = active.blockedHosts.isNotEmpty() || active.blockedPathFragments.isNotEmpty()
        checks["ad selectors"] = active.adSelectors.isNotEmpty()
        checks["skip selectors"] = active.skipSelectors.isNotEmpty()

        checks["YouTube host"] = YouTubeAdapter.isTrustedBridgeUrl("https://m.youtube.com/watch?v=abcdefghijk")
        checks["root YouTube host"] = YouTubeAdapter.isTrustedBridgeUrl("https://youtube.com/watch?v=abcdefghijk")
        checks["reject foreign host"] = !YouTubeAdapter.isTrustedBridgeUrl("https://example.com/watch?v=abcdefghijk")
        checks["reject suffix attack"] = !YouTubeAdapter.isTrustedBridgeUrl("https://youtube.com.evil.example/watch?v=abcdefghijk")
        checks["reject userinfo attack"] = !YouTubeAdapter.isTrustedBridgeUrl("https://youtube.com@evil.example/watch?v=abcdefghijk")
        checks["watch fixture"] = YouTubeAdapter.isWatchUrl("https://www.youtube.com/watch?v=abcdefghijk")
        checks["watch trailing slash"] = YouTubeAdapter.isWatchUrl("https://m.youtube.com/watch/?v=abcdefghijk")

        val rewrittenShort = YouTubeAdapter.rewriteShorts(
            Uri.parse("https://m.youtube.com/shorts/abcdefghijk?t=42&list=PLfixture")
        )
        checks["shorts fixture"] = rewrittenShort?.getQueryParameter("v") == "abcdefghijk"
        checks["shorts timestamp preserved"] = rewrittenShort?.getQueryParameter("t") == "42"
        checks["shorts list preserved"] = rewrittenShort?.getQueryParameter("list") == "PLfixture"

        val shortLink = YouTubeAdapter.normalizeIncomingUrl("https://youtu.be/abcdefghijk?t=33&list=PLfixture")
        checks["youtu.be fixture"] = YouTubeAdapter.videoIdFromUrl(shortLink) == "abcdefghijk"
        checks["youtu.be timestamp preserved"] = Uri.parse(shortLink.orEmpty()).getQueryParameter("t") == "33"
        checks["extract watch id"] = YouTubeAdapter.videoIdFromUrl("https://m.youtube.com/watch?v=abcdefghijk") == "abcdefghijk"
        checks["extract shorts id"] = YouTubeAdapter.videoIdFromUrl("https://youtube.com/shorts/abcdefghijk") == "abcdefghijk"
        checks["reject malformed id"] = YouTubeAdapter.videoIdFromUrl("https://m.youtube.com/watch?v=bad!") == null

        checks["rule data only"] = sequenceOf(
            active.blockedHosts,
            active.blockedPathFragments,
            active.trackerFragments,
            active.adSelectors,
            active.skipSelectors,
            active.shortsSelectors,
            active.recommendationSelectors,
            active.commentSelectors,
            active.endScreenSelectors,
            active.openInAppSelectors
        ).flatten().none { it.contains("javascript:", true) || it.contains("<script", true) }

        val failed = checks.filterValues { !it }.keys
        val summary = if (failed.isEmpty()) {
            "${checks.size}/${checks.size} fixtures passed • rules v${active.ruleVersion}"
        } else {
            "${checks.size - failed.size}/${checks.size} passed • failed: ${failed.joinToString(", ")}"
        }
        return CompatibilitySelfTestResult(failed.isEmpty(), summary)
    }
}
