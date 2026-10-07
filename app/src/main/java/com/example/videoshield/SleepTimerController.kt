package com.example.videoshield

import android.os.Handler
import android.os.Looper
import kotlin.math.ceil

class SleepTimerController(
    private val preferences: ShieldPreferences,
    private val onExpired: () -> Unit,
    private val onTick: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var expiredForTarget = 0L

    private val ticker = object : Runnable {
        override fun run() {
            val endAt = preferences.sleepTimerEndAtMs
            if (endAt <= 0L) return
            val remaining = endAt - System.currentTimeMillis()
            if (remaining <= 0L) {
                preferences.sleepTimerEndAtMs = 0L
                if (expiredForTarget != endAt) {
                    expiredForTarget = endAt
                    onExpired()
                }
                onTick()
                return
            }
            onTick()
            handler.postDelayed(this, nextWakeDelayMs(remaining))
        }
    }

    fun restore() {
        handler.removeCallbacks(ticker)
        val endAt = preferences.sleepTimerEndAtMs
        if (endAt > System.currentTimeMillis()) {
            expiredForTarget = 0L
            handler.post(ticker)
        } else if (endAt > 0L) {
            preferences.sleepTimerEndAtMs = 0L
        }
    }

    fun start(minutes: Int) {
        val safeMinutes = minutes.coerceIn(1, 24 * 60)
        preferences.sleepTimerEndAtMs = System.currentTimeMillis() + safeMinutes * 60_000L
        expiredForTarget = 0L
        restore()
    }

    fun cancel() {
        preferences.sleepTimerEndAtMs = 0L
        handler.removeCallbacks(ticker)
        onTick()
    }

    fun remainingMinutes(): Int {
        val remaining = preferences.sleepTimerEndAtMs - System.currentTimeMillis()
        return if (remaining <= 0L) 0 else ceil(remaining / 60_000.0).toInt()
    }

    fun dispose() {
        handler.removeCallbacks(ticker)
    }

    internal fun nextWakeDelayMs(remainingMs: Long): Long =
        WakeSchedulingPolicy.sleepTimerDelayMs(remainingMs)
}
