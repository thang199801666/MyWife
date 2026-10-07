package com.example.videoshield

/**
 * Renderer-agnostic limits mirrored by the browse/search JavaScript caches.
 *
 * The policy intentionally caches only derived tuning state, never media payloads or DOM-owned
 * content. A card can be reused while its identity and target tuning remain unchanged; periodic
 * revalidation prevents a long-lived SPA node from becoming permanently stale.
 */
object FeedCardReusePolicy {
    const val CARD_RETUNE_TTL_MS = 2_400L
    const val MIN_LOCALITY_SPAN_PX = 320
    const val LOCALITY_VIEWPORT_FRACTION = 0.72f

    fun localitySpanPx(viewportPx: Int): Int =
        maxOf(MIN_LOCALITY_SPAN_PX, (viewportPx.coerceAtLeast(1) * LOCALITY_VIEWPORT_FRACTION).toInt())

    fun localityBucket(scrollYPx: Int, viewportPx: Int): Int =
        scrollYPx.coerceAtLeast(0) / localitySpanPx(viewportPx)

    fun canReuseCard(
        connected: Boolean,
        identityMatches: Boolean,
        targetMatches: Boolean,
        ageMs: Long
    ): Boolean = connected && identityMatches && targetMatches &&
        ageMs in 0 until CARD_RETUNE_TTL_MS

    fun canReuseWindow(
        totalMatches: Boolean,
        rangeMatches: Boolean,
        firstNodeMatches: Boolean,
        middleNodeMatches: Boolean,
        lastNodeMatches: Boolean
    ): Boolean = totalMatches && rangeMatches && firstNodeMatches && middleNodeMatches && lastNodeMatches
}
