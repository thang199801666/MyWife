package com.example.videoshield

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Single boundary for mirroring WebView playback state into PlaybackService.
 * This keeps foreground-service policy and Intent contracts out of MainActivity.
 */
class PlaybackServicePublisher(context: Context) {
    private val appContext = context.applicationContext

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
            if (!enabled) stop()
            return
        }
        val safeDuration = durationMs.coerceAtLeast(0L)
        val safePosition = PlaybackProgressPolicy.normalize(positionMs, safeDuration)
        val intent = Intent(appContext, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_UPDATE
            putExtra(PlaybackService.EXTRA_PLAYING, playing)
            putExtra(PlaybackService.EXTRA_TITLE, title)
            putExtra(PlaybackService.EXTRA_CHANNEL, channel)
            putExtra(PlaybackService.EXTRA_VIDEO_ID, videoId)
            putExtra(PlaybackService.EXTRA_POSITION_MS, safePosition)
            putExtra(PlaybackService.EXTRA_DURATION_MS, safeDuration)
            putExtra(PlaybackService.EXTRA_PLAYBACK_RATE, playbackRate.coerceIn(0.25f, 4f))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appContext.startForegroundService(intent)
        else appContext.startService(intent)
    }

    fun stop() {
        appContext.stopService(Intent(appContext, PlaybackService::class.java))
    }
}
