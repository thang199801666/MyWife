package com.example.videoshield

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class DownloadsActivity : LocalizedActivity() {
    private val worker=SerialTaskQueue("downloads-screen",10L)
    private val main=Handler(Looper.getMainLooper())
    private lateinit var list: ListView
    private lateinit var store: OfflineStore
    private var rows=emptyList<OfflineDownload>()
    private lateinit var adapter: DownloadListAdapter
    private lateinit var empty: TextView
    private lateinit var summary: TextView
    private var filter=0
    private val chips=mutableListOf<Button>()
    private var sheet: Dialog?=null
    private var resumed=false
    private var lastCleanupAt=0L
    @Volatile private var refreshInFlight=false
    private val ticker=object: Runnable {
        override fun run() {
            refresh()
            if(resumed) main.postDelayed(this, if(rows.any { it.status in ACTIVE_STATUSES } || DownloadService.running) 1_500L else 6_000L)
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store=OfflineStore(applicationContext)
        filter=state?.getInt("filter")?.coerceIn(0,3) ?: 0
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(AppTheme.background(this@DownloadsActivity)) }
        root.offlineSystemInsets()
        val toolbar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(4),0,dp(8),0) }
        toolbar.addView(IconButton(this,null,android.R.attr.borderlessButtonStyle).apply {
            setIcon(R.drawable.ic_ui_back); contentDescription=getString(R.string.ui_back); setOnClickListener { finish() }
        },LinearLayout.LayoutParams(dp(48),dp(56)))
        toolbar.addView(TextView(this).apply { text=getString(R.string.ui_downloads); textSize=22f; setTextColor(AppTheme.primary(this@DownloadsActivity)); setTypeface(null,android.graphics.Typeface.BOLD) },LinearLayout.LayoutParams(0,-2,1f))
        toolbar.addView(IconButton(this,null,android.R.attr.borderlessButtonStyle).apply {
            setIcon(R.drawable.ic_ui_info); contentDescription=getString(R.string.ui_download_storage_information)
            setOnClickListener {
                sheet?.dismiss()
                sheet=ActionSheet.show(this@DownloadsActivity,getString(R.string.ui_offline_storage),listOf(
                    ActionSheet.Action(getString(R.string.ui_save_to_device),getString(R.string.offline_keep_file),R.drawable.ic_ui_download),
                    ActionSheet.Action(getString(R.string.ui_temporary_save),getString(R.string.ui_automatically_deleted_30_days_after_download_completes),R.drawable.ic_ui_timer)
                )) { }
            }
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(toolbar)
        summary=TextView(this).apply { textSize=13f; setTextColor(AppTheme.secondary(this@DownloadsActivity)); setPadding(dp(20),dp(8),dp(20),dp(12)) }
        root.addView(summary)
        val filters=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(16),0,dp(16),dp(8)) }
        listOf(getString(R.string.ui_all),"Video","MP3",getString(R.string.ui_temporary)).forEachIndexed { index,label ->
            val chip=Button(this,null,android.R.attr.borderlessButtonStyle).apply {
                text=label; textSize=13f; isAllCaps=false; minWidth=0; minHeight=0; setPadding(dp(16),0,dp(16),0)
                setOnClickListener { filter=index; display() }
            }
            chips+=chip; filters.addView(chip,LinearLayout.LayoutParams(-2,dp(48)).apply { marginEnd=dp(8) })
        }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(filters) })
        adapter=DownloadListAdapter(this,::options)
        list=ListView(this).apply { divider=null; dividerHeight=0; adapter=this@DownloadsActivity.adapter }
        val content=FrameLayout(this); content.addView(list,FrameLayout.LayoutParams(-1,-1))
        empty=TextView(this).apply { gravity=Gravity.CENTER; textSize=16f; setTextColor(AppTheme.secondary(this@DownloadsActivity)); setPadding(dp(32),0,dp(32),0) }
        content.addView(empty,FrameLayout.LayoutParams(-1,-1))
        root.addView(content,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
        list.setOnItemClickListener { _,_,position,_ -> adapter.items.getOrNull(position)?.let { if(it.status=="completed") play(it) else options(it) } }
        display()
    }
    private fun refresh() {
        // Never let the 1.5 s active-download ticker build an I/O backlog if storage is slow.
        if (refreshInFlight) return
        refreshInFlight=true
        runCatching { worker.execute {
            try {
                val now=System.currentTimeMillis()
                if(now-lastCleanupAt>=60_000L) {
                    store.cleanup(now)
                    lastCleanupAt=now
                }
                if(!DownloadService.running) store.all().filter { it.status in setOf("downloading","processing") }.forEach {
                    store.put(it.copy(status="failed",error=getString(R.string.download_interrupted)))
                }
                val items=store.all()
                main.post {
                    if(!resumed || isDestroyed) return@post
                    rows=items
                    display()
                }
            } finally { refreshInFlight=false }
        } }.onFailure { refreshInFlight=false }
    }
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun display() {
        val items=rows.filter { when(filter) { 1 -> !it.mp3; 2 -> it.mp3; 3 -> it.temporary; else -> true } }
        chips.forEachIndexed { index,button ->
            if(button.background is GradientDrawable && button.isSelected==(index==filter)) return@forEachIndexed
            button.isSelected=index==filter
            button.setTextColor(if(index==filter) AppTheme.selectedText(this@DownloadsActivity) else AppTheme.primary(this@DownloadsActivity))
            button.background=GradientDrawable().apply { setColor(if(index==filter) AppTheme.selectedSurface(this@DownloadsActivity) else AppTheme.control(this@DownloadsActivity)); cornerRadius=dp(10).toFloat() }
        }
        val active=rows.count { it.status in ACTIVE_STATUSES }
        summary.setTextIfChanged(if(active>0) getString(R.string.downloads_active,rows.size,active) else getString(R.string.downloads_summary,rows.size))
        adapter.replace(items)
        empty.setTextIfChanged(if(rows.isEmpty()) getString(R.string.ui_no_downloads_yet_n_nopen_a_video_download_to_save_video_or_mp3) else getString(R.string.ui_nothing_in_this_category_yet_n_nselect_all_to_see_everything))
        empty.visibility=if(items.isEmpty()) View.VISIBLE else View.GONE
    }
    private fun play(job: OfflineDownload) {
        if(job.temporary && DownloadPolicy.expired(job.completedAt,System.currentTimeMillis())) { refresh(); return }
        startActivity(Intent(this,OfflinePlayerActivity::class.java).setData(Uri.parse(job.uri)).putExtra("title",job.title)
            .putExtra("audioOnly",job.mp3).putExtra("videoId",YouTubeAdapter.videoIdFromUrl(job.url)))
    }
    private fun options(job: OfflineDownload) {
        val actions=when(job.status) { "completed" -> arrayOf(getString(R.string.ui_play_offline),if(job.temporary) getString(R.string.ui_delete_temporary_file) else getString(R.string.ui_remove_from_list))
            "queued" -> if(DownloadService.running) arrayOf(getString(R.string.ui_cancel)) else arrayOf(getString(R.string.ui_retry),getString(R.string.ui_cancel))
            "downloading","processing" -> arrayOf(getString(R.string.ui_cancel))
            "expired" -> arrayOf(getString(R.string.ui_download_again),getString(R.string.ui_remove_from_list))
            else -> arrayOf(getString(R.string.ui_retry),getString(R.string.ui_details),getString(R.string.ui_remove_from_list)) }
        sheet?.dismiss()
        sheet=ActionSheet.show(this,job.title,actions.map { action -> ActionSheet.Action(action,
            if(action==getString(R.string.ui_remove_from_list) && !job.temporary) getString(R.string.ui_keep_the_file_on_your_device) else "",
            when(action) { getString(R.string.ui_play_offline) -> R.drawable.ic_ui_play; getString(R.string.ui_retry),getString(R.string.ui_download_again) -> R.drawable.ic_ui_download; getString(R.string.ui_details) -> R.drawable.ic_ui_info; else -> R.drawable.ic_ui_close },
            action in setOf(getString(R.string.ui_cancel),getString(R.string.ui_delete_temporary_file),getString(R.string.ui_remove_from_list))) },job.quality) { index -> when(actions[index]) {
            getString(R.string.ui_play_offline) -> play(job)
            getString(R.string.ui_cancel) -> startService(Intent(this,DownloadService::class.java).setAction("cancel").putExtra("id",job.id))
            getString(R.string.ui_retry),getString(R.string.ui_download_again) -> worker.execute {
                store.clearFiles(job.id); store.put(job.copy(status="queued",progress=0,error="",completedAt=0,uri=if(job.temporary) "" else job.uri))
                main.post { if(resumed && !isDestroyed && !isFinishing) DownloadService.enqueue(this,job.id) }
            }
            getString(R.string.ui_details) -> AlertDialog.Builder(this).setMessage(DownloadFailure.localized(this,job.error.ifBlank { getString(R.string.ui_download_incomplete_tap_to_retry) })).setPositiveButton(getString(R.string.ui_ok),null).show()
            else -> worker.execute { store.remove(job.id); main.post { if(!isDestroyed) refresh() } }
        } }
    }
    override fun onResume() { super.onResume(); resumed=true; main.removeCallbacks(ticker); main.post(ticker) }
    override fun onPause() { resumed=false; main.removeCallbacks(ticker); super.onPause() }
    override fun onSaveInstanceState(state: Bundle) { state.putInt("filter",filter); super.onSaveInstanceState(state) }
    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); if(::adapter.isInitialized) adapter.trimMemory(level) }
    override fun onDestroy() { sheet?.dismiss(); adapter.close(); main.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy() }
    companion object { private val ACTIVE_STATUSES=setOf("queued","downloading","processing") }
}
