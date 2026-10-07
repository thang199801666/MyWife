package com.example.videoshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererCrashLoopPolicyTest {
    @Test fun memoryReclaimsNeedLargerBurstThanRealCrashes() {
        assertFalse(RendererCrashLoopPolicy.guardActive(crashes = 0, terminations = 4))
        assertTrue(RendererCrashLoopPolicy.guardActive(crashes = 0, terminations = 5))
        assertFalse(RendererCrashLoopPolicy.guardActive(crashes = 2, terminations = 1))
        assertTrue(RendererCrashLoopPolicy.guardActive(crashes = 3, terminations = 0))
    }

    @Test fun mixedFailuresUseWeightedEvidence() {
        assertTrue(RendererCrashLoopPolicy.guardActive(crashes = 2, terminations = 2))
        assertFalse(RendererCrashLoopPolicy.guardActive(crashes = 1, terminations = 3))
    }

    @Test fun clockRollbackExpiresWindowAndCountsAsStableBoundary() {
        assertTrue(RendererCrashLoopPolicy.windowExpired(20_000L, 10_000L, 600_000L))
        assertTrue(RendererCrashLoopPolicy.stableWindowReached(20_000L, 10_000L, 900_000L))
    }
}
