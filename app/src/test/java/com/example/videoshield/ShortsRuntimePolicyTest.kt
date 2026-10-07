package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsRuntimePolicyTest {
    @Test fun healthyUnmeteredKeepsNextWarm() {
        val policy = ShortsRuntimePolicyResolver.resolve(
            online = true,
            metered = false,
            powerConstrained = false,
            lowRamDevice = false,
            memoryPressure = ShortsMemoryPressure.NORMAL,
            memoryHardening = true
        )
        assertEquals(ShortsPreloadMode.METADATA, policy.previousPreload)
        assertEquals(ShortsPreloadMode.AUTO, policy.nextPreload)
        assertFalse(policy.hardReleaseDistant)
    }

    @Test fun constrainedNetworkAvoidsFullNextPreload() {
        val policy = ShortsRuntimePolicyResolver.resolve(
            online = true,
            metered = true,
            powerConstrained = true,
            lowRamDevice = false,
            memoryPressure = ShortsMemoryPressure.NORMAL,
            memoryHardening = true
        )
        assertEquals(ShortsPreloadMode.METADATA, policy.nextPreload)
    }

    @Test fun lowMemoryHardReleasesDistantMedia() {
        val policy = ShortsRuntimePolicyResolver.resolve(
            online = true,
            metered = false,
            powerConstrained = false,
            lowRamDevice = false,
            memoryPressure = ShortsMemoryPressure.LOW,
            memoryHardening = true
        )
        assertTrue(policy.hardReleaseDistant)
        assertTrue(policy.trimImages)
        assertTrue(policy.retainedVideoPressure <= 6)
    }

    @Test fun offlineStopsNextPreload() {
        val policy = ShortsRuntimePolicyResolver.resolve(
            online = false,
            metered = false,
            powerConstrained = false,
            lowRamDevice = false,
            memoryPressure = ShortsMemoryPressure.NORMAL,
            memoryHardening = true
        )
        assertEquals(ShortsPreloadMode.NONE, policy.nextPreload)
    }
}
