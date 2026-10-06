package com.example.videoshield

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class ShieldDashboardActivity : LocalizedActivity() {
    private lateinit var stats: ShieldStats
    private lateinit var prefs: ShieldPreferences
    private lateinit var rules: RulePackManager
    private lateinit var recoveryDiagnostics: RecoveryDiagnosticsStore
    private lateinit var compatibilityDiagnostics: CompatibilityDiagnosticsStore
    private lateinit var runtimeDiagnostics: RuntimeDiagnosticsStore
    private lateinit var libraryStore: LibraryStore
    private lateinit var rendererCrashGuard: RendererCrashLoopGuard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shield_dashboard)
        SystemBarInsets.install(this)
        stats = ShieldStats(this)
        prefs = ShieldPreferences(this)
        rules = RulePackManager(this)
        recoveryDiagnostics = RecoveryDiagnosticsStore(this)
        compatibilityDiagnostics = CompatibilityDiagnosticsStore(this)
        runtimeDiagnostics = RuntimeDiagnosticsStore(this)
        libraryStore = LibraryStore(this)
        rendererCrashGuard = RendererCrashLoopGuard(this)
        applyThemeSurface()

        findViewById<Button>(R.id.dashboardCloseButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.dashboardSettingsButton).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<Button>(R.id.dashboardResetStatsButton).setOnClickListener { stats.resetLifetime(); refresh() }
        findViewById<Button>(R.id.dashboardResetRecoveryButton).setOnClickListener { recoveryDiagnostics.reset(); refresh() }
        findViewById<Button>(R.id.dashboardResetCompatibilityButton).setOnClickListener { compatibilityDiagnostics.reset(); refresh() }
        findViewById<Button>(R.id.dashboardResetRuntimeButton).setOnClickListener {
            runtimeDiagnostics.reset()
            rendererCrashGuard.clear()
            refresh()
        }
        findViewById<Button>(R.id.dashboardRuntimeTestButton).setOnClickListener {
            val result = RuntimeSelfTest.run(libraryStore)
            runtimeDiagnostics.recordSelfTest(result.summary, result.passed)
            val integrity = libraryStore.quickIntegrityCheck()
            runtimeDiagnostics.recordLibraryCheck(integrity.summary, integrity.healthy)
            Toast.makeText(this, if (result.passed) getString(R.string.ui_runtime_self_test_passed) else getString(R.string.ui_runtime_self_test_found_a_problem), Toast.LENGTH_SHORT).show()
            refresh()
        }
        findViewById<Button>(R.id.dashboardStressTestButton).setOnClickListener {
            val button = findViewById<Button>(R.id.dashboardStressTestButton)
            button.isEnabled = false
            button.text = getString(R.string.ui_running_release_stress_test)
            Thread {
                val result = ReleaseStressHarness.run(libraryStore)
                runtimeDiagnostics.recordStressTest(result.summary, result.passed)
                runOnUiThread {
                    button.isEnabled = true
                    button.text = getString(R.string.ui_run_release_stress_test)
                    Toast.makeText(this, if (result.passed) getString(R.string.ui_release_stress_test_passed) else getString(R.string.ui_release_stress_test_found_a_problem), Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }.start()
        }
        findViewById<Button>(R.id.dashboardCompatibilityTestButton).setOnClickListener {
            val result = CompatibilitySelfTest.run(rules)
            compatibilityDiagnostics.recordSelfTest(result.summary, result.passed)
            Toast.makeText(this, if (result.passed) getString(R.string.ui_compatibility_self_test_passed) else getString(R.string.ui_compatibility_self_test_found_a_problem), Toast.LENGTH_SHORT).show()
            refresh()
        }
        findViewById<Button>(R.id.dashboardSafeModeButton).setOnClickListener {
            prefs.safeMode = !prefs.safeMode
            if (!prefs.safeMode) prefs.safeModeReason = ""
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        applyThemeSurface()
        refresh()
    }

    private fun applyThemeSurface() {
        val color = if (prefs.amoledTheme) Color.BLACK else Color.rgb(11, 12, 15)
        window.statusBarColor = color
        window.navigationBarColor = color
        findViewById<View>(R.id.dashboardRoot).setBackgroundColor(color)
    }

    private fun refresh() {
        val active = rules.active()
        findViewById<TextView>(R.id.dashboardState).text = buildString {
            append(if (prefs.shieldEnabled) getString(R.string.ui_ad_filtering_active) else getString(R.string.ui_ad_filtering_disabled))
            if (prefs.safeMode) append(" • SAFE MODE")
            append("\nRules v${active.ruleVersion} • ${active.name}")
            if (prefs.safeModeReason.isNotBlank()) append("\n${prefs.safeModeReason}")
        }
        findViewById<TextView>(R.id.networkCount).text = stats.lifetimeNetwork().toString()
        findViewById<TextView>(R.id.cosmeticCount).text = stats.lifetimePageAds().toString()
        findViewById<TextView>(R.id.skipCount).text = stats.lifetimeSkips().toString()
        findViewById<TextView>(R.id.segmentSkipCount).text = stats.lifetimeSegmentSkips().toString()
        findViewById<TextView>(R.id.totalCount).text = stats.lifetimeTotal().toString()
        findViewById<TextView>(R.id.allowlistCount).text = prefs.whitelistedChannels.size.toString()
        findViewById<TextView>(R.id.recoveryDiagnosticsText).text = recoveryDiagnostics.summary()
        findViewById<TextView>(R.id.compatibilityDiagnosticsText).text = compatibilityDiagnostics.summary()
        findViewById<TextView>(R.id.runtimeDiagnosticsText).text = runtimeDiagnostics.summary() + "\n" + rendererCrashGuard.summary()
        findViewById<Button>(R.id.dashboardSafeModeButton).text = if (prefs.safeMode) getString(R.string.ui_exit_safe_mode) else getString(R.string.ui_enter_safe_mode)
    }
}
