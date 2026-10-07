package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTransitionPolicyTest {
    @Test fun duplicateExpandedPlayerRequestIsNoOp() {
        assertEquals(
            PlayerNavigationAction.NO_OP,
            NavigationTransitionPolicy.playerAction(
                requestedVideoId = "abc",
                activeRouteVideoId = "abc",
                loadedVideoId = null,
                surfaceVisible = true,
                surfaceExpanded = true,
                expandRequested = true
            )
        )
    }

    @Test fun existingMiniPlayerExpandsWithoutReload() {
        assertEquals(
            PlayerNavigationAction.EXPAND_EXISTING,
            NavigationTransitionPolicy.playerAction(
                requestedVideoId = "abc",
                activeRouteVideoId = "abc",
                loadedVideoId = "abc",
                surfaceVisible = true,
                surfaceExpanded = false,
                expandRequested = true
            )
        )
    }

    @Test fun existingExpandedPlayerCanMinimizeWithoutReload() {
        assertEquals(
            PlayerNavigationAction.MINIMIZE_EXISTING,
            NavigationTransitionPolicy.playerAction(
                requestedVideoId = "abc",
                activeRouteVideoId = "abc",
                loadedVideoId = "abc",
                surfaceVisible = true,
                surfaceExpanded = true,
                expandRequested = false
            )
        )
    }

    @Test fun newVideoLoadsWithoutReusingCurrentSurfaceRequest() {
        assertEquals(
            PlayerNavigationAction.LOAD_TARGET,
            NavigationTransitionPolicy.playerAction(
                requestedVideoId = "next",
                activeRouteVideoId = "old",
                loadedVideoId = "old",
                surfaceVisible = true,
                surfaceExpanded = true,
                expandRequested = true
            )
        )
    }

    @Test fun shortsSwipesShareOneNativeUiKeyButSearchQueriesDoNot() {
        assertEquals("SHORTS", NavigationTransitionPolicy.browseUiKey("SHORTS", ""))
        assertEquals("SHORTS", NavigationTransitionPolicy.browseUiKey("SHORTS", "ignored"))
        assertEquals("SEARCH:cats:", NavigationTransitionPolicy.browseUiKey("SEARCH", "cats"))
        assertEquals("SEARCH:dogs:", NavigationTransitionPolicy.browseUiKey("SEARCH", "dogs"))
        assertEquals(
            "SEARCH:cats:https://m.youtube.com/results?search_query=cats&sp=video",
            NavigationTransitionPolicy.browseUiKey(
                "SEARCH", "cats", "https://m.youtube.com/results?search_query=cats&sp=video"
            )
        )
    }
}
