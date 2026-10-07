package com.example.videoshield

/**
 * Bounded native-cache maintenance for long SPA sessions.
 *
 * WebView/JS cleanup is event-driven on every real media/route transition. The comparatively
 * expensive native policy-string caches are dropped only occasionally so memory does not grow
 * across hours of use while normal navigation avoids repeated ~large script construction.
 */
data class LongSessionMaintenance(
    val trimPolicyScriptCaches: Boolean = false
)

class LongSessionResourcePolicy(
    private val playerCacheTrimEvery: Int = 18,
    private val browseCacheTrimEvery: Int = 24
) {
    private var playerTransitions = 0
    private var browseTransitions = 0

    fun onPlayerVideoChanged(): LongSessionMaintenance {
        playerTransitions++
        return LongSessionMaintenance(
            trimPolicyScriptCaches = playerCacheTrimEvery > 0 && playerTransitions % playerCacheTrimEvery == 0
        )
    }

    fun onBrowseDestinationChanged(): LongSessionMaintenance {
        browseTransitions++
        return LongSessionMaintenance(
            trimPolicyScriptCaches = browseCacheTrimEvery > 0 && browseTransitions % browseCacheTrimEvery == 0
        )
    }
}
