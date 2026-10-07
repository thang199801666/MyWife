package com.example.videoshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPreviewScriptTest {
    @Test fun searchThumbnailRepairUsesViewportDrivenBoundedDiscovery() {
        val script = SearchPreviewScript.build()
        assertTrue(script.contains("IntersectionObserver"))
        assertTrue(script.contains("requestIdleCallback"))
        assertTrue(script.contains("classifyVelocity"))
        assertTrue(script.contains("requestBudget"))
        assertTrue(script.contains("deferredRepair"))
        assertTrue(script.contains("__votuibeSetSearchMemoryPressure"))
        assertTrue(script.contains("pressureTransitions"))
        assertTrue(script.contains("pressureDrops"))
        assertTrue(script.contains("targetCache=new WeakMap()"))
        assertTrue(script.contains("cacheHits"))
        assertTrue(script.contains("cacheMisses"))
        assertTrue(script.contains("repairSkips"))
        assertTrue(script.contains("localityHits"))
        assertTrue(script.contains("layoutReadsSaved"))
        assertTrue(script.contains("entry.boundingClientRect"))
        assertTrue(script.contains("velocityBand==='slow'?'auto':'low'"))
        assertTrue(script.contains("ageObserver"))
        assertTrue(script.contains("observerAgingBudget"))
        assertTrue(script.contains("observed=new Set()"))
        assertTrue(script.contains("observer.unobserve(link)"))
        assertTrue(script.contains("observerPrunes"))
        assertTrue(script.contains("stateCompactions"))
        assertTrue(script.contains("observedTargets:observed.size"))
        assertTrue(script.contains("windowStart"))
        assertTrue(script.contains("currentRouteKey"))
        assertTrue(script.contains("link.isConnected"))
        assertTrue(script.contains("visible?.disconnect()"))
        assertTrue(script.contains("interactionQuietMs=180"))
        assertTrue(script.contains("interactionBusy()"))
        assertTrue(script.contains("touchstart"))
        assertFalse(script.contains("MutationObserver"))
        assertFalse(script.contains("setInterval("))
    }
}
