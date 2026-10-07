package com.example.videoshield

import java.net.URI

/**
 * Pure input resolver for the address bar and shared-text entry points.
 * Keeping this outside MainActivity makes navigation behavior deterministic and regression-testable.
 */
object NavigationTargetResolver {
    private val firstHttpUrl = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)

    fun resolveAddressInput(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank()) return null

        if (isHttpUrl(value)) return value
        if (looksLikeHost(value)) return "https://$value"

        // Match YouTube's default search surface: start unfiltered, then let the
        // dedicated search chips narrow to Videos / Shorts / Channels / Playlists.
        return SearchFilterPolicy.buildUrl(value, SearchFilterState())
    }

    fun extractFirstHttpUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return firstHttpUrl.find(text)?.value?.trimTrailingPunctuation()
    }

    private fun isHttpUrl(value: String): Boolean = try {
        val scheme = URI(value).scheme?.lowercase()
        scheme == "http" || scheme == "https"
    } catch (_: Exception) {
        false
    }

    private fun looksLikeHost(value: String): Boolean {
        if (value.any(Char::isWhitespace)) return false
        if (!value.contains('.')) return false
        if (value.startsWith('.') || value.endsWith('.')) return false
        return value.none { it in listOf('<', '>', '"', '\'', '\\') }
    }

    private fun String.trimTrailingPunctuation(): String = trimEnd('.', ',', ';', ':', ')', ']', '}')
}
