package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewSessionCompactionPolicyTest {
    @Test fun firstCompactionAllowsNormalHistoryButBoundsLongSessions() {
        assertFalse(WebViewSessionCompactionPolicy.shouldCompactBrowse(24, false, true, false))
        assertTrue(WebViewSessionCompactionPolicy.shouldCompactBrowse(25, false, true, false))
        assertFalse(WebViewSessionCompactionPolicy.shouldCompactBrowse(40, false, true, true))
    }

    @Test fun postCompactionKeepsChromiumListSmall() {
        assertFalse(WebViewSessionCompactionPolicy.shouldCompactBrowse(8, true, true, false))
        assertTrue(WebViewSessionCompactionPolicy.shouldCompactBrowse(9, true, true, false))
        assertFalse(WebViewSessionCompactionPolicy.shouldCompactBrowse(99, true, false, false))
    }

    @Test fun pressureTightensLiveHistoryWithoutTouchingUnstableOrShortsRoutes() {
        assertFalse(WebViewSessionCompactionPolicy.shouldCompactBrowse(16, false, true, false, MemoryPressureTier.MODERATE))
        assertTrue(WebViewSessionCompactionPolicy.shouldCompactBrowse(17, false, true, false, MemoryPressureTier.MODERATE))
        assertTrue(WebViewSessionCompactionPolicy.shouldCompactBrowse(11, false, true, false, MemoryPressureTier.LOW))
        assertTrue(WebViewSessionCompactionPolicy.shouldCompactBrowse(5, false, true, false, MemoryPressureTier.CRITICAL))
        assertFalse(WebViewSessionCompactionPolicy.shouldCompactBrowse(50, false, false, false, MemoryPressureTier.CRITICAL))
        assertFalse(WebViewSessionCompactionPolicy.shouldCompactBrowse(50, false, true, true, MemoryPressureTier.CRITICAL))
    }

    @Test fun richStateSerializationShrinksAsHeapPressureRises() {
        assertTrue(WebViewSessionCompactionPolicy.shouldSerializeBrowseState(12, false, false, false))
        assertFalse(WebViewSessionCompactionPolicy.shouldSerializeBrowseState(13, false, false, false))
        assertTrue(WebViewSessionCompactionPolicy.shouldSerializeBrowseState(8, false, false, false, MemoryPressureTier.MODERATE))
        assertFalse(WebViewSessionCompactionPolicy.shouldSerializeBrowseState(9, false, false, false, MemoryPressureTier.MODERATE))
        assertTrue(WebViewSessionCompactionPolicy.shouldSerializeBrowseState(4, false, false, false, MemoryPressureTier.LOW))
        assertFalse(WebViewSessionCompactionPolicy.shouldSerializeBrowseState(1, false, false, false, MemoryPressureTier.CRITICAL))
        assertFalse(WebViewSessionCompactionPolicy.shouldSerializeBrowseState(4, true, false, false))
        assertFalse(WebViewSessionCompactionPolicy.shouldSerializeBrowseState(4, false, true, false))
        assertFalse(WebViewSessionCompactionPolicy.shouldSerializeBrowseState(4, false, false, true))

        assertTrue(WebViewSessionCompactionPolicy.shouldSerializePlayerState(6, false))
        assertFalse(WebViewSessionCompactionPolicy.shouldSerializePlayerState(7, false))
        assertTrue(WebViewSessionCompactionPolicy.shouldSerializePlayerState(2, false, MemoryPressureTier.LOW))
        assertFalse(WebViewSessionCompactionPolicy.shouldSerializePlayerState(3, false, MemoryPressureTier.LOW))
        assertFalse(WebViewSessionCompactionPolicy.shouldSerializePlayerState(1, false, MemoryPressureTier.CRITICAL))
        assertFalse(WebViewSessionCompactionPolicy.shouldSerializePlayerState(2, true))
    }

    @Test fun pressureCompactionDelayAndScrollSnapshotsAreBounded() {
        assertEquals(8, WebViewSessionCompactionPolicy.scrollSnapshotLimit(MemoryPressureTier.NORMAL))
        assertEquals(6, WebViewSessionCompactionPolicy.scrollSnapshotLimit(MemoryPressureTier.MODERATE))
        assertEquals(4, WebViewSessionCompactionPolicy.scrollSnapshotLimit(MemoryPressureTier.LOW))
        assertEquals(2, WebViewSessionCompactionPolicy.scrollSnapshotLimit(MemoryPressureTier.CRITICAL))
        assertEquals(0L, WebViewSessionCompactionPolicy.compactionDelayMs(MemoryPressureTier.CRITICAL, true, false))
        assertTrue(WebViewSessionCompactionPolicy.compactionDelayMs(MemoryPressureTier.LOW, false, false) <
            WebViewSessionCompactionPolicy.compactionDelayMs(MemoryPressureTier.MODERATE, true, false))
        assertTrue(WebViewSessionCompactionPolicy.shouldCompactPlayerHistory(2, false, MemoryPressureTier.MODERATE))
        assertFalse(WebViewSessionCompactionPolicy.shouldCompactPlayerHistory(1, false, MemoryPressureTier.CRITICAL))
    }
}
