package com.example.videoshield

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fullscreen-only gesture host.
 *
 * Normal taps and small moves remain owned by YouTube. The parent only takes
 * control after a deliberate swipe threshold or the second tap of a double-tap.
 */
class FullscreenGestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface Callback {
        fun currentPositionMs(): Long
        fun currentDurationMs(): Long
        fun seekToMs(positionMs: Long)
        fun exitFullscreen()
        fun previewExit(distancePx: Float) {}
        fun cancelExitPreview() {}
    }

    private enum class Mode { NONE, SEEK, BRIGHTNESS, VOLUME, DOUBLE_TAP, EXIT }

    var gesturesEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) resetGesture()
        }

    /** 0.65 = deliberate, 1.60 = responsive. */
    var sensitivity: Float = 1.0f
        set(value) { field = value.coerceIn(0.65f, 1.60f) }

    var doubleTapSeekSeconds: Int = 10
        set(value) { field = value.coerceIn(5, 30) }

    var callback: Callback? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val baseTouchSlop = ViewConfiguration.get(context).scaledTouchSlop * 2.25f
    private val doubleTapDistancePx = 72f * resources.displayMetrics.density

    private var feedbackView: TextView? = null
    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    private var basePositionMs = 0L
    private var durationMs = 0L
    private var previewPositionMs = 0L
    private var initialBrightness = 0.5f
    private var initialVolume = 0
    private var maxVolume = 1
    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var exitReady = false
    private var ignoreSequence = false

    fun bindFeedback(view: TextView) {
        feedbackView = view
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!gesturesEnabled || visibility != View.VISIBLE) return super.onInterceptTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            ignoreSequence = true
            lastTapAt = 0L
            resetGesture()
            return false
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) ignoreSequence = false
        if (ignoreSequence) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                mode = Mode.NONE
                basePositionMs = callback?.currentPositionMs()?.coerceAtLeast(0L) ?: 0L
                durationMs = callback?.currentDurationMs()?.coerceAtLeast(0L) ?: 0L
                previewPositionMs = basePositionMs
                initialBrightness = readCurrentBrightness()
                initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                maxVolume = max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))

                val elapsed = event.eventTime - lastTapAt
                val close = abs(event.x - lastTapX) <= doubleTapDistancePx && abs(event.y - lastTapY) <= doubleTapDistancePx
                val sideTap = event.x < width * 0.35f || event.x > width * 0.65f
                if (sideTap && durationMs > 0L && elapsed in 40L..DOUBLE_TAP_TIMEOUT_MS && close) {
                    mode = Mode.DOUBLE_TAP
                    val delta = doubleTapSeekSeconds * 1000L * if (event.x < width / 2f) -1 else 1
                    previewPositionMs = (basePositionMs + delta).coerceIn(0L, max(0L, durationMs - 250L))
                    val sign = if (delta >= 0) "+" else "−"
                    showFeedback("$sign${doubleTapSeekSeconds}s  •  ${formatTime(previewPositionMs)}")
                    lastTapAt = 0L
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode != Mode.NONE) return true
                val dx = event.x - downX
                val dy = event.y - downY
                val ax = abs(dx)
                val ay = abs(dy)
                val threshold = baseTouchSlop / sensitivity
                if (max(ax, ay) < threshold) return false
                lastTapAt = 0L

                val strongDownwardExit = dy > threshold * 1.35f && ay > ax * 1.20f &&
                    (downY < height * 0.42f || dy > threshold * 2.6f)
                mode = when {
                    ax > ay * 1.2f && durationMs > 0L -> Mode.SEEK
                    strongDownwardExit -> Mode.EXIT
                    ay > ax * 1.2f && downX < width * 0.35f -> Mode.BRIGHTNESS
                    ay > ax * 1.2f && downX > width * 0.65f -> Mode.VOLUME
                    ay > ax * 1.2f && dy > 0 -> Mode.EXIT
                    else -> Mode.NONE
                }

                if (mode != Mode.NONE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateGesture(event)
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (mode == Mode.NONE && abs(event.x - downX) < baseTouchSlop && abs(event.y - downY) < baseTouchSlop) {
                    lastTapAt = event.eventTime
                    lastTapX = event.x
                    lastTapY = event.y
                } else {
                    resetGesture()
                }
            }
            MotionEvent.ACTION_CANCEL -> resetGesture()
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            ignoreSequence = true
            lastTapAt = 0L
            finishGesture()
            return true
        }
        if (ignoreSequence) return true
        if (mode == Mode.NONE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (mode != Mode.DOUBLE_TAP) updateGesture(event)
            MotionEvent.ACTION_UP -> {
                if (mode == Mode.EXIT) updateGesture(event)
                if (mode == Mode.SEEK || mode == Mode.DOUBLE_TAP) callback?.seekToMs(previewPositionMs)
                val shouldExit = mode == Mode.EXIT && exitReady
                finishGesture(cancelExitPreview = !shouldExit)
                if (shouldExit) callback?.exitFullscreen()
            }
            MotionEvent.ACTION_CANCEL -> finishGesture(cancelExitPreview = true)
        }
        return true
    }

    private fun updateGesture(event: MotionEvent) {
        val widthSafe = width.coerceAtLeast(1).toFloat()
        val heightSafe = height.coerceAtLeast(1).toFloat()
        val dx = event.x - downX
        val dy = event.y - downY

        when (mode) {
            Mode.SEEK -> {
                val fullWidthSpan = ((durationMs * 0.10) * sensitivity).toLong().coerceIn(20_000L, 360_000L)
                val delta = ((dx / widthSafe) * fullWidthSpan).toLong()
                previewPositionMs = (basePositionMs + delta).coerceIn(0L, max(0L, durationMs - 250L))
                val sign = if (delta >= 0) "+" else "−"
                showFeedback("$sign${formatTime(abs(delta))}  •  ${formatTime(previewPositionMs)} / ${formatTime(durationMs)}")
            }

            Mode.BRIGHTNESS -> {
                val target = (initialBrightness - (dy / heightSafe) * 1.15f * sensitivity).coerceIn(0.02f, 1f)
                val activity = context as? Activity
                if (activity != null) {
                    val lp = activity.window.attributes
                    lp.screenBrightness = target
                    activity.window.attributes = lp
                }
                showFeedback(context.getString(R.string.brightness,(target * 100).roundToInt()))
            }

            Mode.VOLUME -> {
                val target = (initialVolume - (dy / heightSafe) * maxVolume * 1.15f * sensitivity)
                    .roundToInt().coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                val percent = ((target * 100f) / maxVolume).roundToInt()
                showFeedback(context.getString(R.string.volume,percent))
            }

            Mode.EXIT -> {
                val distance = dy.coerceAtLeast(0f)
                exitReady = distance >= max(64f * resources.displayMetrics.density, heightSafe * 0.18f)
                callback?.previewExit(distance)
                showFeedback(context.getString(if (exitReady) R.string.release_minimize else R.string.swipe_minimize))
            }

            Mode.DOUBLE_TAP, Mode.NONE -> Unit
        }
    }

    private fun readCurrentBrightness(): Float {
        val activity = context as? Activity
        val windowValue = activity?.window?.attributes?.screenBrightness ?: -1f
        if (windowValue in 0f..1f) return windowValue.coerceAtLeast(0.02f)
        return try {
            (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
                .coerceIn(0.02f, 1f)
        } catch (_: Exception) {
            0.5f
        }
    }

    private fun showFeedback(text: String) {
        feedbackView?.apply {
            animate().cancel()
            alpha = 1f
            visibility = View.VISIBLE
            this.text = text
        }
    }

    private fun finishGesture(cancelExitPreview: Boolean = true) {
        feedbackView?.apply {
            animate().cancel()
            animate().alpha(0f).setStartDelay(450L).setDuration(180L).withEndAction {
                visibility = View.GONE
                alpha = 1f
            }.start()
        }
        if (cancelExitPreview && mode == Mode.EXIT) callback?.cancelExitPreview()
        resetGesture(keepFeedback = true)
    }

    private fun resetGesture(keepFeedback: Boolean = false) {
        if (mode == Mode.EXIT && !keepFeedback) callback?.cancelExitPreview()
        mode = Mode.NONE
        exitReady = false
        lastTapAt = 0L
        parent?.requestDisallowInterceptTouchEvent(false)
        if (!keepFeedback) {
            feedbackView?.animate()?.cancel()
            feedbackView?.visibility = View.GONE
            feedbackView?.alpha = 1f
        }
    }

    private fun formatTime(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    companion object {
        private const val DOUBLE_TAP_TIMEOUT_MS = 320L
    }
}
