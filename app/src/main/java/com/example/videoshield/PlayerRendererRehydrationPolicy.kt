package com.example.videoshield

enum class PlayerRendererRecoveryAction {
    REHYDRATE_NOW,
    DEFER_UNTIL_FOREGROUND,
    DEFER_UNTIL_NEEDED,
    WAIT_FOR_MANUAL_RETRY
}

data class PlayerRendererRecoveryContext(
    val guardActive: Boolean,
    val enteringSafeMode: Boolean,
    val activityForeground: Boolean,
    val pictureInPicture: Boolean,
    val hasRecoverableTarget: Boolean,
    val playerVisible: Boolean,
    val hasPlaybackSession: Boolean
)

/**
 * Pure policy for player-only renderer recovery. A dead renderer must never force a healthy
 * browse Activity tree to restart. Background recovery is deferred so a killed renderer is not
 * immediately recreated while Android is actively trying to reclaim memory.
 */
object PlayerRendererRehydrationPolicy {
    fun resolve(context: PlayerRendererRecoveryContext): PlayerRendererRecoveryAction {
        if (context.guardActive && !context.enteringSafeMode) {
            return PlayerRendererRecoveryAction.WAIT_FOR_MANUAL_RETRY
        }
        if (!context.hasRecoverableTarget) {
            return PlayerRendererRecoveryAction.DEFER_UNTIL_NEEDED
        }
        if (!context.activityForeground && !context.pictureInPicture) {
            return PlayerRendererRecoveryAction.DEFER_UNTIL_FOREGROUND
        }
        if (context.playerVisible || context.hasPlaybackSession) {
            return PlayerRendererRecoveryAction.REHYDRATE_NOW
        }
        return PlayerRendererRecoveryAction.DEFER_UNTIL_NEEDED
    }
}
