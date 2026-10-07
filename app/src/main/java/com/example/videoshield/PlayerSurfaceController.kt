package com.example.videoshield

import android.content.Context
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import kotlin.math.max


/**
 * Owns the native player-surface geometry independently from playback itself.
 * Keeping this state outside MainActivity makes it possible to replace the current
 * WebView-backed player without changing the browse shell or mini-player behavior.
 */
class PlayerSurfaceController(
    private val context: Context,
    private val surface: View,
    private val miniChrome: View,
    private val onTransitionSettled: () -> Unit = {}
) : AutoCloseable {
    var state: PlayerSurfaceState = PlayerSurfaceState.HIDDEN
        private set

    val visible: Boolean get() = state != PlayerSurfaceState.HIDDEN
    val expanded: Boolean get() = state == PlayerSurfaceState.EXPANDED
    val minimized: Boolean get() = state == PlayerSurfaceState.MINI

    private val ui = UiMetrics(context)
    private val settleInterpolator = PathInterpolator(0.20f, 0f, 0f, 1f)
    private var previewActive = false
    private var transitionGeneration = 0
    private var pendingPreDraw: android.view.ViewTreeObserver.OnPreDrawListener? = null
    private var transitionInFlight = false
    private var closed = false

    val transitioning: Boolean get() = transitionInFlight

    private fun transitionStarted() {
        transitionInFlight = true
    }

    private fun transitionSettled() {
        val changed = transitionInFlight
        transitionInFlight = false
        if (changed) onTransitionSettled()
    }

    /**
     * Follows the user's finger without relaying out WebView on every MOVE. Relayout of
     * a playing WebView can force YouTube to rebuild its media viewport and discard buffer.
     */
    fun previewMinimize(distancePx: Float) {
        if (closed) return
        if (!expanded || distancePx <= 0f || surface.width <= 0 || surface.height <= 0) return
        val host = surface.parent as? View ?: return
        if (host.width <= 0 || host.height <= 0) return

        surface.animate().cancel()
        previewActive = true

        val cardWidth = miniCardWidth(host.width)
        val cardHeight = cardWidth * 9 / 16
        val bottomInset = miniBottomInset()
        val targetLeft = host.width - ui.miniPlayerMargin - cardWidth
        val targetTop = host.height - bottomInset - ui.miniPlayerMargin - cardHeight
        // MOVE stays transform-only. The preview heads toward the real mini-card destination but
        // keeps a bounded uniform scale so the full watch-page WebView is never squashed into 16:9.
        // On release, the mini CSS/layout is installed once and FLIP finishes from this exact rect.
        val travel = max(
            ui.miniPlayerDragTravelMin.toFloat(),
            minOf(surface.height * 0.42f, targetTop.coerceAtLeast(0).toFloat() * 0.55f)
        )
        val transform = PlayerSurfaceTransitionPolicy.preview(
            distancePx = distancePx,
            travelPx = travel,
            sourceWidth = surface.width.toFloat(),
            sourceHeight = surface.height.toFloat(),
            targetWidth = cardWidth.toFloat(),
            targetHeight = cardHeight.toFloat(),
            targetDeltaX = targetLeft.toFloat() - surface.left,
            targetDeltaY = targetTop.toFloat() - surface.top
        )

        // Top-left pivot makes translation map directly to the destination card rectangle.
        surface.pivotX = 0f
        surface.pivotY = 0f
        surface.scaleX = transform.scaleX
        surface.scaleY = transform.scaleY
        surface.translationX = transform.translationX
        surface.translationY = transform.translationY
    }

    fun cancelMinimizePreview(animated: Boolean = true) {
        if (closed) return
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

    fun expand(animated: Boolean = false) {
        if (closed) return
        if (!minimized || !animated || surface.width <= 0 || surface.height <= 0 || !surface.isLaidOut) {
            applyExpandedLayout()
            return
        }

        val generation = ++transitionGeneration
        transitionStarted()
        surface.animate().cancel()
        miniChrome.animate().cancel()
        val oldVisual = visualRect(surface)
        resetTransforms()
        state = PlayerSurfaceStatePolicy.next(state, PlayerSurfaceAction.EXPAND)
        surface.visibility = View.VISIBLE
        surface.clipToOutline = false
        surface.elevation = 0f
        surface.setBackgroundColor(android.graphics.Color.BLACK)
        surface.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.TOP
        )
        surface.requestLayout()

        val observer = surface.viewTreeObserver
        clearPendingPreDraw()
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (surface.viewTreeObserver.isAlive) surface.viewTreeObserver.removeOnPreDrawListener(this)
                if (pendingPreDraw === this) pendingPreDraw = null
                if (generation != transitionGeneration || state != PlayerSurfaceState.EXPANDED || surface.width <= 0 || surface.height <= 0) {
                    miniChrome.visibility = View.GONE
                    miniChrome.alpha = 1f
                    resetTransforms()
                    surface.setLayerType(View.LAYER_TYPE_NONE, null)
                    transitionSettled()
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
                surface.setLayerType(View.LAYER_TYPE_HARDWARE, null)

                surface.postOnAnimation {
                    if (generation != transitionGeneration || state != PlayerSurfaceState.EXPANDED) return@postOnAnimation
                    surface.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(235L)
                        .setInterpolator(settleInterpolator)
                        .withEndAction {
                            if (generation == transitionGeneration && expanded) {
                                resetTransforms()
                                surface.setLayerType(View.LAYER_TYPE_NONE, null)
                                transitionSettled()
                            }
                        }
                        .start()
                    miniChrome.animate()
                        .alpha(0f)
                        .setDuration(110L)
                        .withEndAction {
                            if (generation == transitionGeneration && expanded) {
                                miniChrome.visibility = View.GONE
                                miniChrome.alpha = 1f
                            }
                        }
                        .start()
                }
                return false
            }
        }
        pendingPreDraw = listener
        observer.addOnPreDrawListener(listener)
    }

    /**
     * Collapses the expanded surface with a FLIP transition. The current visual rectangle
     * (including an in-progress drag) is captured first; after applying the mini layout the
     * inverse transform makes the first mini frame appear at exactly the same place, then it
     * settles into the bottom-right card. This avoids the old snap between two layouts.
     */
    fun minimize(animated: Boolean = false) {
        if (closed) return
        if (!visible) return
        if (!expanded || !animated || surface.width <= 0 || surface.height <= 0 || !surface.isLaidOut) {
            applyMiniLayout()
            return
        }

        val generation = ++transitionGeneration
        transitionStarted()
        surface.animate().cancel()
        miniChrome.animate().cancel()
        val oldVisual = visualRect(surface)
        previewActive = false

        // Reset transforms before changing layout; the inverse FLIP transform is installed
        // in OnPreDraw, so no untransformed mini frame is ever presented to the user.
        resetTransforms()
        surface.setLayerType(View.LAYER_TYPE_NONE, null)
        state = PlayerSurfaceStatePolicy.next(state, PlayerSurfaceAction.MINIMIZE)
        surface.visibility = View.VISIBLE
        miniChrome.visibility = View.VISIBLE
        miniChrome.alpha = 0f
        applyMiniDecor()
        surface.layoutParams = miniLayoutParams()
        surface.requestLayout()

        val observer = surface.viewTreeObserver
        clearPendingPreDraw()
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (surface.viewTreeObserver.isAlive) surface.viewTreeObserver.removeOnPreDrawListener(this)
                if (pendingPreDraw === this) pendingPreDraw = null
                if (generation != transitionGeneration || state != PlayerSurfaceState.MINI || surface.width <= 0 || surface.height <= 0) {
                    miniChrome.alpha = 1f
                    resetTransforms()
                    transitionSettled()
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
                surface.setLayerType(View.LAYER_TYPE_HARDWARE, null)

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
                        .withEndAction {
                            if (generation == transitionGeneration && minimized) {
                                resetTransforms()
                                surface.setLayerType(View.LAYER_TYPE_NONE, null)
                                transitionSettled()
                            }
                        }
                        .start()
                    miniChrome.animate()
                        .alpha(1f)
                        .setStartDelay(90L)
                        .setDuration(150L)
                        .start()
                }
                return false
            }
        }
        pendingPreDraw = listener
        observer.addOnPreDrawListener(listener)
    }

    fun hide() {
        if (closed) return
        clearPendingPreDraw()
        ++transitionGeneration
        transitionInFlight = false
        surface.animate().cancel()
        miniChrome.animate().cancel()
        resetTransforms()
        surface.setLayerType(View.LAYER_TYPE_NONE, null)
        miniChrome.alpha = 1f
        state = PlayerSurfaceStatePolicy.next(state, PlayerSurfaceAction.HIDE)
        miniChrome.visibility = View.GONE
        surface.visibility = View.GONE
    }

    fun restore(name: String?) {
        // Restoration must not depend on the current visibility. A freshly-created controller is
        // HIDDEN, so routing MINI through minimize() would be ignored and lose the saved surface.
        when (PlayerSurfaceStatePolicy.restore(name)) {
            PlayerSurfaceState.EXPANDED -> applyExpandedLayout()
            PlayerSurfaceState.MINI -> applyMiniLayout()
            PlayerSurfaceState.HIDDEN -> hide()
        }
    }


    private fun applyExpandedLayout() {
        clearPendingPreDraw()
        ++transitionGeneration
        transitionInFlight = false
        surface.animate().cancel()
        miniChrome.animate().cancel()
        resetTransforms()
        surface.setLayerType(View.LAYER_TYPE_NONE, null)
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

    private fun applyMiniLayout() {
        clearPendingPreDraw()
        ++transitionGeneration
        transitionInFlight = false
        surface.animate().cancel()
        miniChrome.animate().cancel()
        resetTransforms()
        surface.setLayerType(View.LAYER_TYPE_NONE, null)
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
            cornerRadius = ui.cardRadius.toFloat()
        }
        surface.clipToOutline = true
        surface.elevation = ui.miniPlayerElevation.toFloat()
    }

    private fun miniLayoutParams(): FrameLayout.LayoutParams {
        val available = (surface.parent as? View)?.width?.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val cardWidth = miniCardWidth(available)
        return FrameLayout.LayoutParams(cardWidth, cardWidth * 9 / 16, Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = ui.miniPlayerMargin
            // Keep the floating card above the overlay bottom navigation instead of letting it
            // settle underneath chrome that lives outside browserContainer.
            bottomMargin = ui.miniPlayerMargin + miniBottomInset()
        }
    }

    private fun miniCardWidth(availableWidth: Int): Int =
        minOf(ui.miniPlayerWidth, (availableWidth - ui.miniPlayerMargin * 2).coerceAtLeast(ui.miniPlayerMinWidth))

    private fun miniBottomInset(): Int =
        (surface as? PlayerSurfaceLayout)?.reservedBottomInsetPx?.coerceAtLeast(0) ?: 0

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

    private fun clearPendingPreDraw() {
        val listener = pendingPreDraw ?: return
        pendingPreDraw = null
        val observer = surface.viewTreeObserver
        if (observer.isAlive) runCatching { observer.removeOnPreDrawListener(listener) }
    }

    override fun close() {
        if (closed) return
        closed = true
        ++transitionGeneration
        transitionInFlight = false
        clearPendingPreDraw()
        surface.animate().cancel()
        miniChrome.animate().cancel()
        surface.setLayerType(View.LAYER_TYPE_NONE, null)
        resetTransforms()
    }

}
