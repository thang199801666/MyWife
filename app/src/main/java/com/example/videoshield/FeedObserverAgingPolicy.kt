package com.example.videoshield

/**
 * Bounds IntersectionObserver target retention during long Home/Search SPA sessions.
 *
 * The JavaScript surfaces mirror these values so observer targets age out around the current
 * viewport instead of accumulating every card the user has scrolled past. Keeping this policy
 * pure makes the retention envelope easy to regression-test without an Android runtime.
 */
enum class FeedObserverSurface {
    BROWSE,
    SEARCH
}

data class FeedObserverAgingBudget(
    val scanWindow: Int,
    val retainBefore: Int,
    val retainAfter: Int,
    val hardCap: Int,
    val stateResetPruneCount: Int
) {
    init {
        require(scanWindow > 0)
        require(retainBefore >= 0)
        require(retainAfter >= 0)
        require(hardCap >= scanWindow)
        require(stateResetPruneCount > 0)
    }

    val retentionEnvelope: Int
        get() = scanWindow + retainBefore + retainAfter
}

object FeedObserverAgingPolicy {
    fun budget(
        surface: FeedObserverSurface,
        pressure: MemoryPressureTier,
        constrained: Boolean = false
    ): FeedObserverAgingBudget = when (surface) {
        FeedObserverSurface.BROWSE -> browseBudget(pressure, constrained)
        FeedObserverSurface.SEARCH -> searchBudget(pressure)
    }

    fun shouldResetState(prunedSinceReset: Int, budget: FeedObserverAgingBudget): Boolean =
        prunedSinceReset >= budget.stateResetPruneCount

    private fun browseBudget(
        pressure: MemoryPressureTier,
        constrained: Boolean
    ): FeedObserverAgingBudget = when (pressure) {
        MemoryPressureTier.CRITICAL -> FeedObserverAgingBudget(56, 8, 12, 80, 200)
        MemoryPressureTier.LOW -> FeedObserverAgingBudget(80, 12, 16, 112, 320)
        MemoryPressureTier.MODERATE -> FeedObserverAgingBudget(112, 20, 24, 160, 480)
        MemoryPressureTier.NORMAL -> if (constrained) {
            FeedObserverAgingBudget(96, 16, 24, 144, 480)
        } else {
            FeedObserverAgingBudget(144, 24, 32, 200, 640)
        }
    }

    private fun searchBudget(pressure: MemoryPressureTier): FeedObserverAgingBudget = when (pressure) {
        MemoryPressureTier.CRITICAL -> FeedObserverAgingBudget(48, 8, 10, 72, 160)
        MemoryPressureTier.LOW -> FeedObserverAgingBudget(64, 10, 14, 96, 240)
        MemoryPressureTier.MODERATE -> FeedObserverAgingBudget(88, 14, 20, 128, 320)
        MemoryPressureTier.NORMAL -> FeedObserverAgingBudget(112, 18, 24, 160, 420)
    }
}
