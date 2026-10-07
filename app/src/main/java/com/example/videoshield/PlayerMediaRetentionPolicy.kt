package com.example.videoshield

/**
 * Retention policy for the dedicated Watch renderer.
 *
 * Active playback/PiP always wins over memory trimming. Paused sessions keep a short warm
 * window for instant resume, then progressively drop preload/cache pressure when they stay
 * minimized or backgrounded. No decision reloads media or clears the current source.
 */
enum class PlayerMediaRetentionMode {
    ACTIVE,
    WARM_PAUSED,
    LEAN_PAUSED,
    COLD_PAUSED
}

data class PlayerMediaRetentionContext(
    val foreground: Boolean,
    val pictureInPicture: Boolean,
    val surfaceVisible: Boolean,
    val minimized: Boolean,
    val playing: Boolean,
    val buffering: Boolean,
    val memoryPressure: MemoryPressureTier
)

data class PlayerMediaRetentionDecision(
    val mode: PlayerMediaRetentionMode,
    val graceDelayMs: Long? = null,
    val pauseRenderer: Boolean = false,
    val clearMemoryCache: Boolean = false,
    val compactSession: Boolean = false
)

object PlayerMediaRetentionPolicy {
    private const val MINI_PAUSED_GRACE_MS = 30_000L
    private const val BACKGROUND_PAUSED_GRACE_MS = 45_000L
    private const val MODERATE_BACKGROUND_GRACE_MS = 12_000L

    fun immediate(context: PlayerMediaRetentionContext): PlayerMediaRetentionDecision {
        if (context.playing || context.buffering || context.pictureInPicture) {
            return PlayerMediaRetentionDecision(
                mode = PlayerMediaRetentionMode.ACTIVE,
                compactSession = context.memoryPressure.atLeast(MemoryPressureTier.LOW)
            )
        }

        return when (context.memoryPressure) {
            MemoryPressureTier.CRITICAL -> {
                if (context.foreground && context.surfaceVisible && !context.minimized) {
                    PlayerMediaRetentionDecision(
                        mode = PlayerMediaRetentionMode.LEAN_PAUSED,
                        compactSession = true
                    )
                } else {
                    PlayerMediaRetentionDecision(
                        mode = PlayerMediaRetentionMode.COLD_PAUSED,
                        pauseRenderer = true,
                        clearMemoryCache = true,
                        compactSession = true
                    )
                }
            }

            MemoryPressureTier.LOW -> {
                if (context.foreground && context.surfaceVisible && !context.minimized) {
                    PlayerMediaRetentionDecision(
                        mode = PlayerMediaRetentionMode.LEAN_PAUSED,
                        compactSession = true
                    )
                } else {
                    PlayerMediaRetentionDecision(
                        mode = PlayerMediaRetentionMode.COLD_PAUSED,
                        pauseRenderer = true,
                        clearMemoryCache = true,
                        compactSession = true
                    )
                }
            }

            MemoryPressureTier.MODERATE -> {
                when {
                    !context.foreground -> PlayerMediaRetentionDecision(
                        mode = PlayerMediaRetentionMode.LEAN_PAUSED,
                        graceDelayMs = MODERATE_BACKGROUND_GRACE_MS,
                        compactSession = true
                    )
                    context.minimized || !context.surfaceVisible -> PlayerMediaRetentionDecision(
                        mode = PlayerMediaRetentionMode.LEAN_PAUSED,
                        compactSession = true
                    )
                    else -> PlayerMediaRetentionDecision(PlayerMediaRetentionMode.WARM_PAUSED)
                }
            }

            MemoryPressureTier.NORMAL -> {
                when {
                    !context.foreground -> PlayerMediaRetentionDecision(
                        mode = PlayerMediaRetentionMode.WARM_PAUSED,
                        graceDelayMs = BACKGROUND_PAUSED_GRACE_MS
                    )
                    context.minimized || !context.surfaceVisible -> PlayerMediaRetentionDecision(
                        mode = PlayerMediaRetentionMode.WARM_PAUSED,
                        graceDelayMs = MINI_PAUSED_GRACE_MS
                    )
                    else -> PlayerMediaRetentionDecision(PlayerMediaRetentionMode.WARM_PAUSED)
                }
            }
        }
    }

    fun afterGrace(context: PlayerMediaRetentionContext): PlayerMediaRetentionDecision {
        if (context.playing || context.buffering || context.pictureInPicture) {
            return PlayerMediaRetentionDecision(
                mode = PlayerMediaRetentionMode.ACTIVE,
                compactSession = context.memoryPressure.atLeast(MemoryPressureTier.LOW)
            )
        }
        if (context.foreground && context.surfaceVisible && !context.minimized) {
            return immediate(context).copy(graceDelayMs = null)
        }
        return when (context.memoryPressure) {
            MemoryPressureTier.NORMAL -> {
                if (!context.foreground) {
                    PlayerMediaRetentionDecision(
                        mode = PlayerMediaRetentionMode.COLD_PAUSED,
                        pauseRenderer = true,
                        clearMemoryCache = true,
                        compactSession = true
                    )
                } else {
                    PlayerMediaRetentionDecision(
                        mode = PlayerMediaRetentionMode.LEAN_PAUSED,
                        compactSession = true
                    )
                }
            }
            MemoryPressureTier.MODERATE -> {
                if (!context.foreground) {
                    PlayerMediaRetentionDecision(
                        mode = PlayerMediaRetentionMode.COLD_PAUSED,
                        pauseRenderer = true,
                        clearMemoryCache = true,
                        compactSession = true
                    )
                } else {
                    PlayerMediaRetentionDecision(
                        mode = PlayerMediaRetentionMode.LEAN_PAUSED,
                        compactSession = true
                    )
                }
            }
            MemoryPressureTier.LOW,
            MemoryPressureTier.CRITICAL -> immediate(context).copy(graceDelayMs = null)
        }
    }

    fun shouldWakeRenderer(command: PlaybackCommand): Boolean = when (command) {
        PlaybackCommand.Play,
        PlaybackCommand.Toggle,
        PlaybackCommand.SeekBack,
        PlaybackCommand.SeekForward,
        PlaybackCommand.QueueNext,
        is PlaybackCommand.SeekTo,
        is PlaybackCommand.SetRate,
        is PlaybackCommand.SetRepeat -> true
        PlaybackCommand.Pause,
        is PlaybackCommand.Stop -> false
    }
}
