package com.example.videoshield

import android.content.Context
import java.net.URI
import java.text.DateFormat
import java.util.Date

/** Local-only runtime/device diagnostics. No browsing history is stored here. */
class RuntimeDiagnosticsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("videoshield_runtime_diagnostics", Context.MODE_PRIVATE)

    fun recordTrimMemory(level: Int) {
        editEvent("Trim memory level $level")
            .putLong(KEY_TRIMS, trims() + 1L)
            .putInt(KEY_LAST_TRIM_LEVEL, level)
            .apply()
    }

    fun recordLowMemory() {
        editEvent("Low-memory callback")
            .putLong(KEY_LOW_MEMORY, lowMemoryCallbacks() + 1L)
            .apply()
    }

    fun recordForeground() {
        editEvent("Activity foreground")
            .putLong(KEY_FOREGROUND, foregroundTransitions() + 1L)
            .apply()
    }

    fun recordBackground() {
        editEvent("Activity background")
            .putLong(KEY_BACKGROUND, backgroundTransitions() + 1L)
            .apply()
    }

    fun recordScreen(interactive: Boolean, powerSave: Boolean, idle: Boolean) {
        val key = if (interactive) KEY_SCREEN_ON else KEY_SCREEN_OFF
        val current = if (interactive) screenOnEvents() else screenOffEvents()
        editEvent("Screen ${if (interactive) "on" else "off"} • saver $powerSave • idle $idle")
            .putLong(key, current + 1L)
            .putBoolean(KEY_LAST_POWER_SAVE, powerSave)
            .putBoolean(KEY_LAST_IDLE, idle)
            .apply()
    }

    fun recordPowerState(powerSave: Boolean, idle: Boolean, reason: String) {
        editEvent("Power state: saver $powerSave • idle $idle • ${reason.take(80)}")
            .putBoolean(KEY_LAST_POWER_SAVE, powerSave)
            .putBoolean(KEY_LAST_IDLE, idle)
            .apply()
    }

    fun recordWebViewLifecycle(paused: Boolean, reason: String) {
        val key = if (paused) KEY_WEBVIEW_PAUSES else KEY_WEBVIEW_RESUMES
        val current = if (paused) webViewPauses() else webViewResumes()
        editEvent("WebView ${if (paused) "paused" else "resumed"}: $reason")
            .putLong(key, current + 1L)
            .apply()
    }

    fun recordWakeLock(held: Boolean, reason: String) {
        val key = if (held) KEY_WAKE_ACQUIRES else KEY_WAKE_RELEASES
        val current = if (held) wakeAcquires() else wakeReleases()
        editEvent("Playback wake lock ${if (held) "held" else "released"}: $reason")
            .putLong(key, current + 1L)
            .putBoolean(KEY_WAKE_HELD_LAST, held)
            .apply()
    }

    fun recordRestore(source: String, success: Boolean) {
        editEvent("Restore ${if (success) "OK" else "failed"}: ${source.take(80)}")
            .putLong(KEY_RESTORE_ATTEMPTS, restoreAttempts() + 1L)
            .putLong(KEY_RESTORE_SUCCESSES, restoreSuccesses() + if (success) 1L else 0L)
            .apply()
    }

    fun recordLibraryCheck(summary: String, healthy: Boolean) {
        editEvent("Library check ${if (healthy) "OK" else "FAIL"}")
            .putString(KEY_LIBRARY_CHECK, summary.take(600))
            .putBoolean(KEY_LIBRARY_HEALTHY, healthy)
            .putLong(KEY_LIBRARY_CHECK_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordServiceTaskRemoved() {
        editEvent("Playback task removed")
            .putLong(KEY_TASK_REMOVED, taskRemovedEvents() + 1L)
            .apply()
    }

    fun recordStaleServiceStop() {
        editEvent("Stopped stale playback service")
            .putLong(KEY_STALE_SERVICE_STOPS, staleServiceStops() + 1L)
            .apply()
    }

    fun recordBlockedNavigation(target: String, reason: String) {
        editEvent("Blocked navigation ${redactTarget(target)}: ${reason.take(100)}")
            .putLong(KEY_BLOCKED_NAVIGATIONS, blockedNavigations() + 1L)
            .apply()
    }

    fun recordSelfTest(summary: String, passed: Boolean) {
        editEvent("Runtime self-test ${if (passed) "PASS" else "FAIL"}")
            .putString(KEY_SELF_TEST, summary.take(1200))
            .putBoolean(KEY_SELF_TEST_PASSED, passed)
            .putLong(KEY_SELF_TEST_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordDeviceProfile(summary: String) {
        editEvent("Device policy detected")
            .putString(KEY_DEVICE_PROFILE, summary.take(600))
            .putLong(KEY_DEVICE_PROFILE_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordStressTest(summary: String, passed: Boolean) {
        editEvent("Release stress test ${if (passed) "PASS" else "FAIL"}")
            .putString(KEY_STRESS_TEST, summary.take(1400))
            .putBoolean(KEY_STRESS_TEST_PASSED, passed)
            .putLong(KEY_STRESS_TEST_AT, System.currentTimeMillis())
            .apply()
    }

    fun trims(): Long = prefs.getLong(KEY_TRIMS, 0L)
    fun lowMemoryCallbacks(): Long = prefs.getLong(KEY_LOW_MEMORY, 0L)
    fun foregroundTransitions(): Long = prefs.getLong(KEY_FOREGROUND, 0L)
    fun backgroundTransitions(): Long = prefs.getLong(KEY_BACKGROUND, 0L)
    fun screenOnEvents(): Long = prefs.getLong(KEY_SCREEN_ON, 0L)
    fun screenOffEvents(): Long = prefs.getLong(KEY_SCREEN_OFF, 0L)
    fun webViewPauses(): Long = prefs.getLong(KEY_WEBVIEW_PAUSES, 0L)
    fun webViewResumes(): Long = prefs.getLong(KEY_WEBVIEW_RESUMES, 0L)
    fun wakeAcquires(): Long = prefs.getLong(KEY_WAKE_ACQUIRES, 0L)
    fun wakeReleases(): Long = prefs.getLong(KEY_WAKE_RELEASES, 0L)
    fun restoreAttempts(): Long = prefs.getLong(KEY_RESTORE_ATTEMPTS, 0L)
    fun restoreSuccesses(): Long = prefs.getLong(KEY_RESTORE_SUCCESSES, 0L)
    fun taskRemovedEvents(): Long = prefs.getLong(KEY_TASK_REMOVED, 0L)
    fun staleServiceStops(): Long = prefs.getLong(KEY_STALE_SERVICE_STOPS, 0L)
    fun blockedNavigations(): Long = prefs.getLong(KEY_BLOCKED_NAVIGATIONS, 0L)

    fun summary(): String = buildString {
        append(appContext.getString(R.string.diag_lifecycle, foregroundTransitions(), backgroundTransitions()))
        append("\n").append(appContext.getString(R.string.diag_memory, trims(), lowMemoryCallbacks()))
        val lastTrim = prefs.getInt(KEY_LAST_TRIM_LEVEL, -1)
        if (lastTrim >= 0) append(appContext.getString(R.string.diag_last_level, lastTrim))
        append("\n").append(appContext.getString(R.string.diag_screen, screenOffEvents(), screenOnEvents(),
            yesNo(prefs.getBoolean(KEY_LAST_POWER_SAVE, false)), yesNo(prefs.getBoolean(KEY_LAST_IDLE, false))))
        append("\n").append(appContext.getString(R.string.diag_webview_lifecycle, webViewPauses(), webViewResumes()))
        append("\n").append(appContext.getString(R.string.diag_wake_lock, wakeAcquires(), wakeReleases()))
        append("\n").append(appContext.getString(R.string.diag_restore, restoreSuccesses(), restoreAttempts(), taskRemovedEvents(), staleServiceStops()))
        append("\n").append(appContext.getString(R.string.diag_navigation_safety, blockedNavigations()))
        val device = prefs.getString(KEY_DEVICE_PROFILE, "").orEmpty()
        if (device.isNotBlank()) append("\n").append(appContext.getString(R.string.diag_device_policy, LocalizedPresentation.diagnosticDetail(appContext, device)))
        val libraryCheck = prefs.getString(KEY_LIBRARY_CHECK, "").orEmpty()
        if (libraryCheck.isNotBlank()) {
            val status = appContext.getString(if (prefs.getBoolean(KEY_LIBRARY_HEALTHY, false)) R.string.diag_pass else R.string.diag_fail)
            append("\n").append(appContext.getString(R.string.diag_library, status, LocalizedPresentation.diagnosticDetail(appContext, libraryCheck)))
            val at = prefs.getLong(KEY_LIBRARY_CHECK_AT, 0L)
            if (at > 0L) append(" • ${formatAt(at)}")
        }
        val selfTest = prefs.getString(KEY_SELF_TEST, "").orEmpty()
        if (selfTest.isNotBlank()) {
            val status = appContext.getString(if (prefs.getBoolean(KEY_SELF_TEST_PASSED, false)) R.string.diag_pass else R.string.diag_fail)
            append("\n").append(appContext.getString(R.string.diag_self_test, status)).append(": ").append(LocalizedPresentation.diagnosticDetail(appContext, selfTest))
            val selfAt = prefs.getLong(KEY_SELF_TEST_AT, 0L)
            if (selfAt > 0L) append(" • ${formatAt(selfAt)}")
        }
        val stress = prefs.getString(KEY_STRESS_TEST, "").orEmpty()
        if (stress.isNotBlank()) {
            val status = appContext.getString(if (prefs.getBoolean(KEY_STRESS_TEST_PASSED, false)) R.string.diag_pass else R.string.diag_fail)
            append("\n").append(appContext.getString(R.string.diag_release_stress, status, LocalizedPresentation.diagnosticDetail(appContext, stress)))
            val stressAt = prefs.getLong(KEY_STRESS_TEST_AT, 0L)
            if (stressAt > 0L) append(" • ${formatAt(stressAt)}")
        }
        val event = prefs.getString(KEY_LAST_EVENT, "").orEmpty()
        val at = prefs.getLong(KEY_LAST_EVENT_AT, 0L)
        if (event.isNotBlank()) {
            append("\n").append(appContext.getString(R.string.diag_last_event, LocalizedPresentation.diagnosticDetail(appContext, event)))
            if (at > 0L) append(" • ${formatAt(at)}")
        }
    }

    fun reset() = prefs.edit().clear().apply()

    private fun editEvent(event: String) = prefs.edit()
        .putString(KEY_LAST_EVENT, event.take(240))
        .putLong(KEY_LAST_EVENT_AT, System.currentTimeMillis())

    private fun redactTarget(target: String): String = try {
        val uri = URI(target.trim())
        val scheme = uri.scheme?.lowercase().orEmpty().ifBlank { "unknown" }
        val host = uri.host?.lowercase().orEmpty()
        if (host.isBlank()) scheme else "$scheme://$host"
    } catch (_: Exception) {
        "malformed"
    }

    private fun yesNo(value: Boolean) = appContext.getString(if (value) R.string.diag_yes else R.string.diag_no)
    private fun formatAt(value: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))

    companion object {
        private const val KEY_TRIMS = "trims"
        private const val KEY_LAST_TRIM_LEVEL = "last_trim_level"
        private const val KEY_LOW_MEMORY = "low_memory"
        private const val KEY_FOREGROUND = "foreground"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_SCREEN_ON = "screen_on"
        private const val KEY_SCREEN_OFF = "screen_off"
        private const val KEY_LAST_POWER_SAVE = "last_power_save"
        private const val KEY_LAST_IDLE = "last_idle"
        private const val KEY_WEBVIEW_PAUSES = "webview_pauses"
        private const val KEY_WEBVIEW_RESUMES = "webview_resumes"
        private const val KEY_WAKE_ACQUIRES = "wake_acquires"
        private const val KEY_WAKE_RELEASES = "wake_releases"
        private const val KEY_WAKE_HELD_LAST = "wake_held_last"
        private const val KEY_RESTORE_ATTEMPTS = "restore_attempts"
        private const val KEY_RESTORE_SUCCESSES = "restore_successes"
        private const val KEY_LIBRARY_CHECK = "library_check"
        private const val KEY_LIBRARY_HEALTHY = "library_healthy"
        private const val KEY_LIBRARY_CHECK_AT = "library_check_at"
        private const val KEY_TASK_REMOVED = "task_removed"
        private const val KEY_STALE_SERVICE_STOPS = "stale_service_stops"
        private const val KEY_BLOCKED_NAVIGATIONS = "blocked_navigations"
        private const val KEY_SELF_TEST = "self_test"
        private const val KEY_SELF_TEST_PASSED = "self_test_passed"
        private const val KEY_SELF_TEST_AT = "self_test_at"
        private const val KEY_DEVICE_PROFILE = "device_profile"
        private const val KEY_DEVICE_PROFILE_AT = "device_profile_at"
        private const val KEY_STRESS_TEST = "stress_test"
        private const val KEY_STRESS_TEST_PASSED = "stress_test_passed"
        private const val KEY_STRESS_TEST_AT = "stress_test_at"
        private const val KEY_LAST_EVENT = "last_event"
        private const val KEY_LAST_EVENT_AT = "last_event_at"
    }
}
