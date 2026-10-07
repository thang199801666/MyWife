package com.example.videoshield

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast

/**
 * Event-driven playback inactivity guard. It arms one delayed callback while playback is
 * active instead of polling, then uses a short countdown only while the warning is visible.
 */
class PlaybackInactivityController(
    private val activity: Activity,
    private val preferences: ShieldPreferences,
    private val isPlaybackActive: () -> Boolean,
    private val pausePlayback: () -> Unit,
    private val releaseScreenOn: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private var lastInteractionAt = SystemClock.elapsedRealtime()
    private var playbackWasActive = false
    private var warningDialog: AlertDialog? = null
    private var warningDeadline = 0L
    var screenReleaseRequested: Boolean = false
        private set

    private val inactivityRunnable = Runnable { showWarningIfNeeded() }
    private val countdownRunnable = object : Runnable {
        override fun run() {
            val dialog = warningDialog
            if (dialog == null || !dialog.isShowing) return
            val remainingMs = warningDeadline - SystemClock.elapsedRealtime()
            if (remainingMs <= 0L) {
                expire()
                return
            }
            val seconds = ((remainingMs + 999L) / 1000L).toInt()
            dialog.setMessage(activity.getString(R.string.inactivity_warning_message, seconds))
            handler.postDelayed(this, 1000L)
        }
    }

    fun onForeground() {
        foreground = true
        screenReleaseRequested = false
        lastInteractionAt = SystemClock.elapsedRealtime()
        playbackWasActive = isPlaybackActive()
        scheduleIfNeeded()
    }

    fun onBackground() {
        foreground = false
        handler.removeCallbacks(inactivityRunnable)
        dismissWarning()
    }

    fun onUserInteraction() {
        // While the confirmation dialog is open, only its explicit confirmation button
        // counts. A stray touch must not silently extend the countdown.
        if (warningDialog?.isShowing == true) return
        screenReleaseRequested = false
        lastInteractionAt = SystemClock.elapsedRealtime()
        playbackWasActive = isPlaybackActive()
        scheduleIfNeeded()
    }

    fun onPlaybackContextChanged() {
        val active = isPlaybackActive()
        if (active && !playbackWasActive) {
            lastInteractionAt = SystemClock.elapsedRealtime()
        }
        playbackWasActive = active
        if (!active) {
            handler.removeCallbacks(inactivityRunnable)
            dismissWarning()
        } else {
            scheduleIfNeeded()
        }
    }

    fun onPreferencesChanged() {
        if (!preferences.inactivityWarningEnabled) dismissWarning()
        lastInteractionAt = SystemClock.elapsedRealtime()
        playbackWasActive = isPlaybackActive()
        scheduleIfNeeded()
    }

    fun dispose() {
        foreground = false
        handler.removeCallbacksAndMessages(null)
        dismissWarning()
    }

    private fun scheduleIfNeeded() {
        handler.removeCallbacks(inactivityRunnable)
        if (!foreground || !preferences.inactivityWarningEnabled || !isPlaybackActive()) return
        val timeoutMs = preferences.inactivityTimeoutMinutes * 60_000L
        val dueAt = lastInteractionAt + timeoutMs
        handler.postDelayed(inactivityRunnable, (dueAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
    }

    private fun showWarningIfNeeded() {
        if (!foreground || !preferences.inactivityWarningEnabled || !isPlaybackActive()) {
            onPlaybackContextChanged()
            return
        }
        if (warningDialog?.isShowing == true) return

        warningDeadline = SystemClock.elapsedRealtime() + WARNING_COUNTDOWN_MS
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.inactivity_warning_title)
            .setMessage(activity.getString(R.string.inactivity_warning_message, WARNING_COUNTDOWN_SECONDS))
            .setCancelable(false)
            .setPositiveButton(R.string.inactivity_still_watching, null)
            .setNegativeButton(R.string.inactivity_stop_now, null)
            .create()
        warningDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { confirmWatching() }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { expire() }
            handler.removeCallbacks(countdownRunnable)
            handler.postDelayed(countdownRunnable, 1000L)
        }
        dialog.setOnDismissListener {
            handler.removeCallbacks(countdownRunnable)
            if (warningDialog === dialog) warningDialog = null
        }
        dialog.show()
    }

    private fun confirmWatching() {
        screenReleaseRequested = false
        dismissWarning()
        lastInteractionAt = SystemClock.elapsedRealtime()
        playbackWasActive = isPlaybackActive()
        scheduleIfNeeded()
    }

    private fun expire() {
        screenReleaseRequested = true
        dismissWarning()
        handler.removeCallbacks(inactivityRunnable)
        pausePlayback()
        releaseScreenOn()
        playbackWasActive = false
        Toast.makeText(activity, R.string.inactivity_paused, Toast.LENGTH_SHORT).show()
        // Clearing KEEP_SCREEN_ON lets Android honor the device's configured screen-off
        // timeout. Since the user has already been inactive for the full guard interval,
        // Android normally turns the display off immediately or very shortly afterwards.
    }

    private fun dismissWarning() {
        handler.removeCallbacks(countdownRunnable)
        val dialog = warningDialog
        warningDialog = null
        if (dialog?.isShowing == true) dialog.dismiss()
    }

    companion object {
        private const val WARNING_COUNTDOWN_SECONDS = 30
        private const val WARNING_COUNTDOWN_MS = WARNING_COUNTDOWN_SECONDS * 1000L
    }
}
