package com.example.videoshield

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt

/** Effects belong only to this player session, never the system output mix. */
class OfflineEqualizer(private val context: Context, private val player: MediaPlayer) : AutoCloseable {
    private val settings = EqPreferences(context)
    private val main = Handler(Looper.getMainLooper())
    private var effect: Equalizer? = null
    private var closed = false
    private var failed = false
    private var lastEnabled = false
    var status = context.getString(R.string.eq_off)
        private set
    private val applyTask = Runnable { applySettings() }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        main.removeCallbacks(applyTask); main.post(applyTask)
    }

    init {
        active.add(this)
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        applySettings()
    }

    private fun applySettings() {
        if (closed) return
        val enabled = settings.enabled
        if (enabled != lastEnabled) failed = false
        lastEnabled = enabled
        if (!enabled) {
            releaseEffect()
            runCatching { player.setVolume(1f, 1f) }
            status = context.getString(R.string.eq_off)
            return
        }
        if (failed) return
        try {
            if (effect == null) {
                val sessionId = player.audioSessionId
                require(sessionId > 0)
                effect = Equalizer(0, sessionId).also { eq ->
                    eq.setControlStatusListener { _, _ -> main.post(applyTask) }
                }
            }
            val eq = effect ?: return
            if (!eq.hasControl()) {
                player.setVolume(1f, 1f)
                status = context.getString(R.string.eq_no_control)
                return
            }
            val range = eq.bandLevelRange
            val gains = settings.gains()
            val count = eq.numberOfBands.toInt()
            require(count > 0)
            for (band in 0 until count) {
                val hz = eq.getCenterFreq(band.toShort()) / 1000.0
                val gain = (EqPolicy.gainAt(hz, gains) * 100).roundToInt()
                    .coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                eq.setBandLevel(band.toShort(), gain)
            }
            check(eq.setEnabled(true) == android.media.audiofx.AudioEffect.SUCCESS)
            val volume = EqPolicy.headroom(gains)
            player.setVolume(volume, volume)
            status = context.getString(R.string.eq_active,count)
        } catch (_: Exception) {
            failed = true
            releaseEffect()
            runCatching { player.setVolume(1f, 1f) }
            status = context.getString(R.string.eq_unsupported)
        }
    }

    private fun releaseEffect() {
        effect?.let { runCatching { it.setControlStatusListener(null); it.release() } }
        effect = null
    }

    override fun close() {
        if (closed) return
        closed = true
        main.removeCallbacksAndMessages(null)
        settings.prefs.unregisterOnSharedPreferenceChangeListener(listener)
        releaseEffect()
        active.remove(this)
    }

    companion object {
        private val active = linkedSetOf<OfflineEqualizer>()
        fun currentStatus(context: Context): String = active.lastOrNull()?.status
            ?: context.getString(R.string.eq_open)
    }
}
