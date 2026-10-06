package com.example.videoshield

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * Session counters are hot-path data: a single YouTube page may block many network requests.
 * Keep increments in memory and batch persistent writes instead of scheduling a SharedPreferences
 * disk write for every blocked resource. MainActivity flushes on lifecycle boundaries as well.
 */
class ShieldStats(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("video_shield_stats", Context.MODE_PRIVATE)
    private val sessionNetwork = AtomicLong(0)
    private val sessionPageAds = AtomicLong(0)
    private val sessionSkips = AtomicLong(0)
    private val sessionSegmentSkips = AtomicLong(0)
    private val pendingNetwork = AtomicLong(0)
    private val pendingPageAds = AtomicLong(0)
    private val pendingSkips = AtomicLong(0)
    private val pendingSegmentSkips = AtomicLong(0)
    @Volatile private var persistedNetwork = prefs.getLong(KEY_NETWORK, 0L)
    @Volatile private var persistedPageAds = prefs.getLong(KEY_PAGE_ADS, 0L)
    @Volatile private var persistedSkips = prefs.getLong(KEY_SKIPS, 0L)
    @Volatile private var persistedSegmentSkips = prefs.getLong(KEY_SEGMENT_SKIPS, 0L)
    @Volatile private var lastFlushAt = SystemClock.elapsedRealtime()

    fun networkBlocked() {
        sessionNetwork.incrementAndGet()
        pendingNetwork.incrementAndGet()
        flushIfDue()
    }

    fun pageAdsHidden(count: Int) {
        if (count <= 0) return
        val amount = count.toLong()
        sessionPageAds.addAndGet(amount)
        pendingPageAds.addAndGet(amount)
        flushIfDue()
    }

    fun adSkipped() {
        sessionSkips.incrementAndGet()
        pendingSkips.incrementAndGet()
        flushIfDue()
    }

    fun segmentSkipped() {
        sessionSegmentSkips.incrementAndGet()
        pendingSegmentSkips.incrementAndGet()
        flushIfDue()
    }

    fun sessionNetwork(): Long = sessionNetwork.get()
    fun sessionPageAds(): Long = sessionPageAds.get()
    fun sessionSkips(): Long = sessionSkips.get()
    fun sessionSegmentSkips(): Long = sessionSegmentSkips.get()
    fun sessionTotal(): Long = sessionNetwork() + sessionPageAds() + sessionSkips() + sessionSegmentSkips()

    // Include not-yet-flushed deltas so the dashboard remains exact without forcing I/O.
    fun lifetimeNetwork(): Long = persistedNetwork + pendingNetwork.get()
    fun lifetimePageAds(): Long = persistedPageAds + pendingPageAds.get()
    fun lifetimeSkips(): Long = persistedSkips + pendingSkips.get()
    fun lifetimeSegmentSkips(): Long = persistedSegmentSkips + pendingSegmentSkips.get()
    fun lifetimeTotal(): Long = lifetimeNetwork() + lifetimePageAds() + lifetimeSkips() + lifetimeSegmentSkips()

    @Synchronized
    fun flush() {
        val network = pendingNetwork.getAndSet(0)
        val pageAds = pendingPageAds.getAndSet(0)
        val skips = pendingSkips.getAndSet(0)
        val segmentSkips = pendingSegmentSkips.getAndSet(0)
        lastFlushAt = SystemClock.elapsedRealtime()
        if (network == 0L && pageAds == 0L && skips == 0L && segmentSkips == 0L) return

        val editor = prefs.edit()
        if (network != 0L) {
            persistedNetwork += network
            editor.putLong(KEY_NETWORK, persistedNetwork)
        }
        if (pageAds != 0L) {
            persistedPageAds += pageAds
            editor.putLong(KEY_PAGE_ADS, persistedPageAds)
        }
        if (skips != 0L) {
            persistedSkips += skips
            editor.putLong(KEY_SKIPS, persistedSkips)
        }
        if (segmentSkips != 0L) {
            persistedSegmentSkips += segmentSkips
            editor.putLong(KEY_SEGMENT_SKIPS, persistedSegmentSkips)
        }
        editor.apply()
    }

    @Synchronized
    fun resetLifetime() {
        pendingNetwork.set(0)
        pendingPageAds.set(0)
        pendingSkips.set(0)
        pendingSegmentSkips.set(0)
        persistedNetwork = 0L
        persistedPageAds = 0L
        persistedSkips = 0L
        persistedSegmentSkips = 0L
        lastFlushAt = SystemClock.elapsedRealtime()
        prefs.edit().remove(KEY_NETWORK).remove(KEY_PAGE_ADS).remove(KEY_SKIPS).remove(KEY_SEGMENT_SKIPS).apply()
    }

    private fun flushIfDue() {
        if (SystemClock.elapsedRealtime() - lastFlushAt >= FLUSH_INTERVAL_MS) flush()
    }

    companion object {
        private const val KEY_NETWORK = "network"
        private const val KEY_PAGE_ADS = "page_ads"
        private const val KEY_SKIPS = "skips"
        private const val KEY_SEGMENT_SKIPS = "segment_skips"
        private const val FLUSH_INTERVAL_MS = 15_000L
    }
}
