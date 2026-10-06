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
        val count = if (first <= 0L || now - first > WINDOW_MS) 1 else prefs.getInt(KEY_COUNT, 0) + 1
        val window = if (first <= 0L || now - first > WINDOW_MS) now else first
        prefs.edit()
            .putLong(KEY_WINDOW_STARTED_AT, window)
            .putInt(KEY_COUNT, count)
            .putBoolean(KEY_LAST_DID_CRASH, didCrash)
            .putLong(KEY_LAST_EXIT_AT, now)
            .apply()
    }

    fun isGuardActive(now: Long = System.currentTimeMillis()): Boolean {
        val first = prefs.getLong(KEY_WINDOW_STARTED_AT, 0L)
        if (first <= 0L || now - first > WINDOW_MS) return false
        return prefs.getInt(KEY_COUNT, 0) >= EXIT_THRESHOLD
    }

    fun markStable(now: Long = System.currentTimeMillis()) {
        val last = prefs.getLong(KEY_LAST_EXIT_AT, 0L)
        if (last <= 0L || now - last >= STABLE_CLEAR_MS) clear()
    }

    fun summary(now: Long = System.currentTimeMillis()): String {
        val first = prefs.getLong(KEY_WINDOW_STARTED_AT, 0L)
        val count = prefs.getInt(KEY_COUNT, 0)
        val active = isGuardActive(now)
        return appContext.getString(R.string.diag_renderer_guard, count, EXIT_THRESHOLD,
            appContext.getString(if (active) R.string.diag_active else R.string.diag_clear)) +
            if (first > 0L) appContext.getString(R.string.diag_window_age, (now - first).coerceAtLeast(0L) / 1000L) else ""
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_WINDOW_STARTED_AT = "window_started_at"
        private const val KEY_COUNT = "count"
        private const val KEY_LAST_DID_CRASH = "last_did_crash"
        private const val KEY_LAST_EXIT_AT = "last_exit_at"
        private const val WINDOW_MS = 10L * 60L * 1000L
        private const val STABLE_CLEAR_MS = 15L * 60L * 1000L
        private const val EXIT_THRESHOLD = 3
    }
}
