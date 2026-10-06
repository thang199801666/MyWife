package com.example.videoshield

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Owns the PlaybackService -> Activity command bus registration lifecycle.
 * MainActivity only supplies media actions; it no longer owns BroadcastReceiver plumbing.
 */
class PlayerCommandRouter(
    private val context: Context,
    private val onCommand: (command: String, positionMs: Long?) -> Unit
) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val command = intent?.getStringExtra(PlaybackService.EXTRA_COMMAND) ?: return
            val position = if (intent.hasExtra(PlaybackService.EXTRA_POSITION_MS)) {
                intent.getLongExtra(PlaybackService.EXTRA_POSITION_MS, -1L).takeIf { it >= 0L }
            } else null
            onCommand(command, position)
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter(PlaybackService.ACTION_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // Export flags were introduced in API 33; this branch only runs on older Android.
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun stop() {
        if (!registered) return
        try { context.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
        registered = false
    }
}
