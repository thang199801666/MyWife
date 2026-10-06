package com.example.videoshield

import android.content.Context

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

    fun update(
        playing: Boolean,
        title: String,
        channel: String,
        url: String,
        videoId: String,
        positionMs: Long,
        durationMs: Long
    ) {
        prefs.edit()
            .putBoolean("playing", playing)
            .putString("title", title.take(240))
            .putString("channel", channel.take(180))
            .putString("url", url.take(1000))
            .putString("video_id", videoId.take(64))
            .putLong("position_ms", positionMs.coerceAtLeast(0L))
            .putLong("duration_ms", durationMs.coerceAtLeast(0L))
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    fun markStopped() {
        prefs.edit().putBoolean("playing", false).putLong("updated_at", System.currentTimeMillis()).apply()
    }

    fun clear() {
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
}
