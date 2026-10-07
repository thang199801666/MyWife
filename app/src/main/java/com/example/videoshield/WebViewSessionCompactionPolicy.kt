package com.example.videoshield

/** Pressure-aware thresholds for WebView history serialization/compaction. */
data class WebViewSessionBudget(
    val browseFirstCompactLimit: Int,
    val browsePostCompactLimit: Int,
    val browseRichStateLimit: Int,
    val playerRichStateLimit: Int,
    val scrollSnapshotLimit: Int,
    val visibleCompactionDelayMs: Long,
    val hiddenCompactionDelayMs: Long
)

object WebViewSessionCompactionPolicy {
    const val BROWSE_RICH_STATE_LIMIT = 12
    const val BROWSE_FIRST_COMPACT_LIMIT = 24
    const val BROWSE_POST_COMPACT_LIMIT = 8
    const val PLAYER_RICH_STATE_LIMIT = 6

    fun budget(pressure: MemoryPressureTier): WebViewSessionBudget = when (pressure) {
        MemoryPressureTier.NORMAL -> WebViewSessionBudget(
            browseFirstCompactLimit = BROWSE_FIRST_COMPACT_LIMIT,
            browsePostCompactLimit = BROWSE_POST_COMPACT_LIMIT,
            browseRichStateLimit = BROWSE_RICH_STATE_LIMIT,
            playerRichStateLimit = PLAYER_RICH_STATE_LIMIT,
            scrollSnapshotLimit = 8,
            visibleCompactionDelayMs = 520L,
            hiddenCompactionDelayMs = 320L
        )
        MemoryPressureTier.MODERATE -> WebViewSessionBudget(
            browseFirstCompactLimit = 16,
            browsePostCompactLimit = 6,
            browseRichStateLimit = 8,
            playerRichStateLimit = 4,
            scrollSnapshotLimit = 6,
            visibleCompactionDelayMs = 260L,
            hiddenCompactionDelayMs = 120L
        )
        MemoryPressureTier.LOW -> WebViewSessionBudget(
            browseFirstCompactLimit = 10,
            browsePostCompactLimit = 4,
            browseRichStateLimit = 4,
            playerRichStateLimit = 2,
            scrollSnapshotLimit = 4,
            visibleCompactionDelayMs = 120L,
            hiddenCompactionDelayMs = 40L
        )
        MemoryPressureTier.CRITICAL -> WebViewSessionBudget(
            browseFirstCompactLimit = 4,
            browsePostCompactLimit = 2,
            browseRichStateLimit = 0,
            playerRichStateLimit = 0,
            scrollSnapshotLimit = 2,
            visibleCompactionDelayMs = 0L,
            hiddenCompactionDelayMs = 0L
        )
    }

    fun shouldCompactBrowse(
        historySize: Int,
        alreadyCompacted: Boolean,
        routeStable: Boolean,
        shorts: Boolean,
        pressure: MemoryPressureTier = MemoryPressureTier.NORMAL
    ): Boolean {
        if (!routeStable || shorts || historySize <= 0) return false
        val limits = budget(pressure)
        val limit = if (alreadyCompacted) limits.browsePostCompactLimit else limits.browseFirstCompactLimit
        return historySize > limit
    }

    fun compactionDelayMs(
        pressure: MemoryPressureTier,
        browseVisible: Boolean,
        powerConstrained: Boolean
    ): Long {
        val limits = budget(pressure)
        val base = if (browseVisible) limits.visibleCompactionDelayMs else limits.hiddenCompactionDelayMs
        return if (pressure == MemoryPressureTier.NORMAL && powerConstrained) maxOf(base, 900L) else base
    }

    fun shouldSerializeBrowseState(
        historySize: Int,
        shorts: Boolean,
        rendererRecovering: Boolean,
        historyCompacted: Boolean,
        pressure: MemoryPressureTier = MemoryPressureTier.NORMAL
    ): Boolean {
        val limit = budget(pressure).browseRichStateLimit
        return limit > 0 && !shorts && !rendererRecovering && !historyCompacted && historySize in 1..limit
    }

    fun shouldSerializePlayerState(
        historySize: Int,
        rendererRecovering: Boolean,
        pressure: MemoryPressureTier = MemoryPressureTier.NORMAL
    ): Boolean {
        val limit = budget(pressure).playerRichStateLimit
        return limit > 0 && !rendererRecovering && historySize in 1..limit
    }

    /** The Watch surface never exposes Chromium back/forward navigation to the user. */
    fun shouldCompactPlayerHistory(
        historySize: Int,
        rendererRecovering: Boolean,
        pressure: MemoryPressureTier
    ): Boolean = !rendererRecovering && pressure.atLeast(MemoryPressureTier.MODERATE) && historySize > 1

    fun scrollSnapshotLimit(pressure: MemoryPressureTier): Int = budget(pressure).scrollSnapshotLimit
}
