package com.example.videoshield

import android.os.Process
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Small serial background queue for app-owned I/O and ranking work.
 *
 * Most callers need every mutation to run, but refresh/ranking requests become obsolete while
 * an older request is still queued. [executeLatest] replaces only pending work with the same key;
 * a task that is already running is allowed to finish and callers still use generation checks
 * before publishing its result. This keeps ordering for writes without letting refresh requests
 * accumulate behind them.
 */
class SerialTaskQueue(
    name: String,
    idleTimeoutSeconds: Long = 20L
) : AutoCloseable {
    private class TaggedTask(
        val key: String?,
        private val block: () -> Unit
    ) : Runnable {
        override fun run() = block()
    }

    private val lock = Any()
    private val threadId = AtomicInteger(0)
    private val queue = LinkedBlockingQueue<Runnable>()
    private val executor = ThreadPoolExecutor(
        1,
        1,
        idleTimeoutSeconds.coerceAtLeast(1L),
        TimeUnit.SECONDS,
        queue,
        ThreadFactory { runnable ->
            Thread({
                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
                runnable.run()
            }, "$name-${threadId.incrementAndGet()}").apply { isDaemon = false }
        }
    ).apply {
        allowCoreThreadTimeOut(true)
    }

    val isShutdown: Boolean get() = executor.isShutdown

    fun execute(block: () -> Unit): Boolean = enqueue(TaggedTask(null, block))

    fun executeLatest(key: String, block: () -> Unit): Boolean {
        synchronized(lock) {
            if (executor.isShutdown) return false
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val pending = iterator.next()
                if (pending is TaggedTask && pending.key == key) iterator.remove()
            }
            return enqueueLocked(TaggedTask(key, block))
        }
    }

    fun cancelPending(key: String) {
        synchronized(lock) {
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val pending = iterator.next()
                if (pending is TaggedTask && pending.key == key) iterator.remove()
            }
        }
    }

    /** Queue one final cleanup after already accepted work, then reject new tasks. */
    fun shutdownAfter(finalTask: (() -> Unit)? = null) {
        synchronized(lock) {
            if (executor.isShutdown) return
            if (finalTask != null) enqueueLocked(TaggedTask(null, finalTask))
            executor.shutdown()
        }
    }

    fun shutdownNow() {
        synchronized(lock) {
            queue.clear()
            executor.shutdownNow()
        }
    }

    override fun close() = shutdownAfter()

    private fun enqueue(task: Runnable): Boolean = synchronized(lock) {
        if (executor.isShutdown) false else enqueueLocked(task)
    }

    private fun enqueueLocked(task: Runnable): Boolean = try {
        executor.execute(task)
        true
    } catch (_: RejectedExecutionException) {
        false
    }
}
