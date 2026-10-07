package com.example.videoshield

/**
 * Shared, renderer-agnostic policy for quantizing feed scroll velocity into a small number of
 * request/decode budgets. The JavaScript feed guards mirror these thresholds so behavior stays
 * predictable without asking native code to sample every scroll frame.
 */
enum class FeedVelocityBand { SLOW, MEDIUM, FAST }
enum class FeedMemoryBand { NORMAL, MODERATE, LOW, CRITICAL }

data class FeedPrefetchBudget(
    val band: FeedVelocityBand,
    val cardBudget: Int,
    val searchRequestBudget: Int,
    val imageBudget: Int,
    val aheadPx: Int,
    val behindPx: Int
)

object FeedVelocityPrefetchPolicy {
    const val MEDIUM_THRESHOLD_PX_PER_SEC = 900f
    const val FAST_THRESHOLD_PX_PER_SEC = 2200f

    fun band(pxPerSecond: Float): FeedVelocityBand = when {
        pxPerSecond >= FAST_THRESHOLD_PX_PER_SEC -> FeedVelocityBand.FAST
        pxPerSecond >= MEDIUM_THRESHOLD_PX_PER_SEC -> FeedVelocityBand.MEDIUM
        else -> FeedVelocityBand.SLOW
    }

    fun budget(
        viewportPx: Int,
        pxPerSecond: Float,
        constrained: Boolean = false,
        memory: FeedMemoryBand = FeedMemoryBand.NORMAL
    ): FeedPrefetchBudget {
        val viewport = viewportPx.coerceAtLeast(1)
        val pressure = memory.ordinal
        return when (val band = band(pxPerSecond)) {
            FeedVelocityBand.FAST -> FeedPrefetchBudget(
                band = band,
                cardBudget = if (pressure >= FeedMemoryBand.CRITICAL.ordinal) 1 else 2,
                searchRequestBudget = 1,
                imageBudget = 0,
                aheadPx = minOf(if (pressure >= FeedMemoryBand.LOW.ordinal) 220 else 320,
                    (viewport * if (pressure >= FeedMemoryBand.LOW.ordinal) 0.28f else 0.38f).toInt()),
                behindPx = if (pressure >= FeedMemoryBand.LOW.ordinal) 64 else 96
            )
            FeedVelocityBand.MEDIUM -> FeedPrefetchBudget(
                band = band,
                cardBudget = when {
                    pressure >= FeedMemoryBand.CRITICAL.ordinal -> 2
                    pressure >= FeedMemoryBand.LOW.ordinal -> 3
                    else -> 5
                },
                searchRequestBudget = if (pressure >= FeedMemoryBand.LOW.ordinal) 2 else 3,
                imageBudget = when {
                    pressure >= FeedMemoryBand.CRITICAL.ordinal -> 1
                    pressure >= FeedMemoryBand.LOW.ordinal -> 2
                    pressure >= FeedMemoryBand.MODERATE.ordinal -> 4
                    else -> 6
                },
                aheadPx = minOf(if (pressure >= FeedMemoryBand.LOW.ordinal) 360 else 560,
                    (viewport * if (pressure >= FeedMemoryBand.LOW.ordinal) 0.48f else 0.72f).toInt()),
                behindPx = if (pressure >= FeedMemoryBand.LOW.ordinal) 112 else 180
            )
            FeedVelocityBand.SLOW -> {
                val (cards, search, images, aheadScale, behind) = when (memory) {
                    FeedMemoryBand.CRITICAL -> listOf(3, 1, 2, 42, 160)
                    FeedMemoryBand.LOW -> listOf(5, 2, 6, 58, 220)
                    FeedMemoryBand.MODERATE -> listOf(8, 4, 12, 82, 280)
                    FeedMemoryBand.NORMAL -> listOf(12, 6, 24, if (constrained) 72 else 118,
                        if (constrained) 320 else (viewport * 0.58f).toInt())
                }
                FeedPrefetchBudget(
                    band = band,
                    cardBudget = cards,
                    searchRequestBudget = search,
                    imageBudget = images,
                    aheadPx = (viewport * (aheadScale / 100f)).toInt(),
                    behindPx = behind
                )
            }
        }
    }

    fun shouldSettleFling(previousBand: FeedVelocityBand): Boolean = previousBand != FeedVelocityBand.SLOW
}
