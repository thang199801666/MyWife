package com.example.videoshield

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : LocalizedActivity() {
    private lateinit var p: ShieldPreferences
    private lateinit var rules: RulePackManager
    private lateinit var ruleStatus: TextView
    private lateinit var ruleUrl: EditText
    private lateinit var allowlistStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        SystemBarInsets.install(this)
        p = ShieldPreferences(this)
        rules = RulePackManager(this)
        applyThemeSurface()
        findViewById<Button>(R.id.appUpdateButton).setOnClickListener {
            startActivity(android.content.Intent(this, AppUpdateActivity::class.java))
        }
        findViewById<Button>(R.id.languageButton).apply {
            text = getString(R.string.language_title) + " • " + getString(if (AppLanguage.tag(this@SettingsActivity) == "en") R.string.language_en else R.string.language_vi)
            setOnClickListener {
                android.app.AlertDialog.Builder(this@SettingsActivity)
                    .setTitle(R.string.language_title)
                    .setSingleChoiceItems(arrayOf(getString(R.string.language_vi), getString(R.string.language_en)), if (AppLanguage.tag(this@SettingsActivity) == "en") 1 else 0) { dialog, index ->
                        p.ruleUpdateUrl = ruleUrl.text.toString()
                        dialog.dismiss()
                        val language = if (index == 1) "en" else "vi"
                        if (language != AppLanguage.tag(this@SettingsActivity)) {
                            AppLanguage.select(this@SettingsActivity, language)
                            if (android.os.Build.VERSION.SDK_INT < 33) recreate()
                        }
                    }.setNegativeButton(R.string.ui_cancel, null).show()
            }
        }

        findViewById<Button>(R.id.enhancedDefaultsButton).setOnClickListener {
            EnhancedClientDefaults.apply(p)
            Toast.makeText(this, getString(R.string.ui_enhanced_client_defaults_applied), Toast.LENGTH_SHORT).show()
            recreate()
        }

        bind(R.id.settingShield, p.shieldEnabled) { p.shieldEnabled = it }
        bind(R.id.settingTrackers, p.blockTrackers) { p.blockTrackers = it }
        bind(R.id.settingShorts, p.blockShorts) { p.blockShorts = it }
        bind(R.id.settingRecommendations, p.blockRecommendations) { p.blockRecommendations = it }
        bind(R.id.settingComments, p.blockComments) { p.blockComments = it }
        bind(R.id.settingEndScreen, p.blockEndScreen) { p.blockEndScreen = it }
        bind(R.id.settingOpenInApp, p.blockOpenInApp) { p.blockOpenInApp = it }
        bind(R.id.settingAutoPip, p.autoPiP) { p.autoPiP = it }
        bind(R.id.settingBackgroundControls, p.backgroundControls) { p.backgroundControls = it }
        bind(R.id.settingFullscreenGestures, p.fullscreenGestures) { p.fullscreenGestures = it }
        bind(R.id.settingLightTheme, p.lightTheme) {
            if (p.lightTheme != it) {
                p.lightTheme = it
                recreate()
            }
        }
        bind(R.id.settingCompactChrome, p.compactYouTubeChrome) { p.compactYouTubeChrome = it }
        bind(R.id.settingAutoRepeat, p.autoRepeat) { p.autoRepeat = it }
        bind(R.id.settingCommunitySponsorSkip, p.communitySponsorSkip) { p.communitySponsorSkip = it }
        bind(R.id.settingSkipIntrosOutros, p.skipIntrosOutros) { p.skipIntrosOutros = it }
        findViewById<Button>(R.id.preferredQualityButton).setOnClickListener { cyclePreferredQuality(mobile = false) }
        findViewById<Button>(R.id.mobileQualityButton).setOnClickListener { cyclePreferredQuality(mobile = true) }
        bind(R.id.settingPlaybackRecovery, p.playbackRecovery) { p.playbackRecovery = it }
        bind(R.id.settingScreenOffPlayback, p.screenOffPlayback) { p.screenOffPlayback = it }
        bind(R.id.settingMemoryHardening, p.memoryHardening) { p.memoryHardening = it }
        findViewById<Button>(R.id.gestureSensitivityButton).setOnClickListener { cycleGestureSensitivity() }
        findViewById<Button>(R.id.doubleTapSeekButton).setOnClickListener { cycleDoubleTapSeek() }
        bind(R.id.settingRememberHistory, p.rememberHistory) { p.rememberHistory = it }
        bind(R.id.settingPersonalization, p.personalizedSuggestions) { p.personalizedSuggestions = it }
        findViewById<Button>(R.id.resetPersonalizationButton).setOnClickListener {
            p.recommendationsSince = System.currentTimeMillis()
            LibraryStore(this).use { it.clearPersonalization() }
            toast(getString(R.string.ui_learned_viewing_preferences_cleared))
        }
        bind(R.id.settingResumePlayback, p.resumePlayback) { p.resumePlayback = it }
        bind(R.id.settingAutoAdvanceQueue, p.autoAdvanceQueue) { p.autoAdvanceQueue = it }
        bind(R.id.settingAutoRuleUpdates, p.autoRuleUpdates) { p.autoRuleUpdates = it }
        bind(R.id.settingSafeMode, p.safeMode) {
            p.safeMode = it
            if (!it) p.safeModeReason = ""
            refreshStatus()
        }

        ruleStatus = findViewById(R.id.ruleStatus)
        ruleUrl = findViewById(R.id.ruleUrl)
        allowlistStatus = findViewById(R.id.allowlistStatus)
        ruleUrl.setText(p.ruleUpdateUrl)

        findViewById<Button>(R.id.updateRulesButton).setOnClickListener { updateRules() }
        findViewById<Button>(R.id.rollbackRulesButton).setOnClickListener {
            val ok = rules.rollback()
            if (ok) {
                p.safeMode = false
                p.safeModeReason = ""
                toast(getString(R.string.rules_rolled_back, rules.active().ruleVersion))
            } else toast(getString(R.string.ui_no_previous_rule_pack_is_available))
            refreshStatus()
        }
        findViewById<Button>(R.id.resetRulesButton).setOnClickListener {
            rules.resetToBundled()
            p.safeMode = false
            p.safeModeReason = ""
            toast(getString(R.string.ui_restored_bundled_rules))
            refreshStatus()
        }
        findViewById<Button>(R.id.clearAllowlistButton).setOnClickListener {
            p.clearChannelWhitelist()
            toast(getString(R.string.ui_channel_allowlist_cleared))
            refreshStatus()
        }
        findViewById<Button>(R.id.doneButton).setOnClickListener {
            p.ruleUpdateUrl = ruleUrl.text.toString()
            finish()
        }

        refreshStatus()
        refreshPlaybackControls()
    }

    private fun cycleGestureSensitivity() {
        val values = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f)
        val current = p.gestureSensitivity
        val index = values.indices.minByOrNull { kotlin.math.abs(values[it] - current) } ?: 1
        p.gestureSensitivity = values[(index + 1) % values.size]
        refreshPlaybackControls()
    }

    private fun cycleDoubleTapSeek() {
        val values = intArrayOf(5, 10, 15, 30)
        val current = p.doubleTapSeekSeconds
        val index = values.indexOf(current).takeIf { it >= 0 } ?: 1
        p.doubleTapSeekSeconds = values[(index + 1) % values.size]
        refreshPlaybackControls()
    }

    private fun refreshPlaybackControls() {
        findViewById<Button>(R.id.gestureSensitivityButton).text = getString(R.string.gesture_sensitivity, formatSensitivity(p.gestureSensitivity))
        findViewById<Button>(R.id.doubleTapSeekButton).text = getString(R.string.double_tap_seek, p.doubleTapSeekSeconds)
        findViewById<Button>(R.id.preferredQualityButton).text = getString(R.string.wifi_quality, qualityLabel(p.preferredQuality))
        findViewById<Button>(R.id.mobileQualityButton).text = getString(R.string.mobile_quality, qualityLabel(p.preferredQualityMobile))
    }

    private fun cyclePreferredQuality(mobile: Boolean) {
        val values = listOf(
            "adaptive", "highres", "hd2160", "hd1440", "hd1080", "hd720", "large", "medium", "small", "tiny"
        )
        val current = if (mobile) p.preferredQualityMobile else p.preferredQuality
        val index = values.indexOf(current).takeIf { it >= 0 } ?: 0
        val next = values[(index + 1) % values.size]
        if (mobile) p.preferredQualityMobile = next else p.preferredQuality = next
        refreshPlaybackControls()
    }

    private fun qualityLabel(value: String): String = when (value) {
        "adaptive" -> getString(R.string.quality_adaptive)
        "highres" -> getString(R.string.ui_highest)
        "hd2160" -> "2160p"
        "hd1440" -> "1440p"
        "hd1080" -> "1080p"
        "hd720" -> "720p"
        "large" -> "480p"
        "medium" -> "360p"
        "small" -> "240p"
        "tiny" -> "144p"
        else -> getString(R.string.ui_auto)
    }

    private fun applyThemeSurface() {
        AppTheme.applySystemBars(this)
        findViewById<android.view.View>(R.id.settingsRoot)?.setBackgroundColor(AppTheme.background(this))
    }

    private fun formatSensitivity(value: Float): String {
        val text = if (value % 1f == 0f) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')
        return "${text}×"
    }

    private fun updateRules() {
        val url = ruleUrl.text.toString().trim()
        p.ruleUpdateUrl = url
        if (url.isBlank()) {
            toast(getString(R.string.ui_enter_an_https_rule_pack_url_first))
            return
        }
        val button = findViewById<Button>(R.id.updateRulesButton)
        button.isEnabled = false
        button.text = getString(R.string.ui_updating)
        RulePackUpdater.update(url, rules) { result ->
            button.isEnabled = true
            button.text = getString(R.string.ui_check_install_rules)
            if (result.installed) {
                p.safeMode = false
                p.safeModeReason = ""
            }
            toast(result.message)
            refreshStatus()
        }
    }

    private fun refreshStatus() {
        val active = rules.active()
        val source = if (rules.isUsingBundled()) getString(R.string.ui_bundled) else getString(R.string.ui_downloaded)
        val safe = if (p.safeMode) " • ${getString(R.string.ui_safe_mode_badge)}" else ""
        val reason = if (p.safeModeReason.isNotBlank()) "\n${LocalizedPresentation.safeModeReason(this, p.safeModeReason)}" else ""
        ruleStatus.text = "${active.name} • v${active.ruleVersion} • $source$safe$reason"
        allowlistStatus.text = getString(R.string.allowed_channels, p.whitelistedChannels.size)
    }

    private fun bind(id: Int, value: Boolean, save: (Boolean) -> Unit) {
        findViewById<CompoundButton>(id).apply {
            isChecked = value
            setOnCheckedChangeListener { _, checked -> save(checked) }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
