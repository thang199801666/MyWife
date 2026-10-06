package com.example.videoshield

import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.webkit.WebView
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.min

/** Keeps the playback renderer active only while the app owns an allowed background session. */
class PlaybackWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle
) : WebView(context, attrs, defStyleAttr) {
    var keepActiveWhenHidden: (() -> Boolean)? = null
    var canSwipeMinimize: (() -> Boolean)? = null
    var onSwipeMinimize: (() -> Unit)? = null
    var onMinimizeDrag: ((Float) -> Unit)? = null
    var onMinimizeCancel: (() -> Unit)? = null

    private var sequence = 0
    private var touchActive = false
    private var eligible = false
    private var dragging = false
    private var consuming = false
    private var startX = 0f
    private var startY = 0f
    private var startRawX = 0f
    private var startRawY = 0f
    private var cachedVideoBounds: RectF? = null
    private var cachedAt = 0L
    private var cachedUrl: String? = null
    private var cachedWidth = 0
    private var cachedScrollY = 0
    private var boundsRequestPending = false
    private var velocityTracker: VelocityTracker? = null

    // Video bounds are only a precision aid for the swipe gesture. They are refreshed
    // lazily on ACTION_DOWN; focus/attach changes must not wake the renderer just to
    // measure geometry while the user is passively watching.

    // Capture sooner than before; horizontal scrubbing is still protected by direction checks.
    private val captureDistance
        get() = maxOf(ViewConfiguration.get(context).scaledTouchSlop * 2f, 18f * resources.displayMetrics.density)

    override fun onDetachedFromWindow() {
        cachedVideoBounds = null
        resetSwipe(cancelPreview = true)
        super.onDetachedFromWindow()
    }

    private fun cachedHit(maxAgeMs: Long = 15_000L): Boolean {
        if (cachedUrl != url || cachedWidth != width || android.os.SystemClock.elapsedRealtime() - cachedAt > maxAgeMs) return false
        val bounds = cachedVideoBounds?.let { RectF(it) } ?: return false
        bounds.offset(0f, (cachedScrollY - scrollY).toFloat())
        // Keep YouTube's bottom transport strip available for native scrubbing/taps.
        bounds.bottom -= 18f * resources.displayMetrics.density
        return bounds.width() > 0 && bounds.height() > 0 && bounds.contains(startX, startY)
    }

    /**
     * Renderer JS can be delayed on a busy watch page. A deterministic native fallback for
     * the top video viewport prevents a quick downward swipe from being lost while waiting for
     * getBoundingClientRect(). The fallback is intentionally limited to the visible player area.
     */
    private fun nativePlayerFallbackHit(): Boolean {
        if (width <= 0 || height <= 0 || startX < 0f || startX > width) return false
        val density = resources.displayMetrics.density
        val aspectHeight = width * 9f / 16f
        val maxPlayerBottom = min(height * 0.62f, aspectHeight + 72f * density)
        val minPlayerBottom = min(height * 0.45f, 180f * density)
        return startY in 0f..maxOf(minPlayerBottom, maxPlayerBottom)
    }

    private fun refreshVideoBounds(after: (() -> Unit)? = null) {
        if (boundsRequestPending) return
        boundsRequestPending = true
        val requestedUrl = url
        val requestedWidth = width
        val requestedScroll = scrollY
        evaluateJavascript("""(() => {
            const v=document.querySelector('.html5-video-player video.html5-main-video') ||
              document.querySelector('video.video-stream.html5-main-video') ||
              Array.from(document.querySelectorAll('video')).find(v=>{
                const r=v.getBoundingClientRect();
                return r.width>0 && r.height>0 && r.bottom>0 && r.top<innerHeight;
              });
            if(!v)return null;
            const r=v.getBoundingClientRect();
            return [r.left,r.top,r.right,r.bottom,innerWidth];
        })()""") { json ->
            boundsRequestPending = false
            if (url != requestedUrl || width != requestedWidth) return@evaluateJavascript
            cachedVideoBounds = runCatching {
                if (json.isNullOrBlank() || json == "null") return@runCatching null
                val a = JSONArray(json)
                val viewport = a.getDouble(4).toFloat()
                if (viewport <= 0f) return@runCatching null
                val scale = width / viewport
                RectF(
                    a.getDouble(0).toFloat() * scale,
                    a.getDouble(1).toFloat() * scale,
                    a.getDouble(2).toFloat() * scale,
                    a.getDouble(3).toFloat() * scale
                )
            }.getOrNull()
            cachedUrl = requestedUrl
            cachedWidth = requestedWidth
            cachedScrollY = requestedScroll
            cachedAt = android.os.SystemClock.elapsedRealtime()
            after?.invoke()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            resetSwipe(cancelPreview = true)
            touchActive = true
            startX = event.x
            startY = event.y
            startRawX = event.rawX
            startRawY = event.rawY
            velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
            val token = sequence
            if (canSwipeMinimize?.invoke() == true) {
                // Never make gesture capture depend solely on an asynchronous JS callback.
                val fallbackHit = nativePlayerFallbackHit()
                eligible = cachedHit() || fallbackHit
                val cacheFresh = cachedUrl == url && cachedWidth == width &&
                    android.os.SystemClock.elapsedRealtime() - cachedAt <= 15_000L
                // The native top-player viewport is deterministic for the common gesture path.
                // Only wake JavaScript geometry when the touch is outside that fallback and an
                // accurate media hit-test can actually change the decision.
                if (!cacheFresh && !fallbackHit) refreshVideoBounds {
                    if (token == sequence && touchActive && canSwipeMinimize?.invoke() == true) {
                        eligible = cachedHit() || eligible
                    }
                }
            }
        } else {
            velocityTracker?.addMovement(event)
        }

        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            val wasConsuming = consuming
            resetSwipe(cancelPreview = true)
            return if (wasConsuming) true else super.dispatchTouchEvent(event)
        }

        val swipeStillAllowed = canSwipeMinimize?.invoke() == true
        if (!swipeStillAllowed && !consuming) {
            touchActive = false
            eligible = false
        }

        val dx = event.rawX - startRawX
        val dy = event.rawY - startRawY
        if (event.actionMasked == MotionEvent.ACTION_MOVE && eligible && !consuming &&
            VideoSwipePolicy.downward(dx, dy, captureDistance)) {
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            consuming = true
            dragging = true
            parent?.requestDisallowInterceptTouchEvent(true)
        }

        if (consuming) {
            if (dragging && event.actionMasked == MotionEvent.ACTION_MOVE) {
                onMinimizeDrag?.invoke(dy.coerceAtLeast(0f))
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                velocityTracker?.computeCurrentVelocity(1_000)
                val yVelocity = velocityTracker?.yVelocity ?: 0f
                val density = resources.displayMetrics.density
                val distanceCommit = dy >= 58f * density
                val flingCommit = dy >= 22f * density && yVelocity >= 900f * density && dy > abs(dx) * 1.05f
                val minimize = event.actionMasked == MotionEvent.ACTION_UP && dragging && eligible &&
                    swipeStillAllowed && (distanceCommit || flingCommit)
                resetSwipe(cancelPreview = !minimize)
                if (minimize) onSwipeMinimize?.invoke()
            }
            return true
        }

        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            resetSwipe(cancelPreview = true)
        }
        return handled
    }

    private fun resetSwipe(cancelPreview: Boolean) {
        val hadPreview = dragging || consuming
        ++sequence
        touchActive = false
        eligible = false
        dragging = false
        consuming = false
        velocityTracker?.recycle()
        velocityTracker = null
        if (cancelPreview && hadPreview) onMinimizeCancel?.invoke()
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        val effective = if (visibility != VISIBLE && keepActiveWhenHidden?.invoke() == true) VISIBLE else visibility
        super.onWindowVisibilityChanged(effective)
    }
}

/** Require a deliberate downward movement; horizontal scrubbing stays with YouTube. */
object VideoSwipePolicy {
    fun downward(dx: Float, dy: Float, threshold: Float): Boolean =
        dx.isFinite() && dy.isFinite() && dy >= threshold && dy > abs(dx) * 1.20f
}
