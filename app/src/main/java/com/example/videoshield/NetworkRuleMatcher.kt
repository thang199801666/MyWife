package com.example.videoshield

import java.util.Locale

object NetworkRuleMatcher {
    fun matches(fragment: String, path: String, query: String?): Boolean = matchesNormalized(
        fragment.lowercase(Locale.ROOT),
        path.lowercase(Locale.ROOT),
        query?.lowercase(Locale.ROOT)
    )

    /** Inputs are already lowercase; used by the hot WebView request path. */
    fun matchesNormalized(rule: String, targetPath: String, targetQuery: String?): Boolean {
        if (rule.isBlank()) return false
        if ('?' !in rule) return targetPath.contains(rule)
        val rulePath = rule.substringBefore('?')
        val queryRule = rule.substringAfter('?')
        return rulePath.isNotBlank() && targetPath.contains(rulePath) && queryRule.isNotBlank() &&
            targetQuery.orEmpty().split('&').any { it.startsWith(queryRule) }
    }

    fun isMediaHost(host: String) = host == "googlevideo.com" || host.endsWith(".googlevideo.com")
}
