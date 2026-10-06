package com.example.videoshield

import android.content.ComponentCallbacks2
import android.webkit.CookieManager
import android.webkit.WebView

/**
 * Centralizes WebView/UI lifecycle, memory pressure and playback wake-lock policy.
 * This prevents Activity callbacks from independently making conflicting decisions.
 */
class WebViewLifecycleCoordinator(
    private val webView: WebView,
    private val preferences: ShieldPreferences,
    private val runtimeDiagnostics: RuntimeDiagnosticsStore,
    private val wakeLock: PlaybackWakeLockController,
    private val devicePolicy: DeviceCompatibilityPolicy,
    private val sessionState: () -> PlaybackSessionState,
    private val isInPictureInPicture: () -> Boolean,
    private val isRendererGone: () -> Boolean
) {
    var foreground: Boolean = false
        private set
    var runtimeState: DeviceRuntimeState = DeviceRuntimeState(interactive = true, powerSaveMode = false, deviceIdleMode = false)
        private set
    var webViewPaused: Boolean = false
        private set

    fun onActivityResumed() {
        foreground = true
        runtimeDiagnostics.recordForeground()
        val resume = Runnable { setPaused(false, "activity resumed • ${devicePolicy.profile}") }
        if (devicePolicy.webViewResumeDelayMs > 0L && webViewPaused) {
            webView.postDelayed(resume, devicePolicy.webViewResumeDelayMs)
        } else {
            resume.run()
        }
        updateWakeLock("activity resumed")
    }

    fun onActivityPaused() {
        foreground = false
        runtimeDiagnostics.recordBackground()
        if (preferences.memoryHardening && !shouldKeepActiveInBackground()) {
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
        if (preferences.memoryHardening && !state.interactive && !foreground && !shouldKeepActiveInBackground()) {
            setPaused(true, "screen off without media session")
        }
        updateWakeLock(reason.ifBlank { "device runtime changed" })
    }

    fun onTrimMemory(level: Int) {
        runtimeDiagnostics.recordTrimMemory(level)
        if (!preferences.memoryHardening || isRendererGone()) return
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && !foreground && !shouldKeepActiveInBackground()) {
            setPaused(true, "trim-memory UI hidden")
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && !sessionState().playing) {
            try { CookieManager.getInstance().flush() } catch (_: Exception) {}
        }
    }

    fun onLowMemory() {
        runtimeDiagnostics.recordLowMemory()
        if (!preferences.memoryHardening || isRendererGone()) return
        if (!sessionState().playing) {
            if (!foreground && !shouldKeepActiveInBackground()) {
                setPaused(true, "low-memory callback")
            }
            try { webView.clearCache(false) } catch (_: Exception) {}
            try { CookieManager.getInstance().flush() } catch (_: Exception) {}
        }
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

    fun release(reason: String) = wakeLock.release(reason)

    fun shouldKeepActiveInBackground(): Boolean {
        val session = sessionState()
        return (preferences.backgroundControls && session.hasSession) ||
            isInPictureInPicture() ||
            (preferences.autoPiP && session.playing)
    }

    private fun setPaused(paused: Boolean, reason: String) {
        if (isRendererGone() || webViewPaused == paused) return
        try {
            if (paused) webView.onPause() else webView.onResume()
            webViewPaused = paused
            runtimeDiagnostics.recordWebViewLifecycle(paused, reason)
        } catch (_: Exception) {}
    }
}
