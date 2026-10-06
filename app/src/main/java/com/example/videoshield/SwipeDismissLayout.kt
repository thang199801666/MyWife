package com.example.videoshield

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Horizontal swipe-to-dismiss container that preserves normal child taps until
 * a deliberate horizontal gesture crosses the interception threshold.
 */
class SwipeDismissLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onDismiss: (() -> Unit)? = null
    var enabledForDismiss: Boolean = true

    private val threshold = ViewConfiguration.get(context).scaledTouchSlop * 2.5f
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!enabledForDismiss) return super.onInterceptTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragging && abs(dx) > threshold && abs(dx) > abs(dy) * 1.25f) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging && event.actionMasked != MotionEvent.ACTION_DOWN) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                translationX = dx
                alpha = (1f - abs(dx) / width.coerceAtLeast(1).toFloat()).coerceIn(0.25f, 1f)
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                if (abs(dx) >= width.coerceAtLeast(1) * 0.28f) {
                    val direction = if (dx >= 0f) 1f else -1f
                    animate().translationX(direction * width).alpha(0f).setDuration(150L).withEndAction {
                        onDismiss?.invoke()
                        translationX = 0f
                        alpha = 1f
                        visibility = View.GONE
                    }.start()
                } else restore()
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                restore()
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun restore() {
        animate().translationX(0f).alpha(1f).setDuration(120L).start()
    }
}
