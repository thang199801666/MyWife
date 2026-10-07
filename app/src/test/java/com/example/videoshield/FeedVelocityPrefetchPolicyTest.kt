package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedVelocityPrefetchPolicyTest {
    @Test fun velocityBandsAndBudgetsShrinkAsScrollAccelerates() {
        val slow = FeedVelocityPrefetchPolicy.budget(1000, 300f)
        val medium = FeedVelocityPrefetchPolicy.budget(1000, 1200f)
        val fast = FeedVelocityPrefetchPolicy.budget(1000, 3000f)

        assertEquals(FeedVelocityBand.SLOW, slow.band)
        assertEquals(FeedVelocityBand.MEDIUM, medium.band)
        assertEquals(FeedVelocityBand.FAST, fast.band)
        assertTrue(slow.cardBudget > medium.cardBudget)
        assertTrue(medium.cardBudget > fast.cardBudget)
        assertTrue(slow.searchRequestBudget > medium.searchRequestBudget)
        assertTrue(medium.searchRequestBudget > fast.searchRequestBudget)
        assertTrue(slow.aheadPx > medium.aheadPx)
        assertTrue(medium.aheadPx > fast.aheadPx)
        assertTrue(slow.imageBudget > medium.imageBudget)
        assertTrue(medium.imageBudget > fast.imageBudget)
    }

    @Test fun memoryPressureShrinksSlowFeedAndSearchBudgets() {
        val normal = FeedVelocityPrefetchPolicy.budget(1000, 0f, memory = FeedMemoryBand.NORMAL)
        val moderate = FeedVelocityPrefetchPolicy.budget(1000, 0f, memory = FeedMemoryBand.MODERATE)
        val low = FeedVelocityPrefetchPolicy.budget(1000, 0f, memory = FeedMemoryBand.LOW)
        val critical = FeedVelocityPrefetchPolicy.budget(1000, 0f, memory = FeedMemoryBand.CRITICAL)

        assertTrue(normal.cardBudget > moderate.cardBudget)
        assertTrue(moderate.cardBudget > low.cardBudget)
        assertTrue(low.cardBudget > critical.cardBudget)
        assertTrue(normal.imageBudget > moderate.imageBudget)
        assertTrue(moderate.imageBudget > low.imageBudget)
        assertTrue(low.imageBudget > critical.imageBudget)
        assertTrue(normal.searchRequestBudget > low.searchRequestBudget)
        assertTrue(low.searchRequestBudget > critical.searchRequestBudget)
    }

    @Test fun flingSettlingOnlyRunsAfterMovingBands() {
        assertTrue(FeedVelocityPrefetchPolicy.shouldSettleFling(FeedVelocityBand.FAST))
        assertTrue(FeedVelocityPrefetchPolicy.shouldSettleFling(FeedVelocityBand.MEDIUM))
        assertEquals(false, FeedVelocityPrefetchPolicy.shouldSettleFling(FeedVelocityBand.SLOW))
    }

    @Test fun constrainedSlowModeUsesSmallerPrefetchWindow() {
        val normal = FeedVelocityPrefetchPolicy.budget(1000, 0f, constrained = false)
        val constrained = FeedVelocityPrefetchPolicy.budget(1000, 0f, constrained = true)
        assertTrue(constrained.aheadPx < normal.aheadPx)
        assertTrue(constrained.behindPx < normal.behindPx)
    }
}
