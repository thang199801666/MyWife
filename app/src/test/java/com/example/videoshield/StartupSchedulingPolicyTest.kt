package com.example.videoshield

import org.junit.Assert.assertTrue
import org.junit.Test

class StartupSchedulingPolicyTest {
    @Test
    fun browseColdStartKeepsObserverAndEnrichmentOffFirstFrame() {
        val schedule = StartupSchedulingPolicy.resolve(playbackVisibleAtLaunch = false)
        assertTrue(schedule.localContentWarmupMs > 0L)
        assertTrue(schedule.localContentWarmupMs < schedule.runtimeObserversMs)
        assertTrue(schedule.contentEnrichmentMs > schedule.runtimeObserversMs)
        assertTrue(schedule.localMaintenanceMs > schedule.contentEnrichmentMs)
        assertTrue(schedule.ruleUpdateMs > schedule.localMaintenanceMs)
        assertTrue(schedule.appUpdateMs > schedule.ruleUpdateMs)
    }

    @Test
    fun playbackLaunchGetsMoreRendererHeadroomThanBrowseLaunch() {
        val browse = StartupSchedulingPolicy.resolve(playbackVisibleAtLaunch = false)
        val playback = StartupSchedulingPolicy.resolve(playbackVisibleAtLaunch = true)
        assertTrue(playback.localContentWarmupMs > browse.localContentWarmupMs)
        assertTrue(playback.runtimeObserversMs > browse.runtimeObserversMs)
        assertTrue(playback.contentEnrichmentMs > browse.contentEnrichmentMs)
        assertTrue(playback.ruleUpdateMs > browse.ruleUpdateMs)
        assertTrue(playback.appUpdateMs > browse.appUpdateMs)
    }
}
