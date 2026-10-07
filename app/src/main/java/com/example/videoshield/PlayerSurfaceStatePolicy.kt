package com.example.videoshield

enum class PlayerSurfaceState { HIDDEN, EXPANDED, MINI }

enum class PlayerSurfaceAction { EXPAND, MINIMIZE, HIDE }

/** Pure transition policy used by the runtime controller and release stress fixtures. */
object PlayerSurfaceStatePolicy {
    fun next(current: PlayerSurfaceState, action: PlayerSurfaceAction): PlayerSurfaceState = when (action) {
        PlayerSurfaceAction.EXPAND -> PlayerSurfaceState.EXPANDED
        PlayerSurfaceAction.MINIMIZE -> if (current == PlayerSurfaceState.HIDDEN) PlayerSurfaceState.HIDDEN else PlayerSurfaceState.MINI
        PlayerSurfaceAction.HIDE -> PlayerSurfaceState.HIDDEN
    }

    fun restore(savedName: String?): PlayerSurfaceState =
        runCatching { PlayerSurfaceState.valueOf(savedName.orEmpty()) }.getOrNull() ?: PlayerSurfaceState.HIDDEN
}
