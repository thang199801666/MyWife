package com.example.videoshield

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Conservative foreground-only playback watchdog with deduplicated, back-off recovery.
 *
 * Bridge heartbeats normally arrive roughly every 18-20 seconds. A missing heartbeat or a
 * main-frame error schedules one bounded recovery. A healthy heartbeat cancels a pending
 * recovery, which avoids reload races when WebView recovers by itself.
 */
class PlaybackRecoveryController(
    private val enabled: () -> Boolean,
    private val onRecover: (reason: String, attempt: Int) -> Unit,
    private val onExhausted: (reason: String) -> Unit = {},
    private val recoveryDelayPaddingMs: Long = 0L
) {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var expectedPlaying = false
    private var lastHeartbeatAt = 0L
    private var lastPositionMs = 0L
    private var stableProgressAt = 0L
    private var attempts = 0
    private var exhaustedNotified = false
    private var pendingRecoveryReason: String? = null
    private var lastRecoveryAt = 0L

    private val watchdog = Runnable {
        if (!active || !enabled() || !expectedPlaying) return@Runnable
        val elapsed = SystemClock.elapsedRealtime() - lastHeartbeatAt
        if (elapsed >= WATCHDOG_TIMEOUT_MS) {
            expectedPlaying = false
            scheduleRecovery("Player heartbeat stopped", WATCHDOG_RECOVERY_DELAY_MS)
        } else {
            scheduleWatchdog()
        }
    }

    private val recoveryRunnable = Runnable {
        val reason = pendingRecoveryReason ?: return@Runnable
        pendingRecoveryReason = null
        if (!active || !enabled()) return@Runnable
        if (attempts >= MAX_ATTEMPTS) {
            notifyExhausted("$reason after $MAX_ATTEMPTS recovery attempts")
            return@Runnable
        }
        attempts++
        lastRecoveryAt = SystemClock.elapsedRealtime()
        expectedPlaying = false
        stableProgressAt = 0L
        onRecover(reason, attempts)
    }

    fun setActive(value: Boolean) {
        active = value
        if (!value) {
            handler.removeCallbacks(watchdog)
            cancelPendingRecovery()
            expectedPlaying = false
        } else if (expectedPlaying) {
            scheduleWatchdog()
        }
    }

    fun onNavigationStarted() {
        expectedPlaying = false
        handler.removeCallbacks(watchdog)
        cancelPendingRecovery()
    }

    fun heartbeat(playing: Boolean, positionMs: Long) {
        val now = SystemClock.elapsedRealtime()
        lastHeartbeatAt = now
        expectedPlaying = playing

        // A live heartbeat wins over a previously scheduled reload.
        if (playing) cancelPendingRecovery()

        if (playing) {
            if (positionMs > lastPositionMs + 4_000L) {
                if (stableProgressAt == 0L) stableProgressAt = now
                if (now - stableProgressAt >= STABLE_RESET_MS) {
                    attempts = 0
                    exhaustedNotified = false
                }
            } else if (positionMs + 2_000L < lastPositionMs) {
                stableProgressAt = now
            }
            lastPositionMs = positionMs.coerceAtLeast(0L)
            scheduleWatchdog()
        } else {
            handler.removeCallbacks(watchdog)
        }
    }

    fun onMainFrameError(message: String, hasPlayableVideo: Boolean) {
        if (!hasPlayableVideo || !active || !enabled()) return
        val reason = if (message.isBlank()) "Main page load failed" else "Page error: $message"
        scheduleRecovery(reason, MAIN_FRAME_RETRY_DELAY_MS)
    }

    /** Request one bounded recovery through the same deduplicated/back-off path as page errors. */
    fun requestRecovery(reason: String, delayMs: Long = 0L) {
        scheduleRecovery(reason.ifBlank { "Playback recovery requested" }, delayMs.coerceAtLeast(0L))
    }

    fun resetAttempts() {
        attempts = 0
        exhaustedNotified = false
        stableProgressAt = 0L
        cancelPendingRecovery()
    }

    fun attemptCount(): Int = attempts

    fun hasPendingRecovery(): Boolean = pendingRecoveryReason != null

    fun dispose() {
        handler.removeCallbacksAndMessages(null)
        pendingRecoveryReason = null
    }

    private fun scheduleRecovery(reason: String, requestedDelayMs: Long) {
        if (!active || !enabled()) return
        if (attempts >= MAX_ATTEMPTS) {
            notifyExhausted("$reason after $MAX_ATTEMPTS recovery attempts")
            return
        }

        // Do not stack WebView reloads from simultaneous HTTP + heartbeat errors.
        if (pendingRecoveryReason != null) return
        pendingRecoveryReason = reason

        val sinceLast = SystemClock.elapsedRealtime() - lastRecoveryAt
        val minimumGapRemaining = if (lastRecoveryAt <= 0L) 0L else (MIN_RECOVERY_GAP_MS - sinceLast).coerceAtLeast(0L)
        val attemptBackoff = attempts * ATTEMPT_BACKOFF_MS
        val baseDelay = maxOf(requestedDelayMs, minimumGapRemaining, attemptBackoff)
        val delay = baseDelay + recoveryDelayPaddingMs.coerceIn(0L, MAX_DEVICE_PADDING_MS)
        handler.postDelayed(recoveryRunnable, delay)
    }

    private fun cancelPendingRecovery() {
        handler.removeCallbacks(recoveryRunnable)
        pendingRecoveryReason = null
    }

    private fun notifyExhausted(reason: String) {
        if (exhaustedNotified) return
        exhaustedNotified = true
        cancelPendingRecovery()
        onExhausted(reason)
    }

    private fun scheduleWatchdog() {
        handler.removeCallbacks(watchdog)
        if (active && enabled() && expectedPlaying) handler.postDelayed(watchdog, WATCHDOG_TIMEOUT_MS)
    }

    companion object {
        private const val WATCHDOG_TIMEOUT_MS = 28_000L
        private const val WATCHDOG_RECOVERY_DELAY_MS = 350L
        private const val MAIN_FRAME_RETRY_DELAY_MS = 1_800L
        private const val MIN_RECOVERY_GAP_MS = 4_000L
        private const val ATTEMPT_BACKOFF_MS = 1_500L
        private const val STABLE_RESET_MS = 30_000L
        private const val MAX_ATTEMPTS = 2
        private const val MAX_DEVICE_PADDING_MS = 5_000L
    }
}
