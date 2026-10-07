package com.example.videoshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedCardReusePolicyTest {
    @Test fun cardReuseRequiresSameLiveIdentityTargetAndFreshState() {
        assertTrue(FeedCardReusePolicy.canReuseCard(true, true, true, 900))
        assertFalse(FeedCardReusePolicy.canReuseCard(false, true, true, 900))
        assertFalse(FeedCardReusePolicy.canReuseCard(true, false, true, 900))
        assertFalse(FeedCardReusePolicy.canReuseCard(true, true, false, 900))
        assertFalse(FeedCardReusePolicy.canReuseCard(true, true, true, FeedCardReusePolicy.CARD_RETUNE_TTL_MS))
    }

    @Test fun localityBucketsAreViewportScaledAndBounded() {
        assertEquals(720, FeedCardReusePolicy.localitySpanPx(1000))
        assertEquals(320, FeedCardReusePolicy.localitySpanPx(200))
        assertEquals(0, FeedCardReusePolicy.localityBucket(0, 1000))
        assertEquals(2, FeedCardReusePolicy.localityBucket(1500, 1000))
    }

    @Test fun localityWindowReuseRequiresStableBoundaryAndMidpointNodes() {
        assertTrue(FeedCardReusePolicy.canReuseWindow(true, true, true, true, true))
        assertFalse(FeedCardReusePolicy.canReuseWindow(true, true, true, false, true))
        assertFalse(FeedCardReusePolicy.canReuseWindow(false, true, true, true, true))
    }
}
