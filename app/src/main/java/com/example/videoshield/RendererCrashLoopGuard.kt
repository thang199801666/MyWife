package com.example.videoshield

import android.content.Context

/**
 * Persistent renderer-exit guard used to prevent repeated WebView crash/recreate loops.
 * A burst of renderer exits temporarily disables automatic reload recovery for this app
 * session while preserving manual Retry/Home controls.
 */
class RendererCrashLoopGuard(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("videoshield_renderer_guard", Context.MODE_PRIVATE)

    fun recordRendererExit(didCrash: Boolean, now: Long = System.currentTimeMillis()) {
        val first = prefs.getLong(KEY_WINDOW_STARTED_AT, 0L)
        val windowExpired = RendererCrashLoopPolicy.windowExpired(first, now, WINDOW_MS)
        val legacyCount = if (windowExpired) 0 else prefs.getInt(KEY_COUNT, 0)
        var crashes = if (windowExpired) 0 else prefs.getInt(KEY_CRASH_COUNT, 0)
        var terminations = if (windowExpired) 0 else prefs.getInt(KEY_TERMINATION_COUNT, 0)

        // Migrate an in-flight window created by an older build without throwing away evidence.
        if (!windowExpired && crashes == 0 && terminations == 0 && legacyCount > 0) {
            if (prefs.getBoolean(KEY_LAST_DID_CRASH, false)) crashes = legacyCount
            else terminations = legacyCount
        }
        if (didCrash) crashes++ else terminations++
        val count = crashes + terminations
        val window = if (windowExpired) now else first
        prefs.edit()
            .putLong(KEY_WINDOW_STARTED_AT, window)
            .putInt(KEY_COUNT, count)
            .putInt(KEY_CRASH_COUNT, crashes)
            .putInt(KEY_TERMINATION_COUNT, terminations)
            .putBoolean(KEY_LAST_DID_CRASH, didCrash)
            .putLong(KEY_LAST_EXIT_AT, now)
            .apply()
    }

    fun isGuardActive(now: Long = System.currentTimeMillis()): Boolean {
        val first = prefs.getLong(KEY_WINDOW_STARTED_AT, 0L)
        if (RendererCrashLoopPolicy.windowExpired(first, now, WINDOW_MS)) return false
        val crashes = prefs.getInt(KEY_CRASH_COUNT, 0)
        val terminations = prefs.getInt(KEY_TERMINATION_COUNT, 0)
        if (crashes == 0 && terminations == 0) {
            // Backward-compatible interpretation for a window written by an older version.
            val legacyCount = prefs.getInt(KEY_COUNT, 0)
            return if (prefs.getBoolean(KEY_LAST_DID_CRASH, false)) {
                RendererCrashLoopPolicy.guardActive(legacyCount, 0)
            } else {
                RendererCrashLoopPolicy.guardActive(0, legacyCount)
            }
        }
        return RendererCrashLoopPolicy.guardActive(crashes, terminations)
    }

    fun markStable(now: Long = System.currentTimeMillis()) {
        val last = prefs.getLong(KEY_LAST_EXIT_AT, 0L)
        if (RendererCrashLoopPolicy.stableWindowReached(last, now, STABLE_CLEAR_MS)) clear()
    }

    fun summary(now: Long = System.currentTimeMillis()): String {
        val first = prefs.getLong(KEY_WINDOW_STARTED_AT, 0L)
        var crashes = prefs.getInt(KEY_CRASH_COUNT, 0)
        var terminations = prefs.getInt(KEY_TERMINATION_COUNT, 0)
        if (crashes == 0 && terminations == 0) {
            val legacyCount = prefs.getInt(KEY_COUNT, 0)
            if (prefs.getBoolean(KEY_LAST_DID_CRASH, false)) crashes = legacyCount else terminations = legacyCount
        }
        val active = isGuardActive(now)
        return appContext.getString(
            R.string.diag_renderer_guard_detail,
            crashes,
            terminations,
            appContext.getString(if (active) R.string.diag_active else R.string.diag_clear)
        ) + if (first > 0L) {
            appContext.getString(R.string.diag_window_age, (now - first).coerceAtLeast(0L) / 1000L)
        } else ""
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_WINDOW_STARTED_AT = "window_started_at"
        private const val KEY_COUNT = "count"
        private const val KEY_CRASH_COUNT = "crash_count"
        private const val KEY_TERMINATION_COUNT = "termination_count"
        private const val KEY_LAST_DID_CRASH = "last_did_crash"
        private const val KEY_LAST_EXIT_AT = "last_exit_at"
        private const val WINDOW_MS = 10L * 60L * 1000L
        private const val STABLE_CLEAR_MS = 15L * 60L * 1000L
    }
}
