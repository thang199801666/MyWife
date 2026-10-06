package com.example.videoshield

import android.content.Context
import android.os.SystemClock

data class PlaybackSnapshot(
    val playing: Boolean,
    val title: String,
    val channel: String,
    val url: String,
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    /** Smooth mini-player progress between bridge reports while playback is active. */
    fun predictedPositionMs(now: Long = System.currentTimeMillis(), playbackRate: Float = 1f): Long =
        PlaybackProgressPolicy.predict(
            positionMs = positionMs,
            durationMs = durationMs,
            reportedAtMs = updatedAt,
            nowMs = now,
            playing = playing,
            playbackRate = playbackRate
        )
}

class PlaybackSnapshotStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("videoshield_playback_snapshot", Context.MODE_PRIVATE)
    private var lastWriteAt = 0L
    private var lastPlaying = false
    private var lastTitle = ""
    private var lastChannel = ""
    private var lastUrl = ""
    private var lastVideoId = ""
    private var lastDurationMs = 0L

    fun update(
        playing: Boolean,
        title: String,
        channel: String,
        url: String,
        videoId: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val safeTitle = title.take(240)
        val safeChannel = channel.take(180)
        val safeUrl = url.take(1000)
        val safeVideoId = videoId.take(64)
        val safeDuration = durationMs.coerceAtLeast(0L)
        val now = SystemClock.elapsedRealtime()
        val structuralChange = playing != lastPlaying || safeTitle != lastTitle || safeChannel != lastChannel ||
            safeUrl != lastUrl || safeVideoId != lastVideoId || safeDuration != lastDurationMs
        if (!structuralChange && now - lastWriteAt < POSITION_SNAPSHOT_INTERVAL_MS) return
        lastWriteAt = now
        lastPlaying = playing
        lastTitle = safeTitle
        lastChannel = safeChannel
        lastUrl = safeUrl
        lastVideoId = safeVideoId
        lastDurationMs = safeDuration
        prefs.edit()
            .putBoolean("playing", playing)
            .putString("title", safeTitle)
            .putString("channel", safeChannel)
            .putString("url", safeUrl)
            .putString("video_id", safeVideoId)
            .putLong("position_ms", positionMs.coerceAtLeast(0L))
            .putLong("duration_ms", safeDuration)
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    fun markStopped() {
        lastWriteAt = 0L
        lastPlaying = false
        prefs.edit().putBoolean("playing", false).putLong("updated_at", System.currentTimeMillis()).apply()
    }

    fun clear() {
        lastWriteAt = 0L
        lastVideoId = ""
        prefs.edit().clear().apply()
    }

    fun get(): PlaybackSnapshot = PlaybackSnapshot(
        playing = prefs.getBoolean("playing", false),
        title = prefs.getString("title", "").orEmpty(),
        channel = prefs.getString("channel", "").orEmpty(),
        url = prefs.getString("url", "").orEmpty(),
        videoId = prefs.getString("video_id", "").orEmpty(),
        positionMs = prefs.getLong("position_ms", 0L),
        durationMs = prefs.getLong("duration_ms", 0L),
        updatedAt = prefs.getLong("updated_at", 0L)
    )

    companion object {
        private const val POSITION_SNAPSHOT_INTERVAL_MS = 45_000L
    }
}
