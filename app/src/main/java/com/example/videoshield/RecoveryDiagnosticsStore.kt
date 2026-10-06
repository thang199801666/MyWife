package com.example.videoshield

import android.content.Context
import java.text.DateFormat
import java.util.Date

/** Persistent, privacy-preserving local diagnostics for playback recovery. */
class RecoveryDiagnosticsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("videoshield_recovery_diagnostics", Context.MODE_PRIVATE)

    fun recordRecovery(reason: String, attempt: Int = 0) {
        prefs.edit()
            .putLong(KEY_RECOVERIES, recoveries() + 1L)
            .putString(KEY_LAST_REASON, if (attempt > 0) "$reason (attempt $attempt)".take(240) else reason.take(240))
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordExhausted(reason: String) {
        prefs.edit()
            .putLong(KEY_EXHAUSTED, exhaustedRecoveries() + 1L)
            .putString(KEY_LAST_REASON, reason.take(240))
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordManualRetry() {
        prefs.edit().putLong(KEY_MANUAL_RETRIES, manualRetries() + 1L).apply()
    }

    fun recordRendererGone(didCrash: Boolean) {
        prefs.edit()
            .putLong(KEY_RENDERER_EXITS, rendererExits() + 1L)
            .putString(KEY_LAST_REASON, if (didCrash) "WebView renderer crashed" else "WebView renderer was terminated")
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordOfflineInterruption() {
        prefs.edit().putLong(KEY_OFFLINE, offlineInterruptions() + 1L).apply()
    }

    fun recordSuccessfulHeartbeat() {
        prefs.edit().putLong(KEY_LAST_SUCCESS_AT, System.currentTimeMillis()).apply()
    }

    fun recoveries(): Long = prefs.getLong(KEY_RECOVERIES, 0L)
    fun exhaustedRecoveries(): Long = prefs.getLong(KEY_EXHAUSTED, 0L)
    fun manualRetries(): Long = prefs.getLong(KEY_MANUAL_RETRIES, 0L)
    fun rendererExits(): Long = prefs.getLong(KEY_RENDERER_EXITS, 0L)
    fun offlineInterruptions(): Long = prefs.getLong(KEY_OFFLINE, 0L)
    fun lastReason(): String = prefs.getString(KEY_LAST_REASON, "").orEmpty()
    fun lastAt(): Long = prefs.getLong(KEY_LAST_AT, 0L)
    fun lastSuccessAt(): Long = prefs.getLong(KEY_LAST_SUCCESS_AT, 0L)

    fun summary(): String = buildString {
        append("Recovery attempts: ${recoveries()}")
        append(" • exhausted: ${exhaustedRecoveries()}")
        append("\nManual retries: ${manualRetries()} • renderer exits: ${rendererExits()}")
        append("\nOffline interruptions: ${offlineInterruptions()}")
        if (lastReason().isNotBlank()) append("\nLast recovery: ${lastReason()}")
        if (lastAt() > 0L) append("\n${formatAt(lastAt())}")
        if (lastSuccessAt() > 0L) append("\nLast healthy heartbeat: ${formatAt(lastSuccessAt())}")
    }

    fun reset() = prefs.edit().clear().apply()

    private fun formatAt(value: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))

    companion object {
        private const val KEY_RECOVERIES = "recoveries"
        private const val KEY_EXHAUSTED = "exhausted"
        private const val KEY_MANUAL_RETRIES = "manual_retries"
        private const val KEY_RENDERER_EXITS = "renderer_exits"
        private const val KEY_OFFLINE = "offline_interruptions"
        private const val KEY_LAST_REASON = "last_reason"
        private const val KEY_LAST_AT = "last_at"
        private const val KEY_LAST_SUCCESS_AT = "last_success_at"
    }
}
