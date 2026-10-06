package com.example.videoshield

import android.net.Uri

enum class YouTubeDestination {
    HOME,
    WATCH,
    SHORTS,
    SEARCH,
    SUBSCRIPTIONS,
    PLAYLIST,
    CHANNEL,
    OTHER
}

data class YouTubeRoute(
    val destination: YouTubeDestination,
    val url: String,
    val videoId: String = "",
    val query: String = ""
) {
    /** True for routes that represent a concrete media item. */
    val isPlayback: Boolean get() = destination == YouTubeDestination.WATCH ||
        (destination == YouTubeDestination.SHORTS && videoId.isNotBlank())

    /** Only classic /watch pages belong in the dedicated native player surface.
     * Shorts stay in the browse WebView so YouTube can preserve its vertical feed,
     * adjacent-item preload and swipe position.
     */
    val isNativePlayback: Boolean get() = destination == YouTubeDestination.WATCH && videoId.isNotBlank()

    companion object {
        const val HOME_URL = "https://m.youtube.com/"
        const val SHORTS_URL = "https://m.youtube.com/shorts/"
        const val SUBSCRIPTIONS_URL = "https://m.youtube.com/feed/subscriptions"

        fun parse(rawUrl: String?): YouTubeRoute {
            val url = rawUrl.orEmpty().trim()
            if (url.isBlank()) return YouTubeRoute(YouTubeDestination.HOME, HOME_URL)
            return try {
                val uri = Uri.parse(url)
                if (!YouTubeAdapter.isYouTubeHost(uri.host)) {
                    YouTubeRoute(YouTubeDestination.OTHER, url)
                } else {
                    val path = uri.path.orEmpty().trimEnd('/')
                    when {
                        path == "/watch" -> YouTubeRoute(
                            destination = YouTubeDestination.WATCH,
                            url = url,
                            videoId = uri.getQueryParameter("v").orEmpty()
                        )
                        path == "/shorts" || path.startsWith("/shorts/") -> YouTubeRoute(
                            destination = YouTubeDestination.SHORTS,
                            url = url,
                            videoId = uri.pathSegments.getOrNull(1).orEmpty()
                        )
                        path == "/results" -> YouTubeRoute(
                            destination = YouTubeDestination.SEARCH,
                            url = url,
                            query = uri.getQueryParameter("search_query").orEmpty()
                        )
                        path == "/feed/subscriptions" -> YouTubeRoute(YouTubeDestination.SUBSCRIPTIONS, url)
                        path == "/playlist" -> YouTubeRoute(YouTubeDestination.PLAYLIST, url)
                        path.startsWith("/@") || path.startsWith("/channel/") || path.startsWith("/c/") ->
                            YouTubeRoute(YouTubeDestination.CHANNEL, url)
                        path.isBlank() || path == "/" -> YouTubeRoute(YouTubeDestination.HOME, url)
                        else -> YouTubeRoute(YouTubeDestination.OTHER, url)
                    }
                }
            } catch (_: Exception) {
                YouTubeRoute(YouTubeDestination.OTHER, url)
            }
        }

        fun searchUrl(query: String): String? = NavigationTargetResolver.resolveAddressInput(query)
    }
}
