package com.example.videoshield

import android.webkit.JavascriptInterface
import android.os.Handler
import android.os.Looper

class VideoShieldBridge(
    private val originAllowed: () -> Boolean,
    private val onPageAdsHidden: (Int) -> Unit,
    private val onAdSkipped: () -> Unit,
    private val onSegmentSkipped: (String, Long) -> Unit,
    private val onPlaybackState: (Boolean, String, String, String, String, Long, Long) -> Unit,
    private val onPlaybackEnded: (String) -> Unit,
    private val onCompatibilityReport: (Boolean, Boolean, Int, Int) -> Unit,
    private val onDownloadRequested: () -> Unit,
    private val onQualitySelected: (String) -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    // JavascriptInterface runs on JavaBridge; originAllowed reads WebView.url on the UI thread.
    private fun dispatch(action: () -> Unit) {
        mainHandler.post { if (originAllowed()) action() }
    }

    @JavascriptInterface
    fun requestDownload() { dispatch { onDownloadRequested.invoke() } }

    @JavascriptInterface
    fun onQualitySelected(quality: String?) {
        val value=quality.orEmpty()
        if(value in ShieldPreferences.SUPPORTED_QUALITY_VALUES) dispatch { onQualitySelected.invoke(value) }
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
        title: String?,
        channel: String?,
        channelUrl: String?,
        videoId: String?,
        positionMs: Long,
        durationMs: Long
    ) {
        dispatch { onPlaybackState.invoke(
            playing,
            title.orEmpty().take(240),
            channel.orEmpty().take(180),
            channelUrl.orEmpty().take(1000),
            videoId.orEmpty().take(64),
            positionMs.coerceAtLeast(0L),
            durationMs.coerceAtLeast(0L)
        ) }
    }


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
