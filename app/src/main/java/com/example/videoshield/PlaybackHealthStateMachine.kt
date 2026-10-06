package com.example.videoshield

enum class PlaybackHealthState {
    IDLE,
    NAVIGATING,
    HEALTHY,
    OFFLINE,
    STALLED,
    RECOVERING,
    FAILED
}

data class PlaybackHealthSnapshot(
    val state: PlaybackHealthState,
    val reason: String = "",
    val recoveryAttempt: Int = 0,
    val changedAt: Long = System.currentTimeMillis()
)

/**
 * Small deterministic state machine that sits above WebView/network/recovery callbacks.
 * It does not perform recovery itself; it describes the current playback health so UI
 * and diagnostics do not infer state independently from unrelated callbacks.
 */
class PlaybackHealthStateMachine(
    private val onChanged: (PlaybackHealthSnapshot) -> Unit
) {
    var snapshot: PlaybackHealthSnapshot = PlaybackHealthSnapshot(PlaybackHealthState.IDLE)
        private set

    private var online = true

    fun setOnline(value: Boolean, hasSession: Boolean) {
        online = value
        if (!value) {
            transition(PlaybackHealthState.OFFLINE, "No validated internet connection")
        } else if (snapshot.state == PlaybackHealthState.OFFLINE) {
            transition(if (hasSession) PlaybackHealthState.STALLED else PlaybackHealthState.IDLE, if (hasSession) "Connection restored" else "")
        }
    }

    fun navigationStarted() {
        if (!online) transition(PlaybackHealthState.OFFLINE, "Waiting for network")
        else transition(PlaybackHealthState.NAVIGATING)
    }

    fun pageReady(isWatchPage: Boolean) {
        if (!online) return
        if (!isWatchPage && snapshot.state == PlaybackHealthState.NAVIGATING) {
            transition(PlaybackHealthState.IDLE)
        }
    }

    fun heartbeat(playing: Boolean, videoId: String) {
        if (!online) return
        if (videoId.isNotBlank()) {
            transition(PlaybackHealthState.HEALTHY, if (playing) "Playback active" else "Player ready")
        }
    }

    fun mainFrameError(reason: String, recoverable: Boolean) {
        if (!online) {
            transition(PlaybackHealthState.OFFLINE, "Waiting for network")
            return
        }
        val safeReason = reason.ifBlank { "Main page failed to load" }
        transition(if (recoverable) PlaybackHealthState.STALLED else PlaybackHealthState.FAILED, safeReason)
    }

    fun recoveryStarted(reason: String, attempt: Int) {
        if (!online) return
        transition(PlaybackHealthState.RECOVERING, reason, attempt)
    }

    fun recoveryExhausted(reason: String) {
        if (!online) return
        transition(PlaybackHealthState.FAILED, reason.ifBlank { "Automatic recovery exhausted" })
    }

    fun userRetry() {
        if (!online) {
            transition(PlaybackHealthState.OFFLINE, "Waiting for network")
        } else {
            transition(PlaybackHealthState.NAVIGATING, "Retrying")
        }
    }

    fun reset() = transition(if (online) PlaybackHealthState.IDLE else PlaybackHealthState.OFFLINE)

    private fun transition(state: PlaybackHealthState, reason: String = "", attempt: Int = 0) {
        val current = snapshot
        if (current.state == state && current.reason == reason && current.recoveryAttempt == attempt) return
        snapshot = PlaybackHealthSnapshot(state, reason.take(240), attempt.coerceAtLeast(0))
        onChanged(snapshot)
    }
}
