package com.example.videoshield

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * Small local-only search history used for autocomplete and recommendation ranking.
 * Nothing from this store is uploaded by the app; only the query that the user actually
 * submits is still sent to YouTube's search endpoint as part of normal browsing.
 *
 * The history is tiny, but autocomplete can query it on every keystroke. Keep one process
 * cache shared by every SearchHistoryStore instance so we do not repeatedly parse the same
 * JSON blob or sort the same 50 entries on the UI thread.
 */
data class SearchHistoryEntry(val query: String, val usedAt: Long)

class SearchHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(rawQuery: String, now: Long = System.currentTimeMillis()) {
        val query = sanitize(rawQuery) ?: return
        synchronized(LOCK) {
            val existing = entriesLocked(prefs).filterNot { sameQuery(it.query, query) }.toMutableList()
            existing.add(0, SearchHistoryEntry(query, now))
            saveLocked(prefs, existing.take(MAX_ITEMS))
        }
    }

    fun recent(limit: Int = 12): List<SearchHistoryEntry> = synchronized(LOCK) {
        // saveLocked() already keeps newest entries first.
        entriesLocked(prefs).take(limit.coerceIn(1, MAX_ITEMS))
    }

    fun matches(rawQuery: String, limit: Int = 8): List<SearchHistoryEntry> {
        val needle = fold(rawQuery)
        if (needle.isBlank()) return recent(limit)
        return synchronized(LOCK) {
            entriesLocked(prefs)
                .asSequence()
                .map { entry ->
                    val folded = fold(entry.query)
                    val rank = when {
                        folded == needle -> 0
                        folded.startsWith(needle) -> 1
                        folded.contains(" $needle") -> 2
                        folded.contains(needle) -> 3
                        else -> Int.MAX_VALUE
                    }
                    entry to rank
                }
                .filter { it.second != Int.MAX_VALUE }
                .sortedWith(compareBy<Pair<SearchHistoryEntry, Int>> { it.second }.thenByDescending { it.first.usedAt })
                .map { it.first }
                .take(limit.coerceIn(1, MAX_ITEMS))
                .toList()
        }
    }

    fun remove(rawQuery: String) {
        val query = sanitize(rawQuery) ?: return
        synchronized(LOCK) {
            saveLocked(prefs, entriesLocked(prefs).filterNot { sameQuery(it.query, query) })
        }
    }

    fun clear() = synchronized(LOCK) {
        processCache = emptyList()
        cacheLoaded = true
        prefs.edit().remove(KEY).apply()
    }

    private fun sameQuery(left: String, right: String): Boolean = fold(left) == fold(right)

    companion object {
        private const val PREFS = "search_history"
        private const val KEY = "recent_queries_v1"
        private val LOCK = Any()
        private val FOLD_LOCK = Any()
        private val foldCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 256
        }
        private val WHITESPACE = Regex("\\s+")
        private val COMBINING_MARKS = Regex("\\p{M}+")
        @Volatile private var cacheLoaded = false
        @Volatile private var processCache: List<SearchHistoryEntry> = emptyList()
        const val MAX_ITEMS = 50

        private fun entriesLocked(prefs: android.content.SharedPreferences): List<SearchHistoryEntry> {
            if (cacheLoaded) return processCache
            val raw = prefs.getString(KEY, null)
            processCache = if (raw.isNullOrBlank()) emptyList() else runCatching {
                val array = JSONArray(raw)
                val out = ArrayList<SearchHistoryEntry>(minOf(array.length(), MAX_ITEMS))
                val seen = HashSet<String>()
                for (index in 0 until minOf(array.length(), MAX_ITEMS)) {
                    val obj = array.optJSONObject(index) ?: continue
                    val query = sanitize(obj.optString("q")) ?: continue
                    val key = fold(query)
                    if (seen.add(key)) out += SearchHistoryEntry(query, obj.optLong("t", 0L).coerceAtLeast(0L))
                }
                // Old builds normally wrote newest-first, but sort once during the initial
                // process load so a legacy/out-of-order file cannot affect suggestions.
                out.sortedByDescending { it.usedAt }
            }.getOrDefault(emptyList())
            cacheLoaded = true
            return processCache
        }

        private fun saveLocked(prefs: android.content.SharedPreferences, entries: List<SearchHistoryEntry>) {
            val normalized = entries.take(MAX_ITEMS)
            processCache = normalized
            cacheLoaded = true
            val array = JSONArray()
            normalized.forEach { entry ->
                array.put(JSONObject().apply {
                    put("q", entry.query)
                    put("t", entry.usedAt)
                })
            }
            prefs.edit().putString(KEY, array.toString()).apply()
        }

        fun sanitize(raw: String): String? {
            val query = WHITESPACE.replace(raw.trim(), " ").take(160)
            return query.takeIf { it.isNotBlank() && !it.startsWith("http://", true) && !it.startsWith("https://", true) }
        }

        fun fold(raw: String): String {
            val key = raw.trim().take(160)
            synchronized(FOLD_LOCK) { foldCache[key]?.let { return it } }
            val folded = WHITESPACE.replace(
                COMBINING_MARKS.replace(
                    Normalizer.normalize(key.lowercase(Locale.ROOT), Normalizer.Form.NFD),
                    ""
                ),
                " "
            )
            synchronized(FOLD_LOCK) { foldCache[key] = folded }
            return folded
        }
    }
}
