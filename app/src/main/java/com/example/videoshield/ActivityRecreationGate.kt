package com.example.videoshield

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coalesces concurrent Activity recreation requests (for example when two WebViews share one
 * Chromium renderer and both receive onRenderProcessGone). The gate is intentionally tiny and
 * thread-safe because WebView callbacks may race with lifecycle callbacks during teardown.
 */
class ActivityRecreationGate {
    private val pending = AtomicBoolean(false)

    fun request(): Boolean = pending.compareAndSet(false, true)

    fun cancel() {
        pending.set(false)
    }

    val isPending: Boolean get() = pending.get()
}
