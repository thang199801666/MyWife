package com.example.videoshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedObserverAgingPolicyTest {
    @Test fun pressureShrinksBrowseRetentionEnvelope() {
        val normal = FeedObserverAgingPolicy.budget(
            FeedObserverSurface.BROWSE, MemoryPressureTier.NORMAL, constrained = false
        )
        val low = FeedObserverAgingPolicy.budget(
            FeedObserverSurface.BROWSE, MemoryPressureTier.LOW, constrained = false
        )
        val critical = FeedObserverAgingPolicy.budget(
            FeedObserverSurface.BROWSE, MemoryPressureTier.CRITICAL, constrained = false
        )
        assertTrue(normal.retentionEnvelope > low.retentionEnvelope)
        assertTrue(low.retentionEnvelope > critical.retentionEnvelope)
        assertTrue(normal.hardCap >= normal.retentionEnvelope)
        assertTrue(low.hardCap >= low.retentionEnvelope)
        assertTrue(critical.hardCap >= critical.retentionEnvelope)
    }

    @Test fun constrainedBrowseAndSearchStayBounded() {
        val constrained = FeedObserverAgingPolicy.budget(
            FeedObserverSurface.BROWSE, MemoryPressureTier.NORMAL, constrained = true
        )
        val search = FeedObserverAgingPolicy.budget(
            FeedObserverSurface.SEARCH, MemoryPressureTier.NORMAL
        )
        assertTrue(constrained.hardCap <= 144)
        assertTrue(search.hardCap <= 160)
        assertTrue(constrained.retentionEnvelope <= constrained.hardCap)
        assertTrue(search.retentionEnvelope <= search.hardCap)
    }

    @Test fun stateResetRequiresEnoughAgedTargets() {
        val budget = FeedObserverAgingPolicy.budget(
            FeedObserverSurface.SEARCH, MemoryPressureTier.LOW
        )
        assertFalse(FeedObserverAgingPolicy.shouldResetState(budget.stateResetPruneCount - 1, budget))
        assertTrue(FeedObserverAgingPolicy.shouldResetState(budget.stateResetPruneCount, budget))
    }
}
