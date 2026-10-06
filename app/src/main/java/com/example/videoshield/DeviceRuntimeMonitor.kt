package com.example.videoshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager

/**
 * Tracks device runtime signals that materially affect WebView media playback.
 * The receiver lives only for the Activity lifetime and never performs playback
 * work itself; callers decide how aggressively to react.
 */
data class DeviceRuntimeState(
    val interactive: Boolean,
    val powerSaveMode: Boolean,
    val deviceIdleMode: Boolean
)

class DeviceRuntimeMonitor(
    context: Context,
    private val onChanged: (DeviceRuntimeState, reason: String) -> Unit
) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var registered = false
    private var lastState = currentState()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            publish(intent?.action.orEmpty())
        }
    }

    fun currentState(): DeviceRuntimeState = DeviceRuntimeState(
        interactive = powerManager.isInteractive,
        powerSaveMode = powerManager.isPowerSaveMode,
        deviceIdleMode = powerManager.isDeviceIdleMode
    )

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION") appContext.registerReceiver(receiver, filter)
        }
        registered = true
        lastState = currentState()
        onChanged(lastState, "monitor-start")
    }

    fun stop() {
        if (!registered) return
        try { appContext.unregisterReceiver(receiver) } catch (_: Exception) {}
        registered = false
    }

    private fun publish(reason: String) {
        val state = currentState()
        if (state == lastState && reason != Intent.ACTION_USER_PRESENT) return
        lastState = state
        onChanged(state, reason)
    }
}
