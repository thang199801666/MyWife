package com.example.videoshield

import android.content.ComponentCallbacks2

/**
 * Normalized memory-pressure tiers shared by WebView, media and cache hardening.
 * Android trim constants mix foreground and background severities; centralizing the
 * mapping keeps individual components from interpreting those callbacks differently.
 */
enum class MemoryPressureTier {
    NORMAL,
    MODERATE,
    LOW,
    CRITICAL;

    fun atLeast(other: MemoryPressureTier): Boolean = ordinal >= other.ordinal
}

object MemoryPressurePolicy {
    fun fromTrimLevel(level: Int): MemoryPressureTier = when {
        level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> MemoryPressureTier.CRITICAL
        level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> MemoryPressureTier.LOW
        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> MemoryPressureTier.LOW
        level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> MemoryPressureTier.MODERATE
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> MemoryPressureTier.CRITICAL
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> MemoryPressureTier.LOW
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> MemoryPressureTier.MODERATE
        else -> MemoryPressureTier.NORMAL
    }
}
