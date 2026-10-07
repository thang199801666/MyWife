package com.example.videoshield

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ProgressBar
import kotlin.math.abs
import kotlin.math.min

/**
 * Lightweight pull-to-refresh host for the Home browse WebView.
 *
 * The gesture is deliberately native instead of JavaScript-driven so it does not add
 * polling/DOM work to the YouTube page. It only intercepts a downward drag when the
 * content is already at the top and the activity says the current route is refreshable.
 */
class HomePullRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var canStartRefresh: () -> Boolean = { true }
    var onRefresh: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val triggerDistance = 82f * density
    private val maxPullDistance = 138f * density
    private val refreshingOffset = 58f * density

    private var contentView: View? = null
    private lateinit var indicator: ProgressBar
    private var downX = 0f
    private var downY = 0f
    private var gestureEligible = false
    private var pulling = false
    private var refreshing = false
    private var pullOffset = 0f
    private var offsetAnimator: android.animation.ValueAnimator? = null

    private val refreshTimeout = Runnable { finishRefresh() }

    override fun onFinishInflate() {
        super.onFinishInflate()
        contentView = findViewById<View>(R.id.browseWebView)
        indicator = ProgressBar(context).apply {
            isIndeterminate = true
            visibility = View.GONE
            alpha = 0f
            scaleX = 0.72f
            scaleY = 0.72f
            isClickable = false
            isFocusable = false
        }
        addView(indicator, LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(10)
        })
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val content = contentView ?: return false
        if (refreshing) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pulling = false
                gestureEligible = canStartRefresh() && !content.canScrollVertically(-1)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gestureEligible || content.canScrollVertically(-1)) return false
                val dx = event.x - downX
                val dy = event.y - downY
                if (dy > touchSlop && dy > abs(dx) * 1.15f) {
                    pulling = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    showIndicator()
                    return true
                }
                if (abs(dx) > touchSlop && abs(dx) > abs(dy)) gestureEligible = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetGestureFlags()
        }
        return pulling
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!pulling && event.actionMasked != MotionEvent.ACTION_DOWN) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = (event.y - downY - touchSlop).coerceAtLeast(0f)
                setPullOffset(resistedOffset(dy))
            }
            MotionEvent.ACTION_UP -> {
                if (pullOffset >= triggerDistance) beginRefresh() else settleTo(0f, 180L) { hideIndicator() }
                resetGestureFlags()
            }
            MotionEvent.ACTION_CANCEL -> {
                settleTo(0f, 160L) { hideIndicator() }
                resetGestureFlags()
            }
        }
        return true
    }

    /** Rebind the pull gesture to a newly rehydrated browse WebView. */
    fun bindContentView(view: View) {
        if (contentView === view) return
        offsetAnimator?.cancel()
        offsetAnimator = null
        removeCallbacks(refreshTimeout)
        contentView?.translationY = 0f
        contentView = view
        refreshing = false
        pulling = false
        gestureEligible = false
        pullOffset = 0f
        hideIndicator()
    }

    fun finishRefresh() {
        removeCallbacks(refreshTimeout)
        if (!refreshing && pullOffset <= 0.5f) return
        refreshing = false
        settleTo(0f, 210L) { hideIndicator() }
    }

    fun cancelPull() {
        if (refreshing) return
        resetGestureFlags()
        if (pullOffset > 0f) settleTo(0f, 160L) { hideIndicator() } else hideIndicator()
    }

    private fun beginRefresh() {
        if (refreshing) return
        refreshing = true
        showIndicator()
        settleTo(refreshingOffset, 150L) {
            if (!refreshing) return@settleTo
            indicator.alpha = 1f
            onRefresh?.invoke()
            removeCallbacks(refreshTimeout)
            postDelayed(refreshTimeout, 12_000L)
        }
    }

    private fun resistedOffset(distance: Float): Float {
        // Strong resistance after the trigger keeps a long drag from moving the entire page.
        val linear = distance * 0.48f
        return if (linear <= triggerDistance) linear
        else min(maxPullDistance, triggerDistance + (linear - triggerDistance) * 0.22f)
    }

    private fun setPullOffset(value: Float) {
        pullOffset = value.coerceIn(0f, maxPullDistance)
        contentView?.translationY = pullOffset
        val progress = (pullOffset / triggerDistance).coerceIn(0f, 1f)
        showIndicator()
        indicator.alpha = (0.2f + progress * 0.8f).coerceIn(0f, 1f)
        val scale = 0.72f + progress * 0.28f
        indicator.scaleX = scale
        indicator.scaleY = scale
        indicator.translationY = (pullOffset * 0.16f).coerceAtMost(12f * density)
    }

    private fun settleTo(target: Float, duration: Long, end: (() -> Unit)? = null) {
        val content = contentView ?: return
        content.animate().cancel()
        offsetAnimator?.cancel()
        offsetAnimator = null
        val start = pullOffset
        val delta = target - start
        if (abs(delta) < 0.5f) {
            setPullOffset(target)
            end?.invoke()
            return
        }
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                setPullOffset(start + delta * fraction)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (offsetAnimator !== animation) return
                    offsetAnimator = null
                    setPullOffset(target)
                    end?.invoke()
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    if (offsetAnimator === animation) offsetAnimator = null
                }
            })
        }
        offsetAnimator = animator
        animator.start()
    }

    private fun showIndicator() {
        if (::indicator.isInitialized && indicator.visibility != View.VISIBLE) indicator.visibility = View.VISIBLE
    }

    private fun hideIndicator() {
        if (!::indicator.isInitialized) return
        indicator.visibility = View.GONE
        indicator.alpha = 0f
        indicator.scaleX = 0.72f
        indicator.scaleY = 0.72f
        indicator.translationY = 0f
    }

    private fun resetGestureFlags() {
        pulling = false
        gestureEligible = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refreshTimeout)
        offsetAnimator?.cancel()
        offsetAnimator = null
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()
}
