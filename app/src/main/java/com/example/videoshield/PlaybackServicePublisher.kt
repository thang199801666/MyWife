package com.example.videoshield

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * Single boundary for mirroring WebView playback state into PlaybackService.
 * This keeps foreground-service policy and Intent contracts out of MainActivity.
 */
class PlaybackServicePublisher(context: Context) {
    private val appContext = context.applicationContext
    private var lastPublishedAt = 0L
    private var lastPlaying = false
    private var lastTitle = ""
    private var lastChannel = ""
    private var lastVideoId = ""
    private var lastDurationMs = 0L
    private var lastPlaybackRate = 1f
    private var stopSent = false

    fun publish(
        enabled: Boolean,
        hasSession: Boolean,
        playing: Boolean,
        title: String,
        channel: String,
        videoId: String,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float
    ) {
        if (!enabled || !hasSession) {
            // A newly recreated Activity does not know whether the previous publisher left
            // the service alive. Send one stop for this idle epoch, then suppress repeats.
            if (!stopSent) {
                appContext.stopService(Intent(appContext, PlaybackService::class.java))
                stopSent = true
            }
            return
        }
        val safeDuration = durationMs.coerceAtLeast(0L)
        val safePosition = PlaybackProgressPolicy.normalize(positionMs, safeDuration)
        val safeRate = playbackRate.coerceIn(0.25f, 4f)
        val now = SystemClock.elapsedRealtime()
        val structuralChange = playing != lastPlaying || title != lastTitle || channel != lastChannel ||
            videoId != lastVideoId || safeDuration != lastDurationMs || kotlin.math.abs(safeRate - lastPlaybackRate) > 0.01f
        if (!structuralChange && now - lastPublishedAt < POSITION_PUBLISH_INTERVAL_MS) return
        lastPublishedAt = now
        lastPlaying = playing
        lastTitle = title
        lastChannel = channel
        lastVideoId = videoId
        lastDurationMs = safeDuration
        lastPlaybackRate = safeRate
        val intent = Intent(appContext, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_UPDATE
            putExtra(PlaybackService.EXTRA_PLAYING, playing)
            putExtra(PlaybackService.EXTRA_TITLE, title)
            putExtra(PlaybackService.EXTRA_CHANNEL, channel)
            putExtra(PlaybackService.EXTRA_VIDEO_ID, videoId)
            putExtra(PlaybackService.EXTRA_POSITION_MS, safePosition)
            putExtra(PlaybackService.EXTRA_DURATION_MS, safeDuration)
            putExtra(PlaybackService.EXTRA_PLAYBACK_RATE, safeRate)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appContext.startForegroundService(intent)
        else appContext.startService(intent)
        stopSent = false
    }

    fun stop() {
        lastPublishedAt = 0L
        lastVideoId = ""
        stopSent = true
        // Explicit stops must always cross the process boundary: this publisher may have
        // been recreated while an older Activity instance started the service.
        appContext.stopService(Intent(appContext, PlaybackService::class.java))
    }

    companion object {
        // MediaSession extrapolates progress from position + playbackRate. Position-only
        // keep-alives can therefore be sparse; structural play/video/rate changes still
        // publish immediately.
        private const val POSITION_PUBLISH_INTERVAL_MS = 45_000L
    }
}
