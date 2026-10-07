package com.example.videoshield

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Bounded recovery orchestration for active-network loss/handoff.
 *
 * Stage 1 reasserts the existing media element after the new network settles. Only if a healthy
 * bridge heartbeat does not return does it ask the existing PlaybackRecoveryController to perform
 * a reload. This prevents reconnect storms and preserves the last known playback position.
 */
class NetworkRecoveryCoordinator(
    private val enabled: () -> Boolean,
    private val checkpointProvider: () -> NetworkRecoveryCheckpoint?,
    private val onSoftRecover: (NetworkRecoveryCheckpoint) -> Unit,
    private val onEscalate: (NetworkRecoveryCheckpoint, Int) -> Unit,
    private val nowElapsed: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastLink: NetworkLinkState? = null
    private var activeLink = NetworkLinkState.OFFLINE
    private var checkpoint: NetworkRecoveryCheckpoint? = null
    private var generation = 0L
    private var escalationCount = 0
    private var restoreIssued = false
    private var restoreNotBeforeElapsedMs = 0L

    fun onLinkState(state: NetworkLinkState) {
        val previous = lastLink
        lastLink = state
        activeLink = state
        val linkChanged = previous?.let {
            it.online && state.online && it.networkId.isNotBlank() && state.networkId.isNotBlank() && it.networkId != state.networkId
        } == true
        val reconnected = previous?.online == false && state.online

        // Metering/capability updates on the same validated active network are quality-policy
        // events, not recovery boundaries. Do not let them cancel an in-flight reconnect verify.
        val recoveryBoundary = !state.online || reconnected || linkChanged
        if (!recoveryBoundary) return

        generation++
        cancelScheduled()

        if (!state.online) {
            checkpointProvider()?.let { checkpoint = it }
            restoreIssued = false
            escalationCount = 0
            return
        }

        if (linkChanged) {
            // Seamless Wi-Fi/cellular handoffs may never report an offline state. Capture a point
            // before reasserting so a broken media socket cannot silently lose playback position.
            checkpointProvider()?.let { checkpoint = it }
            restoreIssued = false
            escalationCount = 0
        }

        if ((reconnected || linkChanged) && checkpoint != null) scheduleSoftRecovery(linkChanged)
    }

    /** Called on Activity resume in case the reconnect occurred while runtime observers were paused. */
    fun onForeground() {
        if (activeLink.online && checkpoint != null && enabled()) {
            generation++
            cancelScheduled()
            scheduleSoftRecovery(activeLinkChanged = false)
        }
    }

    /** Keep the checkpoint, but do not leave delayed recovery work queued while UI is backgrounded. */
    fun onBackground() {
        generation++
        cancelScheduled()
    }

    fun onNavigationStarted(targetVideoId: String) {
        val current = checkpoint ?: return
        if (targetVideoId.isNotBlank() && targetVideoId != current.videoId) clear()
    }

    /**
     * Feed bridge updates into the coordinator. Returns at most one restoration instruction per
     * recovery stage; escalation resets that allowance after a page reload.
     */
    fun onBridgeState(videoId: String, playing: Boolean, positionMs: Long): NetworkRestoreInstruction? {
        val current = checkpoint ?: return null
        if (videoId.isNotBlank() && videoId != current.videoId) {
            clear()
            return null
        }
        val now = nowElapsed()
        if (NetworkRecoveryPolicy.healthy(current, videoId, playing, positionMs, now)) {
            clear()
            return null
        }
        if (restoreNotBeforeElapsedMs > 0L && now < restoreNotBeforeElapsedMs) return null
        if (!activeLink.online || !enabled() || restoreIssued || videoId != current.videoId) return null
        restoreIssued = true
        return NetworkRecoveryPolicy.instruction(current, positionMs, playing)
    }

    fun hasCheckpoint(): Boolean = checkpoint != null

    fun dispose() {
        generation++
        cancelScheduled()
        checkpoint = null
        lastLink = null
    }

    private fun scheduleSoftRecovery(activeLinkChanged: Boolean) {
        if (!activeLink.online || checkpoint == null || !enabled()) return
        val localGeneration = generation
        val delay = NetworkRecoveryPolicy.reconnectSettleDelayMs(activeLink.online, activeLink.metered, activeLinkChanged)
        restoreNotBeforeElapsedMs = nowElapsed() + delay
        handler.postDelayed({
            if (localGeneration != generation || !activeLink.online || !enabled()) return@postDelayed
            val current = checkpoint ?: return@postDelayed
            if (nowElapsed() - current.capturedAtElapsedMs > NetworkRecoveryPolicy.MAX_CHECKPOINT_AGE_MS) {
                clear()
                return@postDelayed
            }
            restoreNotBeforeElapsedMs = 0L
            // Soft stage only nudges the existing media element. Keep restoreIssued=false so
            // the first real post-reconnect bridge sample can decide whether a seek is needed.
            restoreIssued = false
            onSoftRecover(current)
            scheduleVerification(localGeneration)
        }, delay)
    }

    private fun scheduleVerification(localGeneration: Long) {
        val delay = NetworkRecoveryPolicy.verificationDelayMs(escalationCount)
        handler.postDelayed({
            if (localGeneration != generation || !activeLink.online || !enabled()) return@postDelayed
            val current = checkpoint ?: return@postDelayed
            if (escalationCount >= NetworkRecoveryPolicy.MAX_ESCALATIONS) return@postDelayed
            escalationCount++
            restoreIssued = false
            onEscalate(current, escalationCount)
            if (escalationCount < NetworkRecoveryPolicy.MAX_ESCALATIONS) {
                scheduleVerification(localGeneration)
            }
        }, delay)
    }

    private fun clear() {
        generation++
        cancelScheduled()
        checkpoint = null
        restoreIssued = false
        restoreNotBeforeElapsedMs = 0L
        escalationCount = 0
    }

    private fun cancelScheduled() {
        handler.removeCallbacksAndMessages(null)
    }
}
