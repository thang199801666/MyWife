package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedInteractionBudgetPolicyTest {
    @Test fun yieldsOnlyInsideQuietWindow() {
        assertEquals(220L, FeedInteractionBudgetPolicy.remainingQuietMs(1_000L, 1_000L, 220L))
        assertEquals(80L, FeedInteractionBudgetPolicy.remainingQuietMs(1_140L, 1_000L, 220L))
        assertEquals(0L, FeedInteractionBudgetPolicy.remainingQuietMs(1_221L, 1_000L, 220L))
    }

    @Test fun zeroInteractionNeverBlocks() {
        assertEquals(0L, FeedInteractionBudgetPolicy.remainingQuietMs(500L, 0L, 320L))
    }
}
