package com.example.videoshield

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * Floating mini-player drag/tap host.
 *
 * The surface itself owns drag/fling geometry while overlay transport controls keep their own
 * gestures. No layout is requested during MOVE; only translation/scale are changed so a playing
 * WebView does not rebuild its media viewport while the card follows the finger.
 */
class PlayerSurfaceLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    var isMini: (() -> Boolean)? = null
    var onVideoTap: (() -> Unit)? = null
    var onFlickExpand: (() -> Unit)? = null
    var onFlickDismiss: (() -> Unit)? = null
    var onInteractionStart: (() -> Unit)? = null
    var onInteractionEnd: (() -> Unit)? = null
    /** Space occupied by overlay app chrome below the floating card. */
    var reservedBottomInsetPx: Int = 0

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minFlingVelocity = maxOf(
        ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4f,
        650f * resources.displayMetrics.density
    )
    private val settleInterpolator = PathInterpolator(0.20f, 0f, 0f, 1f)

    private var startX = 0f
    private var startY = 0f
    private var initialX = 0f
    private var initialY = 0f
    private var moved = false
    private var canceled = false
    private var hardwareLayerActive = false
    private var velocityTracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isMini?.invoke() != true || event.actionMasked != MotionEvent.ACTION_DOWN) return false
        // Let visible transport controls own the gesture. Hidden controls are INVISIBLE and are
        // therefore intentionally not hit-tested; tapping the picture can reveal them.
        if (isOverlayControlHit(event.rawX, event.rawY)) return false
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isMini?.invoke() != true && event.actionMasked == MotionEvent.ACTION_DOWN) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().cancel()
                recycleVelocityTracker()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                startX = event.rawX
                startY = event.rawY
                initialX = translationX
                initialY = translationY
                moved = false
                canceled = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                canceled = true
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (!canceled && isMini?.invoke() == true) {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (!moved && hypot(dx, dy) > touchSlop) {
                        moved = true
                        beginInteractiveTransform()
                        onInteractionStart?.invoke()
                    }
                    val host = parent as? View
                    if (moved && host != null) {
                        val bounds = movementBounds(host)
                        if (bounds != null) {
                            translationX = (initialX + dx).coerceIn(bounds[0], bounds[1])
                            translationY = (initialY + dy).coerceIn(bounds[2], bounds[3])
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!moved && !canceled) {
                    performClick()
                    onVideoTap?.invoke()
                    finishInteractiveTransform(animated = true)
                } else if (moved && !canceled && isMini?.invoke() == true) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    val vy = velocityTracker?.yVelocity ?: 0f
                    when (MiniPlayerGesturePolicy.releaseAction(vx, vy, minFlingVelocity)) {
                        MiniPlayerReleaseAction.DISMISS -> animateFlickDismiss()
                        MiniPlayerReleaseAction.EXPAND -> animateFlickExpand()
                        MiniPlayerReleaseAction.SNAP -> snapToNearestCorner()
                    }
                } else {
                    finishInteractiveTransform(animated = true)
                }
                recycleVelocityTracker()
                onInteractionEnd?.invoke()
            }
            MotionEvent.ACTION_CANCEL -> {
                translationX = initialX
                translationY = initialY
                finishInteractiveTransform(animated = true)
                recycleVelocityTracker()
                parent?.requestDisallowInterceptTouchEvent(false)
                onInteractionEnd?.invoke()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun isOverlayControlHit(rawX: Float, rawY: Float): Boolean {
        val ids = intArrayOf(R.id.miniPlayPauseButton, R.id.miniCloseButton, R.id.miniPlayerProgress)
        val location = IntArray(2)
        for (id in ids) {
            val child = findViewById<View>(id) ?: continue
            if (child.visibility != View.VISIBLE || !child.isEnabled || child.alpha <= 0.05f) continue
            child.getLocationOnScreen(location)
            if (rawX >= location[0] && rawX <= location[0] + child.width &&
                rawY >= location[1] && rawY <= location[1] + child.height) return true
        }
        return false
    }

    /**
     * Re-clamps a dragged mini-player after host geometry changes (rotation, split-screen,
     * display resize). This does not request a WebView relayout during normal drag; it runs only
     * from the Activity configuration boundary.
     */
    fun settleInsideHostBounds() {
        if (isMini?.invoke() != true) return
        val host = parent as? View ?: return
        val bounds = movementBounds(host) ?: return
        animate().cancel()
        translationX = translationX.coerceIn(bounds[0], bounds[1])
        translationY = translationY.coerceIn(bounds[2], bounds[3])
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        releaseHardwareLayer()
    }

    private fun movementBounds(host: View): FloatArray? {
        if (host.width <= 0 || host.height <= 0 || width <= 0 || height <= 0) return null
        val margin = 8f * resources.displayMetrics.density
        val minX = -left + margin
        val maxX = host.width - left - width - margin
        val minY = -top + margin
        val maxY = host.height - top - height - margin - reservedBottomInsetPx.coerceAtLeast(0)
        if (minX > maxX || minY > maxY) return null
        return floatArrayOf(minX, maxX, minY, maxY)
    }

    private fun beginInteractiveTransform() {
        if (!hardwareLayerActive) {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            hardwareLayerActive = true
        }
        animate().cancel()
        animate().scaleX(0.975f).scaleY(0.975f).setDuration(70L).start()
    }

    /** Settles the card to one of all four corners. */
    private fun snapToNearestCorner() {
        val host = parent as? View ?: return finishInteractiveTransform(animated = true)
        val bounds = movementBounds(host) ?: return finishInteractiveTransform(animated = true)
        val target = MiniPlayerGesturePolicy.nearestCorner(translationX, translationY, bounds)
        ensureHardwareLayer()
        animate().cancel()
        animate()
            .translationX(target.x)
            .translationY(target.y)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(190L)
            .setInterpolator(settleInterpolator)
            .withEndAction { releaseHardwareLayer() }
            .start()
    }

    private fun animateFlickDismiss() {
        val host = parent as? View
        val bounds = host?.let { movementBounds(it) }
        val targetY = (bounds?.getOrNull(3) ?: translationY) + height * 1.15f
        ensureHardwareLayer()
        animate().cancel()
        animate()
            .translationY(targetY)
            .scaleX(0.96f)
            .scaleY(0.96f)
            .alpha(0f)
            .setDuration(160L)
            .setInterpolator(settleInterpolator)
            .withEndAction {
                releaseHardwareLayer()
                alpha = 1f
                scaleX = 1f
                scaleY = 1f
                onFlickDismiss?.invoke()
            }
            .start()
    }

    private fun animateFlickExpand() {
        val host = parent as? View
        val bounds = host?.let { movementBounds(it) }
        val targetY = bounds?.getOrNull(2) ?: translationY
        ensureHardwareLayer()
        animate().cancel()
        animate()
            .translationY(targetY)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(105L)
            .setInterpolator(settleInterpolator)
            .withEndAction {
                releaseHardwareLayer()
                onFlickExpand?.invoke()
            }
            .start()
    }

    private fun finishInteractiveTransform(animated: Boolean) {
        if (!animated) {
            scaleX = 1f
            scaleY = 1f
            releaseHardwareLayer()
            return
        }
        ensureHardwareLayer()
        animate().cancel()
        animate().scaleX(1f).scaleY(1f).setDuration(110L).withEndAction { releaseHardwareLayer() }.start()
    }

    private fun ensureHardwareLayer() {
        if (!hardwareLayerActive) {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            hardwareLayerActive = true
        }
    }

    private fun releaseHardwareLayer() {
        if (!hardwareLayerActive) return
        setLayerType(View.LAYER_TYPE_NONE, null)
        hardwareLayerActive = false
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
