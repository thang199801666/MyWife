package com.example.videoshield

object NetworkRuleMatcher {
    fun matches(fragment: String, path: String, query: String?): Boolean {
        val rule = fragment.lowercase(java.util.Locale.ROOT)
        if (rule.isBlank()) return false
        val targetPath = path.lowercase(java.util.Locale.ROOT)
        if ('?' !in rule) return targetPath.contains(rule)
        val rulePath = rule.substringBefore('?')
        val queryRule = rule.substringAfter('?')
        return rulePath.isNotBlank() && targetPath.contains(rulePath) && queryRule.isNotBlank() &&
            query.orEmpty().lowercase(java.util.Locale.ROOT).split('&').any { it.startsWith(queryRule) }
    }
    fun isMediaHost(host: String) = host == "googlevideo.com" || host.endsWith(".googlevideo.com")
}
