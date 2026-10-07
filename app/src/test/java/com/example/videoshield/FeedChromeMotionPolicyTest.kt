package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedChromeMotionPolicyTest {
    @Test fun hidesAfterMeaningfulDownwardTravel() {
        val policy = FeedChromeMotionPolicy(thresholdPx = 28, topRevealPx = 8, deadbandPx = 2)
        assertEquals(FeedChromeMotionPolicy.Action.NONE, policy.onScroll(40, 20, false))
        assertEquals(FeedChromeMotionPolicy.Action.HIDE, policy.onScroll(55, 40, false))
    }

    @Test fun revealsImmediatelyAtTopOrWhenLocked() {
        val policy = FeedChromeMotionPolicy(28, 8, 2)
        policy.onScroll(80, 40, false)
        assertEquals(FeedChromeMotionPolicy.Action.SHOW, policy.onScroll(5, 80, false))
        assertEquals(FeedChromeMotionPolicy.Action.SHOW, policy.onScroll(200, 180, true))
    }

    @Test fun directionChangeRequiresFreshThreshold() {
        val policy = FeedChromeMotionPolicy(28, 8, 2)
        assertEquals(FeedChromeMotionPolicy.Action.NONE, policy.onScroll(70, 50, false))
        assertEquals(FeedChromeMotionPolicy.Action.NONE, policy.onScroll(60, 70, false))
        assertEquals(FeedChromeMotionPolicy.Action.SHOW, policy.onScroll(38, 60, false))
    }
}
