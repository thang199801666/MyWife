package com.example.videoshield

import android.content.Context
import java.text.DateFormat
import java.util.Date

/** Local-only compatibility telemetry. No browsing data leaves the device. */
class CompatibilityDiagnosticsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("videoshield_compatibility_diagnostics", Context.MODE_PRIVATE)

    fun recordReport(
        url: String?,
        playerFound: Boolean,
        videoFound: Boolean,
        scriptErrors: Int,
        reportedRuleVersion: Int,
        activeRuleVersion: Int
    ) {
        if (!YouTubeAdapter.isWatchUrl(url)) return
        val healthy = playerFound && videoFound && scriptErrors <= 1 && reportedRuleVersion == activeRuleVersion
        prefs.edit()
            .putLong(KEY_TOTAL, totalReports() + 1L)
            .putLong(if (healthy) KEY_HEALTHY else KEY_UNHEALTHY, if (healthy) healthyReports() + 1L else unhealthyReports() + 1L)
            .putBoolean(KEY_LAST_PLAYER, playerFound)
            .putBoolean(KEY_LAST_VIDEO, videoFound)
            .putInt(KEY_LAST_ERRORS, scriptErrors.coerceIn(0, 100))
            .putInt(KEY_LAST_REPORTED_RULE, reportedRuleVersion)
            .putInt(KEY_LAST_ACTIVE_RULE, activeRuleVersion)
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordSelfTest(summary: String, passed: Boolean) {
        prefs.edit()
            .putString(KEY_SELF_TEST, summary.take(1200))
            .putBoolean(KEY_SELF_TEST_PASSED, passed)
            .putLong(KEY_SELF_TEST_AT, System.currentTimeMillis())
            .apply()
    }

    fun totalReports(): Long = prefs.getLong(KEY_TOTAL, 0L)
    fun healthyReports(): Long = prefs.getLong(KEY_HEALTHY, 0L)
    fun unhealthyReports(): Long = prefs.getLong(KEY_UNHEALTHY, 0L)

    fun summary(): String = buildString {
        append("Bridge reports: ${totalReports()} • healthy ${healthyReports()} • unhealthy ${unhealthyReports()}")
        val lastAt = prefs.getLong(KEY_LAST_AT, 0L)
        if (lastAt > 0L) {
            append("\nLast: player ${yesNo(prefs.getBoolean(KEY_LAST_PLAYER, false))}")
            append(" • video ${yesNo(prefs.getBoolean(KEY_LAST_VIDEO, false))}")
            append(" • script errors ${prefs.getInt(KEY_LAST_ERRORS, 0)}")
            val reported = prefs.getInt(KEY_LAST_REPORTED_RULE, 0)
            val active = prefs.getInt(KEY_LAST_ACTIVE_RULE, 0)
            append("\nRules reported $reported / active $active")
            append(" • ${formatAt(lastAt)}")
        }
        val selfTest = prefs.getString(KEY_SELF_TEST, "").orEmpty()
        val selfTestAt = prefs.getLong(KEY_SELF_TEST_AT, 0L)
        if (selfTest.isNotBlank()) {
            append("\nSelf-test ${if (prefs.getBoolean(KEY_SELF_TEST_PASSED, false)) "PASS" else "FAIL"}")
            if (selfTestAt > 0L) append(" • ${formatAt(selfTestAt)}")
            append("\n$selfTest")
        }
    }

    fun reset() = prefs.edit().clear().apply()

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
    private fun formatAt(value: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))

    companion object {
        private const val KEY_TOTAL = "total"
        private const val KEY_HEALTHY = "healthy"
        private const val KEY_UNHEALTHY = "unhealthy"
        private const val KEY_LAST_PLAYER = "last_player"
        private const val KEY_LAST_VIDEO = "last_video"
        private const val KEY_LAST_ERRORS = "last_errors"
        private const val KEY_LAST_REPORTED_RULE = "last_reported_rule"
        private const val KEY_LAST_ACTIVE_RULE = "last_active_rule"
        private const val KEY_LAST_AT = "last_at"
        private const val KEY_SELF_TEST = "self_test"
        private const val KEY_SELF_TEST_PASSED = "self_test_passed"
        private const val KEY_SELF_TEST_AT = "self_test_at"
    }
}
