package com.example.videoshield

/**
 * One-shot startup staging. Delays are measured from the first rendered app frame, not from
 * Activity.onCreate(), so background maintenance cannot compete with inflation/WebView bootstrap.
 *
 * Initial network/device state is read synchronously before this schedule starts; observer
 * registration can therefore wait a fraction of a frame budget without changing initial UI state.
 */
data class StartupSchedule(
    val localContentWarmupMs: Long,
    val runtimeObserversMs: Long,
    val contentEnrichmentMs: Long,
    val diagnosticsMs: Long,
    val notificationPermissionMs: Long,
    val localMaintenanceMs: Long,
    val ruleUpdateMs: Long,
    val appUpdateMs: Long
)

object StartupSchedulingPolicy {
    fun resolve(playbackVisibleAtLaunch: Boolean): StartupSchedule = if (playbackVisibleAtLaunch) {
        // A restored/deep-linked player owns the critical renderer/media pipeline. Keep everything
        // else behind it, especially disk maintenance and update checks.
        StartupSchedule(
            localContentWarmupMs = 720L,
            runtimeObserversMs = 120L,
            contentEnrichmentMs = 600L,
            diagnosticsMs = 850L,
            notificationPermissionMs = 1_900L,
            localMaintenanceMs = 1_750L,
            ruleUpdateMs = 3_500L,
            appUpdateMs = 5_000L
        )
    } else {
        // Browse-only cold start: Home gets the first renderer turn. Observer registration starts
        // shortly after first frame; enrichment and maintenance remain progressively staged.
        StartupSchedule(
            localContentWarmupMs = 48L,
            runtimeObserversMs = 80L,
            contentEnrichmentMs = 240L,
            diagnosticsMs = 450L,
            notificationPermissionMs = 1_300L,
            localMaintenanceMs = 1_000L,
            ruleUpdateMs = 2_400L,
            appUpdateMs = 3_700L
        )
    }
}
