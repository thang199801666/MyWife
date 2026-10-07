package com.example.videoshield

import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * Event-driven visibility controller for the floating mini-player controls.
 *
 * There is intentionally no repeating timer here. A single delayed hide is posted only after
 * user interaction or while playback is running. When controls are hidden, the mini seekbar can
 * stop receiving progress redraws until the next reveal.
 */
class MiniPlayerChromeController(
    private val controls: List<View>,
    private val beforeReveal: () -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var playing = false
    private var miniVisible = false
    private var closed = false

    private val hideRunnable = Runnable {
        if (!closed && miniVisible && playing) hide(animated = true)
    }

    val controlsVisible: Boolean
        get() = controls.any { it.visibility == View.VISIBLE && it.alpha > 0.05f }

    fun onMiniEntered(isPlaying: Boolean) {
        if (closed) return
        miniVisible = true
        playing = isPlaying
        show(autoHide = isPlaying)
    }

    fun onMiniExited() {
        miniVisible = false
        handler.removeCallbacks(hideRunnable)
        ++generation
        controls.forEach {
            it.animate().cancel()
            it.alpha = 1f
            it.visibility = View.VISIBLE
        }
    }

    fun onPlaybackChanged(isPlaying: Boolean) {
        if (closed || playing == isPlaying) return
        playing = isPlaying
        if (!miniVisible) return
        if (isPlaying) {
            if (controlsVisible) scheduleHide()
        } else {
            show(autoHide = false)
        }
    }

    fun onInteractionStart() {
        if (!miniVisible || closed) return
        show(autoHide = false)
    }

    fun onInteractionEnd() {
        if (!miniVisible || closed) return
        if (playing) scheduleHide() else show(autoHide = false)
    }

    fun revealForTap(): Boolean {
        if (!miniVisible || closed) return false
        if (controlsVisible) return false
        show(autoHide = playing)
        return true
    }

    fun show(autoHide: Boolean = playing) {
        if (!miniVisible || closed) return
        handler.removeCallbacks(hideRunnable)
        beforeReveal()
        val token = ++generation
        controls.forEach { view ->
            view.animate().cancel()
            view.visibility = View.VISIBLE
            if (view.alpha < 1f) {
                view.animate().alpha(1f).setDuration(120L).withEndAction {
                    if (token == generation) view.alpha = 1f
                }.start()
            } else {
                view.alpha = 1f
            }
        }
        if (autoHide) scheduleHide()
    }

    fun hide(animated: Boolean) {
        if (!miniVisible || closed) return
        handler.removeCallbacks(hideRunnable)
        val token = ++generation
        controls.forEach { view ->
            view.animate().cancel()
            if (!animated) {
                view.alpha = 0f
                view.visibility = View.INVISIBLE
            } else if (view.visibility == View.VISIBLE) {
                view.animate().alpha(0f).setDuration(160L).withEndAction {
                    if (token == generation && miniVisible && playing) {
                        view.alpha = 0f
                        view.visibility = View.INVISIBLE
                    }
                }.start()
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacksAndMessages(null)
        ++generation
        controls.forEach { it.animate().cancel() }
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, AUTO_HIDE_MS)
    }

    companion object {
        private const val AUTO_HIDE_MS = 2_200L
    }
}
