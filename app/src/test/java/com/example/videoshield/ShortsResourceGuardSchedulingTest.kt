package com.example.videoshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsResourceGuardSchedulingTest {
    @Test fun transitionTrimIsFrameCoalescedAndHardTrimIsIdleDeferred() {
        val script = ShortsResourceGuardScript.install()
        assertTrue(script.contains("requestAnimationFrame"))
        assertTrue(script.contains("requestIdleCallback"))
        assertTrue(script.contains("__votuibeHardTrimShorts"))
        assertFalse(script.contains("setInterval("))
        assertTrue(script.contains("playbackRate=2"))
        assertTrue(script.contains("2\\u00d7 locked"))
        assertTrue(script.contains("dy>52"))
        assertTrue(script.contains("autoHideChrome=false"))
        assertTrue(script.contains("data-votuibe-shorts-chrome-hidden"))
        assertTrue(script.contains("shortsChromeHidden"))
        assertTrue(script.contains("const currentActiveVideo=()"))
        assertTrue(script.contains("activeVideo && activeVideo.isConnected!==false"))
        assertTrue(script.contains("trim(false,false);"))
        assertTrue(script.contains("transition() deliberately reveals"))
        assertTrue(script.contains("passive:true"))
        assertTrue(script.contains("attachLockMove"))
        assertTrue(script.contains("passive:false"))
    }

    @Test fun periodicHardTrimCallUsesDeferredEntryPoint() {
        assertTrue(ShortsResourceGuardScript.hardTrimDeferred().contains("__votuibeHardTrimShorts"))
        assertFalse(ShortsResourceGuardScript.hardTrimDeferred().contains("__votuibeTrimShorts"))
    }
}
