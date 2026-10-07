package com.example.videoshield

/** Immutable snapshot of the playback session owned by the native shell. */
data class PlaybackSessionState(
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val hasSession: Boolean = false,
    val title: String = "",
    val channel: String = "",
    val channelUrl: String = "",
    val videoId: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val positionReportedAt: Long = 0L,
    val playbackRate: Float = 1f,
    val repeatEnabled: Boolean = false
)

data class PlaybackSessionDelta(
    val previous: PlaybackSessionState,
    val current: PlaybackSessionState,
    val videoChanged: Boolean,
    val channelChanged: Boolean
) {
    val playingChanged: Boolean get() = previous.playing != current.playing
    val bufferingChanged: Boolean get() = previous.buffering != current.buffering
    val playbackStateChanged: Boolean get() = playingChanged || bufferingChanged
    val presentationChanged: Boolean get() =
        videoChanged || channelChanged || playbackStateChanged ||
            previous.title != current.title || previous.durationMs != current.durationMs
}

/**
 * Owns session state, persisted recovery snapshot and foreground-service publication.
 * UI/library policy remains outside this class so it can be exercised by regression fixtures.
 */
class PlaybackSessionCoordinator(
    private val snapshotStore: PlaybackSnapshotStore,
    private val servicePublisher: PlaybackServicePublisher,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    var state: PlaybackSessionState = PlaybackSessionState()
        private set

    fun onNavigationStarted(targetVideoId: String = "") {
        val previous = state
        when (PlaybackContinuityPolicy.onNavigationStarted(
            hasSession = previous.hasSession,
            playing = previous.playing,
            currentVideoId = previous.videoId,
            targetVideoId = targetVideoId
        )) {
            PlaybackNavigationDisposition.HOLD_SESSION -> {
                // Preserve title/video/play intent until the replacement media element reports.
                // Buffering freezes predicted progress and maps MediaSession to STATE_BUFFERING
                // instead of publishing a fake pause during a normal Watch transition.
                state = previous.copy(
                    buffering = previous.playing,
                    positionReportedAt = now()
                )
            }
            PlaybackNavigationDisposition.CLEAR -> {
                state = previous.copy(
                    playing = false,
                    buffering = false,
                    title = "",
                    channel = "",
                    channelUrl = "",
                    videoId = "",
                    positionMs = 0L,
                    durationMs = 0L,
                    positionReportedAt = 0L
                )
            }
        }
    }

    fun acceptBridgeUpdate(
        playing: Boolean,
        buffering: Boolean,
        title: String,
        channel: String,
        channelUrl: String,
        videoId: String,
        positionMs: Long,
        durationMs: Long,
        pageUrl: String
    ): PlaybackSessionDelta {
        val previous = state
        val normalizedDuration = durationMs.coerceAtLeast(0L)
        val normalizedPosition = PlaybackProgressPolicy.normalize(positionMs, normalizedDuration)
        val current = PlaybackSessionState(
            playing = playing,
            buffering = playing && buffering,
            hasSession = previous.hasSession || playing || videoId.isNotBlank(),
            title = title.take(240),
            channel = channel.take(180),
            channelUrl = (if (channel != previous.channel) channelUrl else channelUrl.ifBlank { previous.channelUrl }).take(1000),
            videoId = videoId.take(64),
            positionMs = normalizedPosition,
            durationMs = normalizedDuration,
            positionReportedAt = now(),
            playbackRate = previous.playbackRate,
            repeatEnabled = previous.repeatEnabled
        )
        state = current
        snapshotStore.update(
            playing = current.playing,
            title = current.title,
            channel = current.channel,
            url = pageUrl,
            videoId = current.videoId,
            positionMs = current.positionMs,
            durationMs = current.durationMs
        )
        return PlaybackSessionDelta(
            previous = previous,
            current = current,
            videoChanged = current.videoId.isNotBlank() && current.videoId != previous.videoId,
            channelChanged = current.channel.isNotBlank() && current.channel != previous.channel
        )
    }

    fun overridePosition(positionMs: Long) {
        state = state.copy(
            positionMs = PlaybackProgressPolicy.normalize(positionMs, state.durationMs),
            positionReportedAt = now()
        )
    }

    fun setPlaybackRate(rate: Float) {
        state = state.copy(playbackRate = rate.coerceIn(0.25f, 4f))
    }

    fun setRepeatEnabled(enabled: Boolean) {
        state = state.copy(repeatEnabled = enabled)
    }

    fun syncControls(playbackRate: Float, repeatEnabled: Boolean) {
        state = state.copy(
            playbackRate = playbackRate.coerceIn(0.25f, 4f),
            repeatEnabled = repeatEnabled
        )
    }

    fun estimatedPositionMs(at: Long = now()): Long {
        val current = state
        return PlaybackProgressPolicy.predict(
            positionMs = current.positionMs,
            durationMs = current.durationMs,
            reportedAtMs = current.positionReportedAt,
            nowMs = at,
            playing = current.playing && !current.buffering,
            playbackRate = current.playbackRate
        )
    }

    fun publish(backgroundControls: Boolean) {
        val current = state
        servicePublisher.publish(
            enabled = backgroundControls,
            hasSession = current.hasSession,
            playing = current.playing,
            buffering = current.buffering,
            title = current.title,
            channel = current.channel,
            videoId = current.videoId,
            positionMs = current.positionMs,
            durationMs = current.durationMs,
            playbackRate = current.playbackRate
        )
    }

    fun stopService() = servicePublisher.stop()

    fun stop(clearSnapshot: Boolean = true) {
        state = state.copy(playing = false, buffering = false, hasSession = false)
        if (clearSnapshot) snapshotStore.clear() else snapshotStore.markStopped()
        servicePublisher.stop()
    }

    fun recoverableUrl(resumeEnabled: Boolean, maxAgeMs: Long, at: Long = now()): String? {
        if (!resumeEnabled) return null
        val snapshot = snapshotStore.get()
        val age = at - snapshot.updatedAt
        if (snapshot.videoId.isBlank() || snapshot.url.isBlank() || age !in 0L..maxAgeMs) return null
        return snapshot.url.takeIf { YouTubeAdapter.isTrustedBridgeUrl(it) }
    }

    companion object {
        fun shouldResume(saved: VideoItem?, currentPositionMs: Long): Boolean {
            if (saved == null || currentPositionMs > 5_000L || saved.positionMs < 15_000L) return false
            if (saved.durationMs <= 0L) return true
            val remaining = saved.durationMs - saved.positionMs
            return remaining > 30_000L && saved.positionMs < (saved.durationMs * 0.95).toLong()
        }
    }
}
