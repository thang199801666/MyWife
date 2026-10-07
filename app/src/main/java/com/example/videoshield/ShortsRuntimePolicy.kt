package com.example.videoshield

/** Native-side resource policy for the long-running Shorts feed. */
enum class ShortsMemoryPressure { NORMAL, MODERATE, LOW, CRITICAL }

enum class ShortsPreloadMode(val webValue: String) {
    NONE("none"),
    METADATA("metadata"),
    AUTO("auto")
}

data class ShortsRuntimePolicy(
    val previousPreload: ShortsPreloadMode,
    val nextPreload: ShortsPreloadMode,
    val hardReleaseDistant: Boolean,
    val trimImages: Boolean,
    val retainedVideoPressure: Int
)

object ShortsRuntimePolicyResolver {
    fun resolve(
        online: Boolean,
        metered: Boolean,
        powerConstrained: Boolean,
        lowRamDevice: Boolean,
        memoryPressure: ShortsMemoryPressure,
        memoryHardening: Boolean
    ): ShortsRuntimePolicy {
        if (!memoryHardening) {
            return ShortsRuntimePolicy(
                previousPreload = ShortsPreloadMode.METADATA,
                nextPreload = when {
                    !online -> ShortsPreloadMode.NONE
                    !metered && !powerConstrained -> ShortsPreloadMode.AUTO
                    else -> ShortsPreloadMode.METADATA
                },
                hardReleaseDistant = false,
                trimImages = false,
                retainedVideoPressure = 12
            )
        }

        val lowOrWorse = memoryPressure.ordinal >= ShortsMemoryPressure.LOW.ordinal
        val critical = memoryPressure == ShortsMemoryPressure.CRITICAL
        val constrained = metered || powerConstrained || lowRamDevice ||
            memoryPressure.ordinal >= ShortsMemoryPressure.MODERATE.ordinal

        return ShortsRuntimePolicy(
            // Previous/current/next remain the only warm window. Under critical pressure the
            // previous item is still retained in the recycler, but it carries no preload.
            previousPreload = if (critical) ShortsPreloadMode.NONE else ShortsPreloadMode.METADATA,
            nextPreload = when {
                !online -> ShortsPreloadMode.NONE
                constrained -> ShortsPreloadMode.METADATA
                else -> ShortsPreloadMode.AUTO
            },
            hardReleaseDistant = lowOrWorse,
            trimImages = lowOrWorse,
            retainedVideoPressure = when (memoryPressure) {
                ShortsMemoryPressure.NORMAL -> if (lowRamDevice) 7 else 10
                ShortsMemoryPressure.MODERATE -> 8
                ShortsMemoryPressure.LOW -> 6
                ShortsMemoryPressure.CRITICAL -> 4
            }
        )
    }
}
