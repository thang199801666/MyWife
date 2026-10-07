package com.example.videoshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongSessionResourcePolicyTest {
    @Test fun playerCacheTrimIsSparse() {
        val policy = LongSessionResourcePolicy(playerCacheTrimEvery = 3, browseCacheTrimEvery = 4)
        assertFalse(policy.onPlayerVideoChanged().trimPolicyScriptCaches)
        assertFalse(policy.onPlayerVideoChanged().trimPolicyScriptCaches)
        assertTrue(policy.onPlayerVideoChanged().trimPolicyScriptCaches)
        assertFalse(policy.onPlayerVideoChanged().trimPolicyScriptCaches)
    }

    @Test fun browseCadenceIsIndependentFromPlayerCadence() {
        val policy = LongSessionResourcePolicy(playerCacheTrimEvery = 2, browseCacheTrimEvery = 3)
        assertFalse(policy.onBrowseDestinationChanged().trimPolicyScriptCaches)
        assertFalse(policy.onPlayerVideoChanged().trimPolicyScriptCaches)
        assertFalse(policy.onBrowseDestinationChanged().trimPolicyScriptCaches)
        assertTrue(policy.onBrowseDestinationChanged().trimPolicyScriptCaches)
    }
}
