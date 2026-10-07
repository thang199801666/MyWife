package com.example.videoshield

enum class BrowseRendererRecoveryAction {
    REHYDRATE_NOW,
    DEFER_UNTIL_VISIBLE,
    WAIT_FOR_MANUAL_RETRY,
    RECREATE_ACTIVITY
}

data class BrowseRendererRecoveryContext(
    val affectsPlayer: Boolean,
    val guardActive: Boolean,
    val enteringSafeMode: Boolean,
    val activityForeground: Boolean,
    val browseVisible: Boolean
)

/** Pure recovery policy so browse-only renderer loss never restarts a healthy player Activity. */
object BrowseRendererRehydrationPolicy {
    fun resolve(context: BrowseRendererRecoveryContext): BrowseRendererRecoveryAction {
        if (context.affectsPlayer) return BrowseRendererRecoveryAction.RECREATE_ACTIVITY
        if (context.guardActive && !context.enteringSafeMode) {
            return BrowseRendererRecoveryAction.WAIT_FOR_MANUAL_RETRY
        }
        if (!context.activityForeground || !context.browseVisible) {
            return BrowseRendererRecoveryAction.DEFER_UNTIL_VISIBLE
        }
        return BrowseRendererRecoveryAction.REHYDRATE_NOW
    }
}
