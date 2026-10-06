package com.example.videoshield

import android.net.Uri

object YouTubeAdapter {
    private val videoIdRegex = Regex("[A-Za-z0-9_-]{6,32}")

    fun isYouTubeHost(host: String?): Boolean {
        val value = host?.trim()?.lowercase().orEmpty()
        return value == "youtube.com" || value.endsWith(".youtube.com") || value == "youtu.be"
    }

    fun isTrustedBridgeUrl(url: String?): Boolean = try {
        val uri = Uri.parse(url.orEmpty())
        (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) && isYouTubeHost(uri.host)
    } catch (_: Exception) {
        false
    }

    fun normalizeIncomingUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val trimmed = raw.trim()
            val uri = Uri.parse(trimmed)
            val host = uri.host?.lowercase().orEmpty()
            when {
                host == "youtu.be" -> {
                    val id = uri.pathSegments.firstOrNull()?.takeIf { validVideoId(it) }
                    if (id == null) null else canonicalWatchUri(id, uri).toString()
                }
                host == "youtube.com" || host == "www.youtube.com" ->
                    uri.buildUpon().scheme("https").authority("m.youtube.com").build().toString()
                host.endsWith(".youtube.com") -> trimmed
                else -> trimmed
            }
        } catch (_: Exception) {
            null
        }
    }

    fun rewriteShorts(uri: Uri): Uri? {
        if (!isYouTubeHost(uri.host)) return null
        val segments = uri.pathSegments
        if (segments.size >= 2 && segments[0].equals("shorts", true)) {
            val id = segments[1].takeIf { validVideoId(it) } ?: return null
            return canonicalWatchUri(id, uri)
        }
        return null
    }

    fun isWatchUrl(url: String?): Boolean = try {
        val uri = Uri.parse(url.orEmpty())
        isYouTubeHost(uri.host) && uri.path?.trimEnd('/') == "/watch"
    } catch (_: Exception) {
        false
    }

    fun videoIdFromUrl(url: String?): String? {
        return try {
            val uri = Uri.parse(url.orEmpty())
            if (!isYouTubeHost(uri.host)) {
                null
            } else when {
                uri.host.equals("youtu.be", true) -> uri.pathSegments.firstOrNull()?.takeIf { validVideoId(it) }
                uri.path?.trimEnd('/') == "/watch" -> uri.getQueryParameter("v")?.takeIf { validVideoId(it) }
                uri.pathSegments.firstOrNull()?.equals("shorts", true) == true -> uri.pathSegments.getOrNull(1)?.takeIf { validVideoId(it) }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun canonicalWatchUri(videoId: String, source: Uri): Uri {
        val builder = Uri.parse("https://m.youtube.com/watch").buildUpon()
            .appendQueryParameter("v", videoId)

        // Preserve navigation/playback context without copying another video id.
        val allowed = setOf("t", "start", "list", "index", "si", "pp")
        source.queryParameterNames.forEach { name ->
            if (name in allowed) source.getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
        }
        source.fragment?.takeIf { it.isNotBlank() }?.let { builder.fragment(it.take(160)) }
        return builder.build()
    }

    private fun validVideoId(value: String): Boolean = videoIdRegex.matches(value)
}
