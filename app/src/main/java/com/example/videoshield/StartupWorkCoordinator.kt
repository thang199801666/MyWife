package com.example.videoshield

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import java.util.LinkedHashMap

/**
 * Lifecycle-owned, one-shot startup scheduler.
 *
 * Work is armed only after the first app frame. Tasks that need a visible Activity stay pending
 * while the Activity is backgrounded and are dispatched from [onForeground] without polling.
 */
class StartupWorkCoordinator(
    private val root: View,
    private val alive: () -> Boolean,
    private val foreground: () -> Boolean
) : AutoCloseable {
    private data class Task(
        val key: String,
        val delayMs: Long,
        val requireForeground: Boolean,
        val yieldToInteraction: Boolean,
        val action: () -> Unit,
        var scheduled: Boolean = false,
        var runnable: Runnable? = null
    )

    private val main = Handler(Looper.getMainLooper())
    private val pending = LinkedHashMap<String, Task>()
    private var firstFrameReady = false
    private var interactionUntilUptimeMs = 0L
    private var closed = false

    private val firstFrameCommit = Runnable { markFirstFrameReady() }
    private val firstFrameListener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            removeFirstFrameListener()
            // post() puts the staging boundary after the traversal that produced the first frame.
            root.post(firstFrameCommit)
            return true
        }
    }

    init {
        root.viewTreeObserver.addOnPreDrawListener(firstFrameListener)
    }

    val isFirstFrameReady: Boolean get() = firstFrameReady

    fun defer(
        key: String,
        delayMs: Long = 0L,
        requireForeground: Boolean = false,
        yieldToInteraction: Boolean = false,
        action: () -> Unit
    ) {
        if (closed) return
        pending.remove(key)?.let { old -> old.runnable?.let { main.removeCallbacks(it) } }
        val task = Task(key, delayMs.coerceAtLeast(0L), requireForeground, yieldToInteraction, action)
        pending[key] = task
        if (firstFrameReady) schedule(task, task.delayMs)
    }


    /**
     * Extends the current input/scroll quiet window. Scheduled tasks do not need to be removed from
     * the Handler queue on every scroll callback; when they fire, they self-defer once to the end
     * of the latest window. This keeps the hot scroll path allocation-free and event-driven.
     */
    fun noteInteraction(quietWindowMs: Long = FeedInteractionBudgetPolicy.STARTUP_QUIET_WINDOW_MS) {
        if (closed || quietWindowMs <= 0L) return
        val until = SystemClock.uptimeMillis() + quietWindowMs
        if (until > interactionUntilUptimeMs) interactionUntilUptimeMs = until
    }

    /** Called from Activity.onResume(); foreground-gated work resumes without a retry timer. */
    fun onForeground() {
        if (closed || !firstFrameReady) return
        pending.values.toList().forEach { task ->
            if (!task.scheduled) schedule(task, 0L)
        }
    }

    private fun markFirstFrameReady() {
        if (closed || firstFrameReady || !alive()) return
        firstFrameReady = true
        pending.values.toList().forEach { task -> schedule(task, task.delayMs) }
    }

    private fun schedule(task: Task, delayMs: Long) {
        if (closed || task.scheduled || !pending.containsKey(task.key)) return
        val runnable = Runnable {
            task.scheduled = false
            task.runnable = null
            if (closed || !alive() || !pending.containsKey(task.key)) return@Runnable
            if (task.requireForeground && !foreground()) return@Runnable
            if (task.yieldToInteraction) {
                val remaining = (interactionUntilUptimeMs - SystemClock.uptimeMillis()).coerceAtLeast(0L)
                if (remaining > 0L) {
                    schedule(task, remaining)
                    return@Runnable
                }
            }
            pending.remove(task.key)
            task.action()
        }
        task.scheduled = true
        task.runnable = runnable
        if (delayMs <= 0L) main.post(runnable) else main.postDelayed(runnable, delayMs)
    }

    private fun removeFirstFrameListener() {
        val observer = root.viewTreeObserver
        if (observer.isAlive) runCatching { observer.removeOnPreDrawListener(firstFrameListener) }
    }

    override fun close() {
        if (closed) return
        closed = true
        removeFirstFrameListener()
        root.removeCallbacks(firstFrameCommit)
        pending.values.forEach { task -> task.runnable?.let { main.removeCallbacks(it) } }
        pending.clear()
    }
}
