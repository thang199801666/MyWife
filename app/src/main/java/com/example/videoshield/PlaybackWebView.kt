package com.example.videoshield

import android.content.Context
import android.util.AttributeSet
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import org.json.JSONArray
import kotlin.math.abs

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
    private val boundsRefresh = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || !hasWindowFocus()) return
            if (canSwipeMinimize?.invoke() == true && !touchActive) refreshVideoBounds()
            postDelayed(this, 500L)
        }
    }
    private val captureDistance get() = maxOf(ViewConfiguration.get(context).scaledTouchSlop * 3f, 24f * resources.displayMetrics.density)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(boundsRefresh)
        post(boundsRefresh)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(boundsRefresh)
        cachedVideoBounds = null
        resetSwipe()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        removeCallbacks(boundsRefresh)
        if (hasWindowFocus && isAttachedToWindow) post(boundsRefresh)
    }

    private fun cachedHit(): Boolean {
        if (cachedUrl != url || cachedWidth != width || android.os.SystemClock.elapsedRealtime() - cachedAt > 2000L) return false
        val bounds = cachedVideoBounds?.let { RectF(it) } ?: return false
        bounds.offset(0f, (cachedScrollY - scrollY).toFloat())
        bounds.bottom -= 24f * resources.displayMetrics.density
        return bounds.width() > 0 && bounds.height() > 0 && bounds.contains(startX, startY)
    }

    private fun refreshVideoBounds(after: (() -> Unit)? = null) {
        if (boundsRequestPending) return
        boundsRequestPending = true
        val requestedUrl = url
        val requestedWidth = width
        val requestedScroll = scrollY
        evaluateJavascript("""(() => {
            const v=Array.from(document.querySelectorAll('video')).find(v=>{
                const r=v.getBoundingClientRect();
                return r.width>0 && r.height>0 && r.bottom>0 && r.top<innerHeight;
            }); if(!v)return null;
            const r=v.getBoundingClientRect();
            return [r.left,r.top,r.right,r.bottom,innerWidth];
        })()""") { json ->
            boundsRequestPending = false
            if (url != requestedUrl || width != requestedWidth) return@evaluateJavascript
            cachedVideoBounds = runCatching {
                val a=JSONArray(json)
                val viewport=a.getDouble(4).toFloat()
                if (viewport <= 0f) return@runCatching null
                val scale=width/viewport
                RectF(a.getDouble(0).toFloat()*scale,a.getDouble(1).toFloat()*scale,
                    a.getDouble(2).toFloat()*scale,a.getDouble(3).toFloat()*scale)
            }.getOrNull()
            cachedUrl=requestedUrl; cachedWidth=requestedWidth; cachedScrollY=requestedScroll
            cachedAt=android.os.SystemClock.elapsedRealtime()
            after?.invoke()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            resetSwipe()
            touchActive = true
            startX = event.x
            startY = event.y
            startRawX = event.rawX
            startRawY = event.rawY
            val token = sequence
            if (canSwipeMinimize?.invoke() == true) {
                // A warm snapshot makes capture synchronous even on a busy renderer.
                // Fresh URL/width and native scroll offset keep comments outside the region.
                eligible = cachedHit()
                if (!eligible) refreshVideoBounds {
                    if (token == sequence && touchActive && canSwipeMinimize?.invoke() == true) eligible = cachedHit()
                }
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || canSwipeMinimize?.invoke() != true) {
            touchActive = false
            eligible = false
            dragging = false
            onMinimizeDrag?.invoke(0f)
            // A captured sequence stays consumed until its terminal event.
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
            if (dragging && event.actionMasked == MotionEvent.ACTION_MOVE) onMinimizeDrag?.invoke(dy.coerceAtLeast(0f))
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                val minimize = event.actionMasked == MotionEvent.ACTION_UP && dragging && eligible &&
                    canSwipeMinimize?.invoke() == true && VideoSwipePolicy.downward(dx, dy, 64f * resources.displayMetrics.density)
                resetSwipe()
                if (minimize) onSwipeMinimize?.invoke()
            }
            return true
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) resetSwipe()
        return handled
    }

    private fun resetSwipe() {
        ++sequence
        touchActive = false
        eligible = false
        dragging = false
        consuming = false
        onMinimizeDrag?.invoke(0f)
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
        dx.isFinite() && dy.isFinite() && dy >= threshold && dy > abs(dx) * 1.5f
}
