package com.example.videoshield

import android.content.Context

class EqPreferences(context: Context) {
    val prefs = context.applicationContext.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    val preset: String get() = prefs.getString("preset", "Flat").orEmpty()
    fun gains(): IntArray = IntArray(5) { prefs.getInt("band_$it", 0).coerceIn(-12, 12) }
    fun setGain(band: Int, gain: Int) {
        require(band in 0..4)
        prefs.edit().putInt("band_$band", gain.coerceIn(-12, 12)).putString("preset", "Custom").apply()
    }
    fun selectPreset(name: String) {
        val values = EqPolicy.presets[name] ?: return
        val edit = prefs.edit().putString("preset", name)
        values.forEachIndexed { i, gain -> edit.putInt("band_$i", gain) }
        edit.apply()
    }
}
