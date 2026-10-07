package com.example.videoshield

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryPressurePolicyTest {
    @Test fun mapsForegroundPressure() {
        assertEquals(MemoryPressureTier.MODERATE, MemoryPressurePolicy.fromTrimLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
        assertEquals(MemoryPressureTier.LOW, MemoryPressurePolicy.fromTrimLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
        assertEquals(MemoryPressureTier.CRITICAL, MemoryPressurePolicy.fromTrimLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
    }

    @Test fun mapsBackgroundPressure() {
        assertEquals(MemoryPressureTier.MODERATE, MemoryPressurePolicy.fromTrimLevel(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertEquals(MemoryPressureTier.LOW, MemoryPressurePolicy.fromTrimLevel(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertEquals(MemoryPressureTier.LOW, MemoryPressurePolicy.fromTrimLevel(ComponentCallbacks2.TRIM_MEMORY_MODERATE))
        assertEquals(MemoryPressureTier.CRITICAL, MemoryPressurePolicy.fromTrimLevel(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
    }
}
