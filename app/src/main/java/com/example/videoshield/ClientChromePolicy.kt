package com.example.videoshield

/**
 * Presentation policy for the enhanced-client shell.
 * Keeps browser/debug implementation details out of the primary UI.
 */
data class ClientChromeState(
    val showAppBar: Boolean,
    val showPlaybackActions: Boolean,
    val navSelection: YouTubeDestination,
    val searchHint: String
)

object ClientChromePolicy {
    fun forRoute(
        route: YouTubeRoute,
        fullscreen: Boolean,
        pictureInPicture: Boolean,
        minimalPlaybackChrome: Boolean = false
    ): ClientChromeState {
        if (fullscreen || pictureInPicture) {
            return ClientChromeState(
                showAppBar = false,
                showPlaybackActions = false,
                navSelection = route.destination,
                searchHint = "Search YouTube"
            )
        }
        return ClientChromeState(
            // Shorts already render their own top overlay. Keeping the native app bar above it
            // duplicates chrome and shortens the vertical video viewport, so enter an immersive
            // browse presentation for the entire Shorts route. This changes only on route
            // transitions (not while idling/auto-hiding) and therefore avoids frame-cadence relayout.
            showAppBar = route.destination != YouTubeDestination.SHORTS &&
                !(minimalPlaybackChrome && route.isNativePlayback),
            showPlaybackActions = route.isNativePlayback,
            navSelection = route.destination,
            searchHint = if (route.destination == YouTubeDestination.SEARCH && route.query.isNotBlank()) {
                route.query.take(80)
            } else {
                "Search YouTube"
            }
        )
    }
}
