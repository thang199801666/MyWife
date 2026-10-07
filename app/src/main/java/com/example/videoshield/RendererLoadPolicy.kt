package com.example.videoshield

/**
 * Keeps renderer image/decode work away from surface transitions.
 *
 * Media playback is never gated here; this policy controls only future image loads in the two
 * WebViews. Disables are immediate, while re-enables are intentionally delayed by a small bounded
 * amount so Chromium can settle the first compositor frame before thumbnails/avatars wake up.
 */
data class RendererLoadContext(
    val foreground: Boolean,
    val playerExpanded: Boolean,
    val playerTransitioning: Boolean,
    val pictureInPicture: Boolean,
    val browseObscured: Boolean,
    val powerConstrained: Boolean,
    val memoryPressure: MemoryPressureTier
)

data class RendererLoadDecision(
    val loadPlayerImages: Boolean,
    val loadBrowseImages: Boolean,
    val playerResumeDelayMs: Long,
    val browseResumeDelayMs: Long
)

object RendererLoadPolicy {
    fun resolve(context: RendererLoadContext): RendererLoadDecision {
        val player = context.foreground && context.playerExpanded &&
            !context.playerTransitioning && !context.pictureInPicture
        val browse = context.foreground && !context.browseObscured && !context.pictureInPicture
        val stressed = context.powerConstrained || context.memoryPressure != MemoryPressureTier.NORMAL
        return RendererLoadDecision(
            loadPlayerImages = player,
            loadBrowseImages = browse,
            playerResumeDelayMs = if (stressed) 280L else 160L,
            browseResumeDelayMs = if (stressed) 180L else 90L
        )
    }
}
