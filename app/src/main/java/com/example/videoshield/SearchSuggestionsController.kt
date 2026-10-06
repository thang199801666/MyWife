package com.example.videoshield

import android.graphics.Color
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
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.Future

/** Debounced completion overlay. Stale responses never replace a newer keyword. */
class SearchSuggestionsController(
    private val input: EditText,
    container: FrameLayout,
    private val submit: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,ArrayBlockingQueue(1))
    private var pending: Future<*>? = null
    private var generation = 0L
    private var closed = false
    private var paused = false
    private val cache = object : LinkedHashMap<String, List<String>>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > 20
    }
    private val values = mutableListOf<String>()
    private val list = ListView(input.context).apply {
        visibility = View.GONE
        setBackgroundColor(Color.rgb(14,14,14))
        dividerHeight = 0
        contentDescription = input.context.getString(R.string.search_suggestions)
    }
    private val adapter = object : ArrayAdapter<String>(input.context, android.R.layout.simple_list_item_1, values) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = super.getView(position, convertView, parent) as TextView
            row.setTextColor(Color.WHITE); row.textSize = 16f
            row.contentDescription = input.context.getString(R.string.search_suggestion,getItem(position))
            val icon = input.context.getDrawable(R.drawable.ic_search)?.mutate()
            val size = (20 * input.resources.displayMetrics.density).toInt()
            icon?.setTint(Color.LTGRAY); icon?.setBounds(0,0,size,size)
            row.setCompoundDrawables(icon,null,null,null)
            row.compoundDrawablePadding = size
            return row
        }
    }
    private val request = Runnable {
        val query = input.text.toString().trim().take(160)
        val ticket = generation
        if (!eligible() || query.isBlank()) return@Runnable
        cache[query]?.let { show(it, query); return@Runnable }
        pending = worker.submit {
            val result = runCatching { SearchSuggestionClient.load(query,AppLanguage.tag(input.context)) }.getOrDefault(emptyList())
            main.post {
                if (!closed && !paused && ticket == generation && eligible() && input.text.toString().trim().take(160) == query) {
                    if (result.isNotEmpty()) cache[query] = result
                    show(result, query)
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
            val query = values.getOrNull(position) ?: return@setOnItemClickListener
            dismiss(); submit(query)
        }
        input.addTextChangedListener(watcher)
        input.setOnFocusChangeListener { _, focused -> if (focused) schedule() else dismiss() }
    }
    private fun eligible() = !closed && !paused && input.hasFocus() && input.visibility == View.VISIBLE
    private fun schedule() {
        dismiss()
        if (!eligible() || input.text.isNullOrBlank()) return
        // Show the current query immediately while waiting for network completions.
        show(emptyList(), input.text.toString().trim().take(160))
        main.postDelayed(request, 250)
    }
    private fun show(rows: List<String>, query: String) {
        values.clear(); values.addAll(rows.ifEmpty { listOf(query) })
        adapter.notifyDataSetChanged(); list.visibility = View.VISIBLE; list.bringToFront()
    }
    fun dismiss(): Boolean {
        val visible = list.visibility == View.VISIBLE
        ++generation; main.removeCallbacks(request); pending?.cancel(true)
        worker.purge()
        list.visibility = View.GONE
        return visible
    }
    fun resume() { paused = false; if (input.hasFocus()) schedule() }
    fun pause() { paused = true; dismiss() }
    fun close() {
        closed = true; dismiss(); input.removeTextChangedListener(watcher)
        input.onFocusChangeListener = null
        worker.shutdownNow(); main.removeCallbacksAndMessages(null); cache.clear()
        (list.parent as? ViewGroup)?.removeView(list)
    }
}
