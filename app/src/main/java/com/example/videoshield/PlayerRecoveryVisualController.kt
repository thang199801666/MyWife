package com.example.videoshield

import android.content.ComponentCallbacks2
import android.view.View
import android.widget.ImageView

/**
 * Keeps a lightweight native poster above a replacement playback WebView until the expected
 * renderer bridge is ready. The thumbnail loader is created lazily only after a renderer loss;
 * normal playback/startup pays no extra thread, bitmap or network cost.
 */
class PlayerRecoveryVisualController(
    private val overlay: View,
    private val thumbnail: ImageView
) : AutoCloseable {
    private var thumbnails: VideoThumbnailLoader? = null
    private var generation = 0
    private var videoId = ""
    private var closed = false

    val visible: Boolean
        get() = !closed && overlay.visibility == View.VISIBLE && overlay.alpha > 0.01f

    fun show(targetVideoId: String) {
        if (closed) return
        val token = ++generation
        overlay.animate().cancel()
        overlay.alpha = 1f
        overlay.visibility = View.VISIBLE

        if (targetVideoId != videoId) {
            videoId = targetVideoId
            thumbnail.setImageDrawable(null)
        }
        if (targetVideoId.isNotBlank()) {
            val loader = thumbnails ?: VideoThumbnailLoader().also { thumbnails = it }
            loader.bind(thumbnail, targetVideoId)
        }
        // Keep token referenced so a stale fade-out can never hide a newly-shown recovery visual.
        overlay.tag = token
    }

    fun hide(animated: Boolean = true) {
        if (closed || overlay.visibility != View.VISIBLE) return
        val token = ++generation
        overlay.animate().cancel()
        overlay.tag = token
        if (!animated) {
            overlay.alpha = 1f
            overlay.visibility = View.GONE
            thumbnail.setImageDrawable(null)
            return
        }
        overlay.animate()
            .alpha(0f)
            .setDuration(140L)
            .withEndAction {
                if (!closed && generation == token) {
                    overlay.visibility = View.GONE
                    overlay.alpha = 1f
                    thumbnail.setImageDrawable(null)
                }
            }
            .start()
    }

    fun trimMemory(level: Int) {
        if (closed) return
        thumbnails?.trimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && !visible) {
            thumbnail.setImageDrawable(null)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        ++generation
        overlay.animate().cancel()
        overlay.visibility = View.GONE
        overlay.alpha = 1f
        thumbnail.setImageDrawable(null)
        thumbnails?.close()
        thumbnails = null
    }
}
