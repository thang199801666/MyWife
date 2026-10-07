package com.example.videoshield

import android.content.ComponentCallbacks2
import android.webkit.CookieManager
import android.webkit.WebView

/**
 * Centralizes WebView/UI lifecycle, memory pressure and playback wake-lock policy.
 * This prevents Activity callbacks from independently making conflicting decisions.
 */
class WebViewLifecycleCoordinator(
    private var webView: WebView,
    private val preferences: ShieldPreferences,
    private val runtimeDiagnostics: RuntimeDiagnosticsStore,
    private val wakeLock: PlaybackWakeLockController,
    private val devicePolicy: DeviceCompatibilityPolicy,
    private val sessionState: () -> PlaybackSessionState,
    private val isInPictureInPicture: () -> Boolean,
    private val isRendererGone: () -> Boolean,
    private val rendererActive: () -> Boolean = { true }
) {
    var foreground: Boolean = false
        private set
    var runtimeState: DeviceRuntimeState = DeviceRuntimeState(interactive = true, powerSaveMode = false, deviceIdleMode = false)
        private set
    var webViewPaused: Boolean = false
        private set

    private var resumeGeneration = 0L
    private var pendingResume: Runnable? = null


    /**
     * Swap the renderer host without rebuilding playback/session state. Pending callbacks are
     * detached from the dead view first, then the new view inherits the coordinator's current
     * foreground/background policy.
     */
    fun rebindWebView(newWebView: WebView, reason: String) {
        cancelPendingResume()
        webView = newWebView
        webViewPaused = false
        if (foreground) {
            scheduleResume()
        } else if (preferences.memoryHardening && !shouldKeepActiveInBackground()) {
            setPaused(true, "$reason • background")
        }
        runtimeDiagnostics.recordWebViewLifecycle(webViewPaused, "$reason • rebound")
        updateWakeLock(reason)
    }

    /** Renderer loss releases process-bound work but deliberately preserves Activity foreground state. */
    fun onRendererGone(reason: String) {
        cancelPendingResume()
        webViewPaused = false
        wakeLock.release(reason)
    }

    fun onActivityResumed() {
        foreground = true
        runtimeDiagnostics.recordForeground()
        if (rendererActive()) scheduleResume() else cancelPendingResume()
        updateWakeLock("activity resumed")
    }

    fun onActivityPaused() {
        foreground = false
        cancelPendingResume()
        runtimeDiagnostics.recordBackground()
        if (rendererActive() && preferences.memoryHardening && !shouldKeepActiveInBackground()) {
            setPaused(true, "activity background without media session")
        }
        updateWakeLock("activity paused")
    }

    fun onDeviceRuntimeChanged(state: DeviceRuntimeState, reason: String) {
        val previous = runtimeState
        runtimeState = state
        if (previous.interactive != state.interactive || reason == "monitor-start") {
            runtimeDiagnostics.recordScreen(state.interactive, state.powerSaveMode, state.deviceIdleMode)
        } else if (previous.powerSaveMode != state.powerSaveMode || previous.deviceIdleMode != state.deviceIdleMode) {
            runtimeDiagnostics.recordPowerState(state.powerSaveMode, state.deviceIdleMode, reason)
        }
        if (rendererActive() && preferences.memoryHardening && !state.interactive && !foreground && !shouldKeepActiveInBackground()) {
            cancelPendingResume()
            setPaused(true, "screen off without media session")
        }
        updateWakeLock(reason.ifBlank { "device runtime changed" })
    }

    fun onTrimMemory(level: Int) {
        runtimeDiagnostics.recordTrimMemory(level)
        if (!preferences.memoryHardening || isRendererGone() || !rendererActive()) return
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && !foreground && !shouldKeepActiveInBackground()) {
            cancelPendingResume()
            setPaused(true, "trim-memory UI hidden")
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && !sessionState().playing) {
            try { CookieManager.getInstance().flush() } catch (_: Exception) {}
        }
    }

    fun onLowMemory() {
        runtimeDiagnostics.recordLowMemory()
        if (!preferences.memoryHardening || isRendererGone() || !rendererActive()) return
        if (!sessionState().playing) {
            if (!foreground && !shouldKeepActiveInBackground()) {
                cancelPendingResume()
                setPaused(true, "low-memory callback")
            }
            // The Activity's PlayerMediaRetentionPolicy owns paused-player memory-cache
            // eviction so active/PiP media can never be cleared by a generic lifecycle path.
            try { CookieManager.getInstance().flush() } catch (_: Exception) {}
        }
    }

    /**
     * After the paused-session grace period, allow Chromium to park the Watch renderer even when
     * background media controls are enabled. Native session state stays alive and a later media
     * command explicitly wakes the renderer before dispatching into JavaScript.
     */
    fun trimPausedBackgroundSession(reason: String): Boolean {
        if (!preferences.memoryHardening || isRendererGone() || !rendererActive()) return false
        val session = sessionState()
        if (session.playing || session.buffering || isInPictureInPicture()) return false
        cancelPendingResume()
        setPaused(true, reason)
        return webViewPaused
    }

    /** Wake a renderer parked by paused-session trimming before Play/Seek/Rate/Repeat commands. */
    fun resumeForPlaybackCommand(reason: String) {
        if (isRendererGone() || !rendererActive()) return
        cancelPendingResume()
        setPaused(false, reason)
        updateWakeLock(reason)
    }

    fun updateWakeLock(reason: String) {
        val session = sessionState()
        wakeLock.update(
            enabled = preferences.screenOffPlayback,
            playing = session.playing,
            backgroundControls = preferences.backgroundControls,
            activityForeground = foreground,
            screenInteractive = runtimeState.interactive,
            reason = reason
        )
    }

    fun release(reason: String) {
        foreground = false
        cancelPendingResume()
        wakeLock.release(reason)
    }

    fun shouldKeepActiveInBackground(): Boolean {
        val session = sessionState()
        return (preferences.backgroundControls && session.hasSession) ||
            isInPictureInPicture() ||
            (preferences.autoPiP && session.playing)
    }

    private fun scheduleResume() {
        cancelPendingResume()
        if (!rendererActive() || isRendererGone()) return
        val generation = ++resumeGeneration
        val resume = Runnable {
            pendingResume = null
            if (generation != resumeGeneration || !foreground || isRendererGone()) return@Runnable
            setPaused(false, "activity resumed • ${devicePolicy.profile}")
        }
        if (devicePolicy.webViewResumeDelayMs > 0L && webViewPaused) {
            pendingResume = resume
            webView.postDelayed(resume, devicePolicy.webViewResumeDelayMs)
        } else {
            resume.run()
        }
    }

    private fun cancelPendingResume() {
        ++resumeGeneration
        pendingResume?.let(webView::removeCallbacks)
        pendingResume = null
    }

    private fun setPaused(paused: Boolean, reason: String) {
        if (!rendererActive() || isRendererGone() || webViewPaused == paused) return
        try {
            if (paused) webView.onPause() else webView.onResume()
            webViewPaused = paused
            runtimeDiagnostics.recordWebViewLifecycle(paused, reason)
        } catch (_: Exception) {}
    }
}
