package com.example.videoshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class RendererLoadPolicyTest {
    private fun context(
        foreground: Boolean = true,
        expanded: Boolean = true,
        transitioning: Boolean = false,
        pip: Boolean = false,
        obscured: Boolean = true,
        constrained: Boolean = false,
        pressure: MemoryPressureTier = MemoryPressureTier.NORMAL
    ) = RendererLoadContext(foreground, expanded, transitioning, pip, obscured, constrained, pressure)

    @Test fun expandedPlayerDefersBrowseButAllowsPlayerImages() {
        val d = RendererLoadPolicy.resolve(context())
        assertTrue(d.loadPlayerImages)
        assertFalse(d.loadBrowseImages)
        assertEquals(160L, d.playerResumeDelayMs)
    }

    @Test fun transitionNeverLoadsPlayerImages() {
        val d = RendererLoadPolicy.resolve(context(transitioning = true))
        assertFalse(d.loadPlayerImages)
    }

    @Test fun browseSurfaceLoadsOnlyBrowseImages() {
        val d = RendererLoadPolicy.resolve(context(expanded = false, obscured = false))
        assertFalse(d.loadPlayerImages)
        assertTrue(d.loadBrowseImages)
    }

    @Test fun constrainedRuntimeUsesLongerResumeDelay() {
        val d = RendererLoadPolicy.resolve(context(constrained = true))
        assertEquals(280L, d.playerResumeDelayMs)
        assertEquals(180L, d.browseResumeDelayMs)
    }
}
