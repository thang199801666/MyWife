package com.example.videoshield

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private data class SearchSuggestionRow(val text: String, val fromHistory: Boolean)
private data class SearchSuggestionHolder(
    val icon: ImageView,
    val title: TextView,
    val action: IconButton
)

/**
 * YouTube-like completion overlay. Recent searches are local/persistent and are merged ahead
 * of network completions. Query changes cancel both the queued Future and the active HTTP socket,
 * so fast typing cannot leave stale requests consuming radio/network time in the background.
 */
class SearchSuggestionsController(
    private val input: EditText,
    container: FrameLayout,
    private val canUseNetwork: () -> Boolean = { true },
    private val submit: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(
        1, 1, 10L, TimeUnit.SECONDS, ArrayBlockingQueue(1),
        ThreadPoolExecutor.DiscardOldestPolicy()
    ).apply { allowCoreThreadTimeOut(true) }
    private val history = SearchHistoryStore(input.context)
    private var pending: Future<*>? = null
    private var pendingRequest: SearchSuggestionClient.Request? = null
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
        clipToPadding = false
        setPadding(0, dp(6), 0, dp(8))
        elevation = dp(4).toFloat()
        contentDescription = input.context.getString(R.string.search_suggestions)
    }

    private val adapter = object : ArrayAdapter<SearchSuggestionRow>(input.context, 0, values) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? LinearLayout) ?: createRow()
            val holder = row.tag as SearchSuggestionHolder
            val item = getItem(position) ?: return row

            holder.title.text = item.text
            holder.title.setTextColor(AppTheme.primary(input.context))
            holder.icon.setImageResource(if (item.fromHistory) R.drawable.ic_history else R.drawable.ic_search)
            holder.icon.imageTintList = android.content.res.ColorStateList.valueOf(AppTheme.icon(input.context))
            row.contentDescription = if (item.fromHistory) {
                input.context.getString(R.string.search_history_suggestion, item.text)
            } else {
                input.context.getString(R.string.search_suggestion, item.text)
            }

            if (item.fromHistory) {
                holder.action.setIcon(R.drawable.ic_ui_close)
                holder.action.contentDescription = input.context.getString(R.string.search_remove_history, item.text)
                holder.action.setOnClickListener {
                    history.remove(item.text)
                    schedule()
                }
            } else {
                holder.action.setIcon(R.drawable.ic_search_insert)
                holder.action.contentDescription = input.context.getString(R.string.search_fill_suggestion, item.text)
                holder.action.setOnClickListener {
                    if (closed || paused) return@setOnClickListener
                    input.setText(item.text)
                    input.setSelection(input.text.length)
                }
            }
            return row
        }
    }

    private val request = Runnable {
        val query = input.text.toString().trim().take(160)
        val ticket = generation
        if (!eligible() || query.isBlank() || !canUseNetwork()) return@Runnable
        val cacheKey = SearchHistoryStore.fold(query)
        cache[cacheKey]?.let { showMerged(query, it); return@Runnable }

        val call = SearchSuggestionClient.request(query, AppLanguage.tag(input.context))
        pendingRequest = call
        pending = worker.submit {
            val loaded = runCatching { call.execute() }
            val result = loaded.getOrDefault(emptyList())
            main.post {
                if (pendingRequest === call) pendingRequest = null
                if (!closed && !paused && ticket == generation && eligible() &&
                    input.text.toString().trim().take(160) == query) {
                    // Successful empty autocomplete is cacheable too; transient network
                    // failures are not, so reconnect/focus can retry immediately.
                    if (loaded.isSuccess) cache[cacheKey] = result
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
        input.addTextChangedListener(watcher)
        input.setOnFocusChangeListener { _, focused -> if (focused) schedule() else dismiss() }
    }

    private fun createRow(): LinearLayout {
        val icon = ImageView(input.context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val title = TextView(input.context).apply {
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val action = IconButton(input.context, null, android.R.attr.borderlessButtonStyle)
        return LinearLayout(input.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(52)
            setPadding(dp(18), 0, dp(4), 0)
            isClickable = true
            isFocusable = true
            addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(18) })
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(4) })
            addView(action, LinearLayout.LayoutParams(dp(44), dp(44)))
            tag = SearchSuggestionHolder(icon, title, action)
        }
    }

    private fun dp(value: Int): Int = (value * input.resources.displayMetrics.density + 0.5f).toInt()
    private fun eligible() = !closed && !paused && input.hasFocus() && input.visibility == View.VISIBLE

    private fun cancelPending(hide: Boolean) {
        ++generation
        main.removeCallbacks(request)
        pendingRequest?.cancel()
        pendingRequest = null
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
        // Local history appears immediately; remote completions are merged only after the
        // user pauses typing. Offline mode never schedules a network task.
        showMerged(query, emptyList())
        if (canUseNetwork()) main.postDelayed(request, 240L)
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
        val queryKey = SearchHistoryStore.fold(query)
        fun add(text: String, fromHistory: Boolean) {
            val clean = text.trim().take(160)
            if (clean.isBlank()) return
            val key = SearchHistoryStore.fold(clean)
            if (keys.add(key)) result += SearchSuggestionRow(clean, fromHistory)
        }
        history.matches(query, 5).forEach { add(it.query, true) }
        remote.forEach { add(it, false) }
        if (result.none { SearchHistoryStore.fold(it.text) == queryKey }) add(query, false)
        values.clear()
        values += result.take(10)
        adapter.notifyDataSetChanged()
        list.visibility = if (values.isEmpty()) View.GONE else View.VISIBLE
        if (values.isNotEmpty()) list.bringToFront()
    }

    fun recordSubmitted(query: String) {
        val clean = SearchHistoryStore.sanitize(query) ?: return
        history.record(clean)
        cache.remove(SearchHistoryStore.fold(clean))
    }

    fun onNetworkChanged() {
        if (!closed && !paused && input.hasFocus()) schedule()
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
