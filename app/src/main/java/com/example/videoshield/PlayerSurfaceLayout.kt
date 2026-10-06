package com.example.videoshield

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.hypot

/** Floating mini-player drag/tap host; transport buttons retain their own taps. */
class PlayerSurfaceLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    var isMini: (() -> Boolean)? = null
    var onVideoTap: (() -> Unit)? = null
    private var startX = 0f
    private var startY = 0f
    private var initialX = 0f
    private var initialY = 0f
    private var moved = false
    private var canceled = false
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        isMini?.invoke() == true && event.actionMasked == MotionEvent.ACTION_DOWN &&
            event.y < height - 56f * resources.displayMetrics.density

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX; startY = event.rawY
                initialX = translationX; initialY = translationY
                moved = false; canceled = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> canceled = true
            MotionEvent.ACTION_MOVE -> {
                if (!canceled && isMini?.invoke() == true) {
                    val dx = event.rawX - startX; val dy = event.rawY - startY
                    moved = moved || hypot(dx, dy) > ViewConfiguration.get(context).scaledTouchSlop
                    val host = parent as? View
                    if (moved && host != null) {
                        val margin = 8f * resources.displayMetrics.density
                        val minX = -left + margin; val maxX = host.width - left - width - margin
                        val minY = -top + margin; val maxY = host.height - top - height - margin
                        if (minX <= maxX && minY <= maxY) {
                            translationX = (initialX + dx).coerceIn(minX, maxX)
                            translationY = (initialY + dy).coerceIn(minY, maxY)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!moved && !canceled) { performClick(); onVideoTap?.invoke() }
            }
            MotionEvent.ACTION_CANCEL -> {
                translationX = initialX; translationY = initialY
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
