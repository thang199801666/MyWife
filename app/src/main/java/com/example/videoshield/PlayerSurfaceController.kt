package com.example.videoshield

import android.content.Context
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import kotlin.math.max


enum class PlayerSurfaceState {
    HIDDEN,
    EXPANDED,
    MINI
}

/**
 * Owns the native player-surface geometry independently from playback itself.
 * Keeping this state outside MainActivity makes it possible to replace the current
 * WebView-backed player without changing the browse shell or mini-player behavior.
 */
class PlayerSurfaceController(
    private val context: Context,
    private val surface: View,
    private val miniChrome: View
) {
    var state: PlayerSurfaceState = PlayerSurfaceState.HIDDEN
        private set

    val visible: Boolean get() = state != PlayerSurfaceState.HIDDEN
    val expanded: Boolean get() = state == PlayerSurfaceState.EXPANDED
    val minimized: Boolean get() = state == PlayerSurfaceState.MINI

    private val settleInterpolator = PathInterpolator(0.20f, 0f, 0f, 1f)
    private var previewActive = false
    private var transitionGeneration = 0

    /**
     * Follows the user's finger without relaying out WebView on every MOVE. Relayout of
     * a playing WebView can force YouTube to rebuild its media viewport and discard buffer.
     */
    fun previewMinimize(distancePx: Float) {
        if (!expanded || distancePx <= 0f || surface.width <= 0 || surface.height <= 0) return
        surface.animate().cancel()
        previewActive = true
        val travel = max(dp(180).toFloat(), surface.height * 0.32f)
        val progress = (distancePx / travel).coerceIn(0f, 1f)
        val scale = 1f - 0.075f * progress
        surface.pivotX = surface.width * 0.5f
        surface.pivotY = surface.height * 0.5f
        surface.scaleX = scale
        surface.scaleY = scale
        // Keep the surface visually attached to the finger while allowing some resistance.
        surface.translationY = distancePx * (0.62f - 0.12f * progress)
        surface.translationX = dp(10) * progress
    }

    fun cancelMinimizePreview(animated: Boolean = true) {
        if (!expanded) {
            resetTransforms()
            return
        }
        if (!previewActive && surface.translationY == 0f && surface.scaleX == 1f && surface.scaleY == 1f) return
        previewActive = false
        surface.animate().cancel()
        if (!animated) {
            resetTransforms()
            return
        }
        surface.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(170L)
            .setInterpolator(settleInterpolator)
            .withEndAction { if (expanded) resetTransforms() }
            .start()
    }

    fun expand() {
        ++transitionGeneration
        surface.animate().cancel()
        miniChrome.animate().cancel()
        resetTransforms()
        miniChrome.alpha = 1f
        surface.clipToOutline = false
        surface.elevation = 0f
        surface.setBackgroundColor(android.graphics.Color.BLACK)
        state = PlayerSurfaceState.EXPANDED
        surface.visibility = View.VISIBLE
        miniChrome.visibility = View.GONE
        surface.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.TOP
        )
        surface.requestLayout()
    }

    /**
     * Collapses the expanded surface with a FLIP transition. The current visual rectangle
     * (including an in-progress drag) is captured first; after applying the mini layout the
     * inverse transform makes the first mini frame appear at exactly the same place, then it
     * settles into the bottom-right card. This avoids the old snap between two layouts.
     */
    fun minimize(animated: Boolean = false) {
        if (!visible) return
        if (!expanded || !animated || surface.width <= 0 || surface.height <= 0 || !surface.isLaidOut) {
            applyMiniLayout()
            return
        }

        val generation = ++transitionGeneration
        surface.animate().cancel()
        miniChrome.animate().cancel()
        val oldVisual = visualRect(surface)
        previewActive = false

        // Reset transforms before changing layout; the inverse FLIP transform is installed
        // in OnPreDraw, so no untransformed mini frame is ever presented to the user.
        resetTransforms()
        state = PlayerSurfaceState.MINI
        surface.visibility = View.VISIBLE
        miniChrome.visibility = View.VISIBLE
        miniChrome.alpha = 0f
        applyMiniDecor()
        surface.layoutParams = miniLayoutParams()
        surface.requestLayout()

        val observer = surface.viewTreeObserver
        observer.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (surface.viewTreeObserver.isAlive) surface.viewTreeObserver.removeOnPreDrawListener(this)
                if (generation != transitionGeneration || state != PlayerSurfaceState.MINI || surface.width <= 0 || surface.height <= 0) {
                    miniChrome.alpha = 1f
                    resetTransforms()
                    return true
                }

                val targetLeft = surface.left.toFloat()
                val targetTop = surface.top.toFloat()
                val newWidth = surface.width.toFloat().coerceAtLeast(1f)
                val newHeight = surface.height.toFloat().coerceAtLeast(1f)
                surface.pivotX = 0f
                surface.pivotY = 0f
                surface.scaleX = (oldVisual.width() / newWidth).coerceAtLeast(0.01f)
                surface.scaleY = (oldVisual.height() / newHeight).coerceAtLeast(0.01f)
                surface.translationX = oldVisual.left - targetLeft
                surface.translationY = oldVisual.top - targetTop

                // Cancel this draw so the first visible mini-layout frame already carries
                // the inverse transform above. Animation starts on the following frame.
                surface.postOnAnimation {
                    if (generation != transitionGeneration || state != PlayerSurfaceState.MINI) return@postOnAnimation
                    surface.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(270L)
                        .setInterpolator(settleInterpolator)
                        .withEndAction { if (generation == transitionGeneration && minimized) resetTransforms() }
                        .start()
                    miniChrome.animate()
                        .alpha(1f)
                        .setStartDelay(90L)
                        .setDuration(150L)
                        .start()
                }
                return false
            }
        })
    }

    fun hide() {
        ++transitionGeneration
        surface.animate().cancel()
        miniChrome.animate().cancel()
        resetTransforms()
        miniChrome.alpha = 1f
        state = PlayerSurfaceState.HIDDEN
        miniChrome.visibility = View.GONE
        surface.visibility = View.GONE
    }

    fun restore(name: String?) {
        when (runCatching { PlayerSurfaceState.valueOf(name.orEmpty()) }.getOrNull()) {
            PlayerSurfaceState.EXPANDED -> expand()
            PlayerSurfaceState.MINI -> minimize(animated = false)
            else -> hide()
        }
    }

    private fun applyMiniLayout() {
        ++transitionGeneration
        surface.animate().cancel()
        miniChrome.animate().cancel()
        resetTransforms()
        state = PlayerSurfaceState.MINI
        surface.visibility = View.VISIBLE
        miniChrome.visibility = View.VISIBLE
        miniChrome.alpha = 1f
        applyMiniDecor()
        surface.layoutParams = miniLayoutParams()
        surface.requestLayout()
    }

    private fun applyMiniDecor() {
        surface.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(AppTheme.elevated(context))
            cornerRadius = dp(12).toFloat()
        }
        surface.clipToOutline = true
        surface.elevation = dp(12).toFloat()
    }

    private fun miniLayoutParams(): FrameLayout.LayoutParams {
        val available = (surface.parent as? View)?.width?.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val cardWidth = minOf(dp(240), (available - dp(24)).coerceAtLeast(dp(120)))
        return FrameLayout.LayoutParams(cardWidth, cardWidth * 9 / 16 + dp(56), Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = dp(12)
            bottomMargin = dp(12)
        }
    }

    private fun visualRect(view: View): RectF {
        val sx = view.scaleX.takeIf { it.isFinite() && it > 0f } ?: 1f
        val sy = view.scaleY.takeIf { it.isFinite() && it > 0f } ?: 1f
        val px = view.pivotX
        val py = view.pivotY
        val left = view.left + view.translationX + px * (1f - sx)
        val top = view.top + view.translationY + py * (1f - sy)
        return RectF(left, top, left + view.width * sx, top + view.height * sy)
    }

    private fun resetTransforms() {
        surface.pivotX = surface.width * 0.5f
        surface.pivotY = surface.height * 0.5f
        surface.translationX = 0f
        surface.translationY = 0f
        surface.scaleX = 1f
        surface.scaleY = 1f
        surface.alpha = 1f
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
