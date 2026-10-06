package com.example.videoshield

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

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

    fun previewMinimize(distancePx: Float) {
        surface.translationY = if (expanded) distancePx.coerceIn(0f, dp(160).toFloat()) else 0f
    }

    fun expand() {
        surface.translationX = 0f
        surface.translationY = 0f
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

    fun minimize() {
        surface.translationX = 0f
        surface.translationY = 0f
        state = PlayerSurfaceState.MINI
        surface.visibility = View.VISIBLE
        miniChrome.visibility = View.VISIBLE
        val available = (surface.parent as? View)?.width?.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val cardWidth = minOf(dp(240), (available - dp(24)).coerceAtLeast(dp(120)))
        surface.layoutParams = FrameLayout.LayoutParams(cardWidth, cardWidth * 9 / 16 + dp(56), Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = dp(12); bottomMargin = dp(12)
        }
        surface.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(AppTheme.elevated(context)); cornerRadius = dp(12).toFloat()
        }
        surface.clipToOutline = true
        surface.elevation = dp(12).toFloat()
        surface.requestLayout()
    }

    fun hide() {
        surface.translationX = 0f
        surface.translationY = 0f
        state = PlayerSurfaceState.HIDDEN
        miniChrome.visibility = View.GONE
        surface.visibility = View.GONE
    }

    fun restore(name: String?) {
        when (runCatching { PlayerSurfaceState.valueOf(name.orEmpty()) }.getOrNull()) {
            PlayerSurfaceState.EXPANDED -> expand()
            PlayerSurfaceState.MINI -> minimize()
            else -> hide()
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
