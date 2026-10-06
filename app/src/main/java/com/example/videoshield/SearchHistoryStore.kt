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
 */
data class SearchHistoryEntry(val query: String, val usedAt: Long)

class SearchHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(rawQuery: String, now: Long = System.currentTimeMillis()) {
        val query = sanitize(rawQuery) ?: return
        synchronized(LOCK) {
            val existing = loadLocked().filterNot { sameQuery(it.query, query) }.toMutableList()
            existing.add(0, SearchHistoryEntry(query, now))
            saveLocked(existing.take(MAX_ITEMS))
        }
    }

    fun recent(limit: Int = 12): List<SearchHistoryEntry> = synchronized(LOCK) {
        loadLocked().sortedByDescending { it.usedAt }.take(limit.coerceIn(1, MAX_ITEMS))
    }

    fun matches(rawQuery: String, limit: Int = 8): List<SearchHistoryEntry> {
        val needle = fold(rawQuery)
        if (needle.isBlank()) return recent(limit)
        return synchronized(LOCK) {
            loadLocked()
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
            saveLocked(loadLocked().filterNot { sameQuery(it.query, query) })
        }
    }

    fun clear() = synchronized(LOCK) { prefs.edit().remove(KEY).apply() }

    private fun loadLocked(): List<SearchHistoryEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val out = ArrayList<SearchHistoryEntry>(minOf(array.length(), MAX_ITEMS))
            for (index in 0 until minOf(array.length(), MAX_ITEMS)) {
                val obj = array.optJSONObject(index) ?: continue
                val query = sanitize(obj.optString("q")) ?: continue
                out += SearchHistoryEntry(query, obj.optLong("t", 0L).coerceAtLeast(0L))
            }
            out.distinctBy { fold(it.query) }
        }.getOrDefault(emptyList())
    }

    private fun saveLocked(entries: List<SearchHistoryEntry>) {
        val array = JSONArray()
        entries.take(MAX_ITEMS).forEach { entry ->
            array.put(JSONObject().apply {
                put("q", entry.query)
                put("t", entry.usedAt)
            })
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun sameQuery(left: String, right: String): Boolean = fold(left) == fold(right)

    companion object {
        private const val PREFS = "search_history"
        private const val KEY = "recent_queries_v1"
        private val LOCK = Any()
        const val MAX_ITEMS = 50

        fun sanitize(raw: String): String? {
            val query = raw.trim().replace(Regex("\\s+"), " ").take(160)
            return query.takeIf { it.isNotBlank() && !it.startsWith("http://", true) && !it.startsWith("https://", true) }
        }

        fun fold(raw: String): String = Normalizer.normalize(raw.lowercase(Locale.ROOT).trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), " ")
    }
}
