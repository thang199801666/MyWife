package com.example.videoshield

import android.webkit.JavascriptInterface
import android.os.Handler
import android.os.Looper

class VideoShieldBridge(
    private val originAllowed: () -> Boolean,
    private val onPageAdsHidden: (Int) -> Unit,
    private val onAdSkipped: () -> Unit,
    private val onSegmentSkipped: (String, Long) -> Unit,
    private val onPlaybackState: (Boolean, Boolean, String, String, String, String, Long, Long) -> Unit,
    private val onPlaybackEnded: (String) -> Unit,
    private val onCompatibilityReport: (Boolean, Boolean, Int, Int) -> Unit,
    private val onDownloadRequested: () -> Unit,
    private val onPlayerOptionsRequested: () -> Unit = {},
    private val onQualitySelected: (String) -> Unit = {},
    private val onRepeatSelected: (Boolean) -> Unit = {},
    private val onPlaybackRateSelected: (Float) -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private val playbackLock = Any()
    private var pendingPlayback: PlaybackReport? = null
    private var playbackDispatchScheduled = false
    private val playbackDispatch = Runnable {
        val report = synchronized(playbackLock) {
            playbackDispatchScheduled = false
            pendingPlayback.also { pendingPlayback = null }
        } ?: return@Runnable
        if (!closed && originAllowed()) {
            onPlaybackState.invoke(
                report.playing, report.buffering, report.title, report.channel, report.channelUrl, report.videoId,
                report.positionMs, report.durationMs
            )
        }
    }

    // JavascriptInterface runs on JavaBridge; originAllowed reads WebView.url on the UI thread.
    private fun dispatch(action: () -> Unit) {
        if (closed) return
        mainHandler.post { if (!closed && originAllowed()) action() }
    }

    @JavascriptInterface
    fun requestDownload() { dispatch { onDownloadRequested.invoke() } }

    @JavascriptInterface
    fun requestPlayerOptions() { dispatch { onPlayerOptionsRequested.invoke() } }

    @JavascriptInterface
    fun onQualitySelected(quality: String?) {
        val value=quality.orEmpty()
        if(value in ShieldPreferences.SUPPORTED_QUALITY_VALUES) dispatch { onQualitySelected.invoke(value) }
    }

    /**
     * Mirrors the repeat switch exposed by YouTube's own player settings back into
     * the native preference store. Without this bridge the injected repeat policy
     * can race the website UI and immediately turn a user-selected loop back off.
     */
    @JavascriptInterface
    fun onRepeatSelected(enabled: Boolean) {
        dispatch { onRepeatSelected.invoke(enabled) }
    }

    /**
     * Mirrors a playback-speed selection made in YouTube's own settings sheet.
     * Keeping this in the native preference store prevents the sticky playback-rate
     * policy from immediately restoring the previous app-side value.
     */
    @JavascriptInterface
    fun onPlaybackRateSelected(rate: Double) {
        if (!rate.isFinite()) return
        val safe = rate.toFloat().coerceIn(0.25f, 4.0f)
        dispatch { onPlaybackRateSelected.invoke(safe) }
    }

    @JavascriptInterface
    fun onPageAdsHidden(count: Int) {
        dispatch { onPageAdsHidden.invoke(count.coerceIn(0, 100)) }
    }

    @JavascriptInterface
    fun onAdSkipped() {
        dispatch { onAdSkipped.invoke() }
    }

    @JavascriptInterface
    fun onSegmentSkipped(category: String?, durationMs: Long) {
        dispatch { onSegmentSkipped.invoke(category.orEmpty().take(40), durationMs.coerceIn(0L, 60L * 60L * 1000L)) }
    }

    @JavascriptInterface
    fun onPlaybackState(
        playing: Boolean,
        buffering: Boolean,
        title: String?,
        channel: String?,
        channelUrl: String?,
        videoId: String?,
        positionMs: Long,
        durationMs: Long
    ) {
        if (closed) return
        val report = PlaybackReport(
            playing = playing,
            buffering = playing && buffering,
            title = title.orEmpty().take(240),
            channel = channel.orEmpty().take(180),
            channelUrl = channelUrl.orEmpty().take(1000),
            videoId = videoId.orEmpty().take(64),
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L)
        )
        // Playback reports are snapshots. If the UI thread is temporarily busy, only the
        // newest snapshot matters; don't let stale bridge callbacks build a main-queue tail.
        val shouldPost = synchronized(playbackLock) {
            pendingPlayback = report
            if (playbackDispatchScheduled) false else {
                playbackDispatchScheduled = true
                true
            }
        }
        if (shouldPost) mainHandler.post(playbackDispatch)
    }

    fun close() {
        if (closed) return
        closed = true
        synchronized(playbackLock) {
            pendingPlayback = null
            playbackDispatchScheduled = false
        }
        mainHandler.removeCallbacksAndMessages(null)
    }

    private data class PlaybackReport(
        val playing: Boolean,
        val buffering: Boolean,
        val title: String,
        val channel: String,
        val channelUrl: String,
        val videoId: String,
        val positionMs: Long,
        val durationMs: Long
    )

    @JavascriptInterface
    fun onPlaybackEnded(videoId: String?) {
        val safe = videoId.orEmpty().take(64)
        if (safe.isNotBlank()) dispatch { onPlaybackEnded.invoke(safe) }
    }

    @JavascriptInterface
    fun onCompatibilityReport(playerFound: Boolean, videoFound: Boolean, scriptErrors: Int, ruleVersion: Int) {
        dispatch { onCompatibilityReport.invoke(playerFound, videoFound, scriptErrors.coerceIn(0, 100), ruleVersion) }
    }
}
