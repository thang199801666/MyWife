package com.example.videoshield

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private data class SearchSuggestionRow(val text: String, val fromHistory: Boolean)

/**
 * YouTube-like completion overlay. Recent searches are local/persistent and are merged ahead
 * of network completions. Stale responses never replace a newer keyword.
 */
class SearchSuggestionsController(
    private val input: EditText,
    container: FrameLayout,
    private val submit: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(1))
    private val history = SearchHistoryStore(input.context)
    private var pending: Future<*>? = null
    private var generation = 0L
    private var closed = false
    private var paused = false
    private val cache = object : LinkedHashMap<String, List<String>>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > 20
    }
    private val values = mutableListOf<SearchSuggestionRow>()
    private val list = ListView(input.context).apply {
        visibility = View.GONE
        setBackgroundColor(AppTheme.surface(input.context))
        dividerHeight = 0
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        contentDescription = input.context.getString(R.string.search_suggestions)
    }
    private val adapter = object : ArrayAdapter<SearchSuggestionRow>(input.context, android.R.layout.simple_list_item_1, values) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = super.getView(position, convertView, parent) as TextView
            val item = getItem(position) ?: return row
            row.text = item.text
            row.setTextColor(AppTheme.primary(input.context))
            row.textSize = 16f
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            row.minHeight = dp(54)
            row.setPadding(dp(18), 0, dp(18), 0)
            row.contentDescription = if (item.fromHistory) {
                input.context.getString(R.string.search_history_suggestion, item.text)
            } else {
                input.context.getString(R.string.search_suggestion, item.text)
            }
            val icon = input.context.getDrawable(if (item.fromHistory) R.drawable.ic_history else R.drawable.ic_search)?.mutate()
            val size = dp(21)
            icon?.setTint(AppTheme.icon(input.context))
            icon?.setBounds(0, 0, size, size)
            row.setCompoundDrawablesRelative(icon, null, null, null)
            row.compoundDrawablePadding = dp(18)
            return row
        }
    }

    private val request = Runnable {
        val query = input.text.toString().trim().take(160)
        val ticket = generation
        if (!eligible() || query.isBlank()) return@Runnable
        cache[query]?.let { showMerged(query, it); return@Runnable }
        pending = worker.submit {
            val result = runCatching { SearchSuggestionClient.load(query, AppLanguage.tag(input.context)) }.getOrDefault(emptyList())
            main.post {
                if (!closed && !paused && ticket == generation && eligible() && input.text.toString().trim().take(160) == query) {
                    if (result.isNotEmpty()) cache[query] = result
                    showMerged(query, result)
                }
            }
        }
    }

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { schedule() }
        override fun afterTextChanged(s: Editable?) {}
    }

    init {
        container.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val item = values.getOrNull(position) ?: return@setOnItemClickListener
            recordSubmitted(item.text)
            dismiss()
            submit(item.text)
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            val item = values.getOrNull(position) ?: return@setOnItemLongClickListener false
            if (!item.fromHistory) return@setOnItemLongClickListener false
            history.remove(item.text)
            schedule()
            true
        }
        input.addTextChangedListener(watcher)
        input.setOnFocusChangeListener { _, focused -> if (focused) schedule() else dismiss() }
    }

    private fun dp(value: Int): Int = (value * input.resources.displayMetrics.density + 0.5f).toInt()
    private fun eligible() = !closed && !paused && input.hasFocus() && input.visibility == View.VISIBLE

    private fun cancelPending(hide: Boolean) {
        ++generation
        main.removeCallbacks(request)
        pending?.cancel(true)
        pending = null
        worker.purge()
        if (hide) list.visibility = View.GONE
    }

    private fun schedule() {
        cancelPending(hide = false)
        if (!eligible()) {
            list.visibility = View.GONE
            return
        }
        val query = input.text.toString().trim().take(160)
        if (query.isBlank()) {
            showRecentHistory()
            return
        }
        // Local history appears immediately; remote completions are merged after debounce.
        showMerged(query, emptyList())
        main.postDelayed(request, 220L)
    }

    private fun showRecentHistory() {
        values.clear()
        values += history.recent(10).map { SearchSuggestionRow(it.query, true) }
        adapter.notifyDataSetChanged()
        list.visibility = if (values.isEmpty()) View.GONE else View.VISIBLE
        if (values.isNotEmpty()) list.bringToFront()
    }

    private fun showMerged(query: String, remote: List<String>) {
        val result = ArrayList<SearchSuggestionRow>(10)
        val keys = LinkedHashSet<String>()
        fun add(text: String, fromHistory: Boolean) {
            val clean = text.trim().take(160)
            if (clean.isBlank()) return
            val key = SearchHistoryStore.fold(clean)
            if (keys.add(key)) result += SearchSuggestionRow(clean, fromHistory)
        }
        history.matches(query, 5).forEach { add(it.query, true) }
        remote.forEach { add(it, false) }
        if (result.none { SearchHistoryStore.fold(it.text) == SearchHistoryStore.fold(query) }) add(query, false)
        values.clear()
        values += result.take(10)
        adapter.notifyDataSetChanged()
        list.visibility = if (values.isEmpty()) View.GONE else View.VISIBLE
        if (values.isNotEmpty()) list.bringToFront()
    }

    fun recordSubmitted(query: String) {
        val clean = SearchHistoryStore.sanitize(query) ?: return
        history.record(clean)
        // A submitted query becomes the newest local suggestion immediately.
        cache.remove(clean)
    }

    fun dismiss(): Boolean {
        val visible = list.visibility == View.VISIBLE
        cancelPending(hide = true)
        return visible
    }

    fun resume() { paused = false; if (input.hasFocus()) schedule() }
    fun pause() { paused = true; dismiss() }
    fun close() {
        closed = true
        dismiss()
        input.removeTextChangedListener(watcher)
        input.onFocusChangeListener = null
        worker.shutdownNow()
        main.removeCallbacksAndMessages(null)
        cache.clear()
        (list.parent as? ViewGroup)?.removeView(list)
    }
}
