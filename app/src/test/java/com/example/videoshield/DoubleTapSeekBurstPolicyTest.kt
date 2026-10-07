package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapSeekBurstPolicyTest {
    @Test
    fun repeatedSameSideTapsAccumulate() {
        val first = DoubleTapSeekBurstPolicy.start(90_000L, 1, 10, 1_000L)
        assertTrue(DoubleTapSeekBurstPolicy.canContinue(first, 1, 1_200L))
        val second = DoubleTapSeekBurstPolicy.extend(first, 10, 1_200L)
        val third = DoubleTapSeekBurstPolicy.extend(second, 10, 1_400L)
        assertEquals(30_000L, third.accumulatedMs)
        assertEquals(120_000L, DoubleTapSeekBurstPolicy.targetPositionMs(third, 300_000L))
    }

    @Test
    fun sideSwitchAndExpiredTapDoNotContinueBurst() {
        val first = DoubleTapSeekBurstPolicy.start(90_000L, -1, 10, 1_000L)
        assertFalse(DoubleTapSeekBurstPolicy.canContinue(first, 1, 1_200L))
        assertFalse(DoubleTapSeekBurstPolicy.canContinue(first, -1, 1_700L))
    }

    @Test
    fun targetClampsAtVideoBounds() {
        val back = DoubleTapSeekBurstPolicy.start(4_000L, -1, 10, 1_000L)
        val forward = DoubleTapSeekBurstPolicy.start(298_000L, 1, 10, 1_000L)
        assertEquals(0L, DoubleTapSeekBurstPolicy.targetPositionMs(back, 300_000L))
        assertEquals(299_750L, DoubleTapSeekBurstPolicy.targetPositionMs(forward, 300_000L))
    }

    @Test
    fun longBurstIsBounded() {
        var state = DoubleTapSeekBurstPolicy.start(300_000L, 1, 30, 1_000L)
        repeat(10_000) { index ->
            state = DoubleTapSeekBurstPolicy.extend(state, 30, 1_100L + index)
        }
        assertEquals(DoubleTapSeekBurstPolicy.MAX_ACCUMULATED_MS, state.accumulatedMs)
    }
}
