package com.example.videoshield

/** Snapshot used to seed player commands while a replacement renderer is warming up. */
data class PlaybackRecoveryHandoff(
    val playing: Boolean,
    val positionMs: Long,
    val playbackRate: Float,
    val repeatEnabled: Boolean
)

data class PlaybackRecoveryActivation(
    val desiredPlaying: Boolean,
    val targetPositionMs: Long,
    val seekApplied: Boolean,
    val playbackRate: Float,
    val repeatEnabled: Boolean
)

/**
 * Stable playback command boundary whose concrete WebView backend can be swapped after a
 * renderer process dies. During renderer recovery, user commands are coalesced in native state
 * instead of being dropped or fired into a half-ready WebView. Once the bridge for the expected
 * media element is alive, [activateRecovery] applies one deterministic handoff: controls, seek,
 * then transport intent. The newest user command always wins over the crash snapshot.
 */
class RebindablePlaybackBackend(initial: PlaybackBackend) : PlaybackBackend {
    @Volatile
    private var delegate: PlaybackBackend? = initial

    @Volatile
    private var commandReady = true

    private var recoverySeed: PlaybackRecoveryHandoff? = null
    private var transportOverride: Boolean? = null
    private var positionOverrideMs: Long? = null
    private var rateOverride: Float? = null
    private var repeatOverride: Boolean? = null

    val attached: Boolean get() = delegate != null
    val recovering: Boolean get() = synchronized(this) { recoverySeed != null }
    val recoveryDesiredPlaying: Boolean?
        get() = synchronized(this) { transportOverride ?: recoverySeed?.playing }

    @Synchronized
    fun beginRecovery(seed: PlaybackRecoveryHandoff) {
        delegate = null
        commandReady = false
        recoverySeed = seed.copy(
            positionMs = seed.positionMs.coerceAtLeast(0L),
            playbackRate = seed.playbackRate.coerceIn(0.25f, 4f)
        )
        transportOverride = null
        positionOverrideMs = null
        rateOverride = null
        repeatOverride = null
    }

    /** Attach a new renderer host. Recovery commands stay gated until the first valid bridge. */
    @Synchronized
    fun rebind(next: PlaybackBackend, ready: Boolean = true) {
        delegate = next
        commandReady = ready
    }

    /** Detach without discarding the recovery handoff; used by terminal fallback paths. */
    @Synchronized
    fun detach() {
        delegate = null
        commandReady = false
    }

    @Synchronized
    fun cancelRecovery() {
        recoverySeed = null
        transportOverride = null
        positionOverrideMs = null
        rateOverride = null
        repeatOverride = null
        commandReady = delegate != null
    }

    /**
     * Make the replacement backend authoritative. This is intentionally one-shot and does not use
     * delayed retries: renderer-side rate/repeat policies already own their sparse retry points.
     */
    fun activateRecovery(reportedPositionMs: Long): PlaybackRecoveryActivation? {
        val payload = synchronized(this) {
            val seed = recoverySeed ?: return null
            val target = delegate ?: return null
            val position = (positionOverrideMs ?: seed.positionMs).coerceAtLeast(0L)
            val rate = (rateOverride ?: seed.playbackRate).coerceIn(0.25f, 4f)
            val repeat = repeatOverride ?: seed.repeatEnabled
            val playing = transportOverride ?: seed.playing
            val forceSeek = positionOverrideMs != null
            val seek = forceSeek || PlayerRendererRestorePolicy.shouldSeek(position, reportedPositionMs)

            recoverySeed = null
            transportOverride = null
            positionOverrideMs = null
            rateOverride = null
            repeatOverride = null
            commandReady = true
            RecoveryPayload(target, playing, position, seek, rate, repeat)
        }

        // Keep ordering deterministic: controls before seek, transport last. This avoids starting
        // playback at the wrong speed/position for a frame while the replacement renderer settles.
        payload.backend.setPlaybackRate(payload.rate)
        payload.backend.setRepeatEnabled(payload.repeat)
        if (payload.seek) payload.backend.seekToMs(payload.positionMs)
        if (payload.playing) payload.backend.play() else payload.backend.pause()
        return PlaybackRecoveryActivation(
            desiredPlaying = payload.playing,
            targetPositionMs = payload.positionMs,
            seekApplied = payload.seek,
            playbackRate = payload.rate,
            repeatEnabled = payload.repeat
        )
    }

    override fun play() = transport(true) { it.play() }
    override fun pause() = transport(false) { it.pause() }

    override fun toggle() {
        val target = synchronized(this) {
            if (commandReady) delegate else {
                val baseline = transportOverride ?: recoverySeed?.playing ?: false
                transportOverride = !baseline
                null
            }
        }
        target?.toggle()
    }

    override fun seekBack() = seekRelative(-10_000L) { it.seekBack() }
    override fun seekForward() = seekRelative(10_000L) { it.seekForward() }

    override fun seekToMs(positionMs: Long) {
        val safe = positionMs.coerceAtLeast(0L)
        val target = synchronized(this) {
            if (commandReady) delegate else {
                positionOverrideMs = safe
                null
            }
        }
        target?.seekToMs(safe)
    }

    override fun setRepeatEnabled(enabled: Boolean) {
        val target = synchronized(this) {
            if (commandReady) delegate else {
                repeatOverride = enabled
                null
            }
        }
        target?.setRepeatEnabled(enabled)
    }

    override fun setPlaybackRate(rate: Float) {
        val safe = rate.coerceIn(0.25f, 4f)
        val target = synchronized(this) {
            if (commandReady) delegate else {
                rateOverride = safe
                null
            }
        }
        target?.setPlaybackRate(safe)
    }

    override fun setCommunitySegments(videoId: String, segments: List<CommunitySegment>) {
        val target = synchronized(this) { if (commandReady) delegate else null }
        target?.setCommunitySegments(videoId, segments)
    }

    override fun clearCommunitySegments() {
        val target = synchronized(this) { if (commandReady) delegate else null }
        target?.clearCommunitySegments()
    }

    private inline fun transport(playing: Boolean, action: (PlaybackBackend) -> Unit) {
        val target = synchronized(this) {
            if (commandReady) delegate else {
                transportOverride = playing
                null
            }
        }
        target?.let(action)
    }

    private inline fun seekRelative(deltaMs: Long, action: (PlaybackBackend) -> Unit) {
        val target = synchronized(this) {
            if (commandReady) delegate else {
                val baseline = positionOverrideMs ?: recoverySeed?.positionMs ?: 0L
                positionOverrideMs = (baseline + deltaMs).coerceAtLeast(0L)
                null
            }
        }
        target?.let(action)
    }

    private data class RecoveryPayload(
        val backend: PlaybackBackend,
        val playing: Boolean,
        val positionMs: Long,
        val seek: Boolean,
        val rate: Float,
        val repeat: Boolean
    )
}
