package com.example.videoshield

/**
 * Keeps a small logical browse history outside Chromium so the WebView back/forward list can be
 * compacted during very long sessions without losing the user's most recent navigation path.
 *
 * The coordinator is intentionally URL-only: scroll state remains owned by BrowseResourceGuard
 * (and renderer-recovery state in MainActivity), while YouTube's document/media state stays in
 * the WebView. Shorts are canonicalized by the caller so vertical swipes do not consume slots.
 */
class BrowseHistoryCoordinator(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    data class Snapshot(
        val entries: List<String>,
        val index: Int,
        val compacted: Boolean
    )

    private val entries = ArrayList<String>(maxEntries.coerceAtLeast(1))
    private var index = -1
    var compacted: Boolean = false
        private set

    fun commit(url: String) {
        val value = url.trim().take(MAX_URL_LENGTH)
        if (value.isBlank()) return
        if (index in entries.indices && entries[index] == value) return

        if (index > 0 && entries[index - 1] == value) {
            index--
            return
        }
        if (index + 1 < entries.size && entries[index + 1] == value) {
            index++
            return
        }

        if (index + 1 < entries.size) {
            entries.subList(index + 1, entries.size).clear()
        }
        entries.add(value)
        index = entries.lastIndex
        trimToLimit()
    }


    fun replaceCurrent(url: String) {
        val value = url.trim().take(MAX_URL_LENGTH)
        if (value.isBlank()) return
        if (index !in entries.indices) {
            commit(value)
            return
        }
        entries[index] = value
    }

    fun backTarget(): String? = if (index > 0) entries[index - 1] else null
    fun forwardTarget(): String? = if (index >= 0 && index + 1 < entries.size) entries[index + 1] else null

    fun markCompacted() {
        compacted = true
    }

    fun snapshot(): Snapshot = Snapshot(entries.toList(), index, compacted)

    fun restore(snapshot: Snapshot) {
        entries.clear()
        val cleaned = snapshot.entries
            .map { it.trim().take(MAX_URL_LENGTH) }
            .filter { it.isNotBlank() }
        val limit = maxEntries.coerceAtLeast(1)
        val trimStart = (cleaned.size - limit).coerceAtLeast(0)
        cleaned.drop(trimStart).forEach(entries::add)
        index = if (entries.isEmpty()) {
            -1
        } else {
            // Snapshot indices are relative to the original list. Translate them when restoring
            // an oversized legacy/corrupt snapshot so current/back/forward still refer to the
            // same logical entry after the oldest prefix is discarded.
            (snapshot.index.coerceIn(0, cleaned.lastIndex) - trimStart).coerceIn(0, entries.lastIndex)
        }
        compacted = snapshot.compacted
    }

    fun size(): Int = entries.size
    fun current(): String? = entries.getOrNull(index)

    private fun trimToLimit() {
        val limit = maxEntries.coerceAtLeast(1)
        val overflow = entries.size - limit
        if (overflow <= 0) return
        repeat(overflow) { entries.removeAt(0) }
        index = (index - overflow).coerceAtLeast(0)
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 12
        private const val MAX_URL_LENGTH = 2048
    }
}
