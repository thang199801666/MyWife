package com.example.videoshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseFirstPaintScriptTest {
    @Test
    fun firstPaintPriorityIsBoundedAndEventDriven() {
        val script = BrowseFirstPaintScript.install()
        assertTrue(script.contains("slice(0, 36)"))
        assertTrue(script.contains("promoted < 2"))
        assertTrue(script.contains("fetchPriority = 'high'"))
        assertTrue(script.contains("fetchPriority = near ? 'auto' : 'low'"))
        assertTrue(script.contains("requestAnimationFrame"))
        assertTrue(script.contains("yt-navigate-finish"))
        assertFalse(script.contains("setInterval("))
        assertFalse(script.contains("MutationObserver"))
    }
}
