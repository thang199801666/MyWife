package com.example.videoshield

import android.content.Context
import android.os.PowerManager

/**
 * Holds a bounded PARTIAL_WAKE_LOCK only while playback is actively serving the
 * user outside the foreground UI. The lock is never held for paused playback.
 */
class PlaybackWakeLockController(
    context: Context,
    private val onChanged: (held: Boolean, reason: String) -> Unit = { _, _ -> }
) {
    private val wakeLock = (context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideoShield:Playback").apply {
            setReferenceCounted(false)
        }

    fun update(
        enabled: Boolean,
        playing: Boolean,
        backgroundControls: Boolean,
        activityForeground: Boolean,
        screenInteractive: Boolean,
        reason: String
    ) {
        val shouldHold = enabled && playing && backgroundControls && (!activityForeground || !screenInteractive)
        if (shouldHold && !wakeLock.isHeld) {
            try {
                wakeLock.acquire(MAX_HOLD_MS)
                onChanged(true, reason)
            } catch (_: SecurityException) {
                onChanged(false, "wake-lock denied")
            }
        } else if (!shouldHold && wakeLock.isHeld) {
            release(reason)
        }
    }

    fun isHeld(): Boolean = wakeLock.isHeld

    fun release(reason: String = "release") {
        if (!wakeLock.isHeld) return
        try { wakeLock.release() } catch (_: RuntimeException) {}
        onChanged(false, reason)
    }

    companion object {
        // Bounded acquisition prevents a stale process from holding the CPU forever.
        private const val MAX_HOLD_MS = 6L * 60L * 60L * 1000L
    }
}
