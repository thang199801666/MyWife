package com.example.videoshield

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.view.View
import android.widget.*

object EqDialog {
    private val dialogs = mutableMapOf<Activity, AlertDialog>()
    fun dismiss(activity: Activity) { dialogs.remove(activity)?.dismiss() }

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        dismiss(activity)
        val settings = EqPreferences(activity)
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding)
        }
        fun label(value: String) = TextView(activity).apply {
            text = value; setTextColor(Color.WHITE); textSize = 14f; setPadding(0, 8, 0, 8)
        }
        body.addView(label(activity.getString(R.string.ui_eq_applies_to_offline_video_mp3_in_downloads_it_does_not_yet_appl)))
        val toggle = Switch(activity).apply { text = activity.getString(R.string.ui_enable_eq); isChecked = settings.enabled }
        body.addView(toggle)
        val status = label(OfflineEqualizer.currentStatus(activity))
        body.addView(status)
        val choices = EqPolicy.presets.keys.toList() + "Custom"
        val presets = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, choices.map { key -> when(key) {
                "Flat" -> activity.getString(R.string.preset_flat); "Bass" -> activity.getString(R.string.preset_bass_boost)
                "Treble" -> activity.getString(R.string.preset_treble); "Vocal" -> activity.getString(R.string.preset_vocal)
                "Custom" -> activity.getString(R.string.custom_eq); else -> key
            } })
        }
        body.addView(presets)
        val sliders = mutableListOf<SeekBar>()
        val values = mutableListOf<TextView>()
        EqPolicy.frequencies.forEachIndexed { band, hz ->
            val value = label("")
            values.add(value); body.addView(value)
            val slider = SeekBar(activity).apply {
                max = 24
                contentDescription = "EQ $hz Hz"
                layoutParams = LinearLayout.LayoutParams(-1, (48 * resources.displayMetrics.density).toInt())
            }
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) settings.setGain(band, progress - 12)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
            sliders.add(slider); body.addView(slider)
        }
        body.addView(label(activity.getString(R.string.ui_use_the_phone_s_media_volume_buttons_boosting_eq_bands_automatica)))
        var refreshing = false
        var dismissed = false
        fun refresh() {
            if (dismissed) return
            refreshing = true
            toggle.isChecked = settings.enabled
            val gains = settings.gains()
            gains.forEachIndexed { i, gain ->
                sliders[i].progress = gain + 12
                val hz = EqPolicy.frequencies[i]
                val frequency = if (hz >= 1000) "${hz / 1000.0} kHz" else "$hz Hz"
                values[i].text = "$frequency     ${if (gain > 0) "+" else ""}$gain dB"
            }
            presets.setSelection(choices.indexOf(settings.preset).coerceAtLeast(0))
            status.text = OfflineEqualizer.currentStatus(activity)
            refreshing = false
        }
        toggle.setOnCheckedChangeListener { _, checked -> if (!refreshing) settings.enabled = checked }
        presets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = choices[position]
                if (!refreshing && position == presets.selectedItemPosition && selected != settings.preset && selected != "Custom") settings.selectPreset(selected)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> body.post { refresh() } }
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        val dialog = AlertDialog.Builder(activity).setTitle(activity.getString(R.string.ui_equalizer_eq))
            .setView(ScrollView(activity).apply { addView(body) })
            .setPositiveButton(activity.getString(R.string.ui_close), null).setNeutralButton(activity.getString(R.string.ui_reset), null).create()
        dialog.setOnDismissListener {
            dismissed = true
            dialogs.remove(activity)
            settings.prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
        dialog.setOnShowListener {
            refresh()
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { settings.selectPreset("Flat") }
        }
        dialogs[activity] = dialog
        dialog.show()
    }
}
