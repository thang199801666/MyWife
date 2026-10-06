package com.example.videoshield

import android.content.Context
import java.util.concurrent.atomic.AtomicLong

class ShieldStats(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("video_shield_stats", Context.MODE_PRIVATE)
    private val sessionNetwork = AtomicLong(0)
    private val sessionPageAds = AtomicLong(0)
    private val sessionSkips = AtomicLong(0)
    private val sessionSegmentSkips = AtomicLong(0)

    fun networkBlocked() { sessionNetwork.incrementAndGet(); incrementPersistent(KEY_NETWORK) }
    fun pageAdsHidden(count: Int) {
        if (count <= 0) return
        sessionPageAds.addAndGet(count.toLong())
        incrementPersistent(KEY_PAGE_ADS, count.toLong())
    }
    fun adSkipped() { sessionSkips.incrementAndGet(); incrementPersistent(KEY_SKIPS) }
    fun segmentSkipped() { sessionSegmentSkips.incrementAndGet(); incrementPersistent(KEY_SEGMENT_SKIPS) }

    fun sessionNetwork(): Long = sessionNetwork.get()
    fun sessionPageAds(): Long = sessionPageAds.get()
    fun sessionSkips(): Long = sessionSkips.get()
    fun sessionSegmentSkips(): Long = sessionSegmentSkips.get()
    fun sessionTotal(): Long = sessionNetwork() + sessionPageAds() + sessionSkips() + sessionSegmentSkips()

    fun lifetimeNetwork(): Long = prefs.getLong(KEY_NETWORK, 0)
    fun lifetimePageAds(): Long = prefs.getLong(KEY_PAGE_ADS, 0)
    fun lifetimeSkips(): Long = prefs.getLong(KEY_SKIPS, 0)
    fun lifetimeSegmentSkips(): Long = prefs.getLong(KEY_SEGMENT_SKIPS, 0)
    fun lifetimeTotal(): Long = lifetimeNetwork() + lifetimePageAds() + lifetimeSkips() + lifetimeSegmentSkips()

    fun resetLifetime() {
        prefs.edit().remove(KEY_NETWORK).remove(KEY_PAGE_ADS).remove(KEY_SKIPS).remove(KEY_SEGMENT_SKIPS).apply()
    }

    private fun incrementPersistent(key: String, amount: Long = 1L) {
        synchronized(this) { prefs.edit().putLong(key, prefs.getLong(key, 0) + amount).apply() }
    }

    companion object {
        private const val KEY_NETWORK = "network"
        private const val KEY_PAGE_ADS = "page_ads"
        private const val KEY_SKIPS = "skips"
        private const val KEY_SEGMENT_SKIPS = "segment_skips"
    }
}
