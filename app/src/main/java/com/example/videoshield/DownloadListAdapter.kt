package com.example.videoshield

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Recycles rows and thumbnails while progress changes; never rebuilds the list adapter. */
class DownloadListAdapter(private val activity: Activity, private val options: (OfflineDownload) -> Unit): BaseAdapter() {
    private val thumbnails=VideoThumbnailLoader()
    var items=emptyList<OfflineDownload>(); private set
    fun replace(value: List<OfflineDownload>) { if(items!=value) { items=value; notifyDataSetChanged() } }
    fun close()=thumbnails.close()
    override fun getCount()=items.size
    override fun getItem(position: Int)=items[position]
    override fun getItemId(position: Int)=position.toLong()
    private fun dp(value: Int)=(value*activity.resources.displayMetrics.density).toInt()
    private fun rounded(color: Int,radius: Int=12)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private class State(val text: String,val active: Boolean=false,val failed: Boolean=false)
    private fun state(job: OfflineDownload)=when(job.status) {
        "completed" -> State(activity.getString(R.string.ui_ready_to_play_offline))
        "queued" -> State(activity.getString(R.string.ui_waiting_to_download),true)
        "downloading" -> State(activity.getString(R.string.download_progress,job.progress),true)
        "processing" -> State(activity.getString(R.string.ui_processing_and_saving_file),true)
        "expired" -> State(activity.getString(R.string.ui_expired_download_again_to_watch))
        "cancelled" -> State(activity.getString(R.string.ui_cancelled))
        "destination" -> State(activity.getString(R.string.ui_save_location_not_selected))
        else -> State(activity.getString(R.string.ui_download_incomplete_tap_to_retry),failed=true)
    }
    private inner class Holder {
        val root=LinearLayout(activity).apply { gravity=Gravity.TOP; setPadding(dp(16),dp(12),dp(8),dp(12)) }
        val image=ImageView(activity).apply { scaleType=ImageView.ScaleType.CENTER_CROP; background=rounded(Color.rgb(40,40,40)); clipToOutline=true }
        val badge=TextView(activity).apply { textSize=10f; setTextColor(Color.WHITE); background=rounded(0xcc000000.toInt(),4); setPadding(dp(4),dp(2),dp(4),dp(2)) }
        val title=TextView(activity).apply { textSize=14f; setTextColor(Color.WHITE); maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END; setTypeface(null,Typeface.BOLD) }
        val detail=TextView(activity).apply { textSize=11f; setTextColor(Color.LTGRAY); maxLines=2; setPadding(0,dp(5),0,0) }
        val status=TextView(activity).apply { textSize=11f; setPadding(0,dp(6),0,dp(4)) }
        val progress=ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal).apply { max=100; progressTintList=ColorStateList.valueOf(Color.rgb(255,51,88)); progressBackgroundTintList=ColorStateList.valueOf(Color.rgb(55,55,55)) }
        val more=IconButton(activity,null,android.R.attr.borderlessButtonStyle).apply { setIcon(R.drawable.ic_ui_more); isFocusable=false }
        init {
            val preview=FrameLayout(activity)
            preview.addView(image,FrameLayout.LayoutParams(-1,-1))
            preview.addView(badge,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.END).apply { bottomMargin=dp(4); marginEnd=dp(4) })
            root.addView(preview,LinearLayout.LayoutParams(dp(124),dp(70)))
            val labels=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),0,0,0); addView(title); addView(detail); addView(status); addView(progress,LinearLayout.LayoutParams(-1,dp(3))) }
            root.addView(labels,LinearLayout.LayoutParams(0,-2,1f)); root.addView(more,LinearLayout.LayoutParams(dp(48),dp(48))); root.tag=this
        }
    }
    override fun getView(position: Int,convertView: View?,parent: ViewGroup): View {
        val holder=convertView?.tag as? Holder ?: Holder()
        val job=items[position]; val state=state(job)
        holder.title.text=job.title; holder.detail.text="${LocalizedPresentation.quality(activity,job.quality)}\n${if(job.temporary) activity.getString(R.string.ui_temporary_30_days) else activity.getString(R.string.ui_on_device)}"
        holder.status.text=state.text
        holder.status.setTextColor(if(state.failed) Color.rgb(255,125,142) else if(state.active) Color.rgb(62,166,255) else Color.LTGRAY)
        holder.badge.text=if(job.mp3) "MP3" else "VIDEO"
        holder.more.contentDescription=activity.getString(R.string.options_for,job.title); holder.more.setOnClickListener { options(job) }
        holder.progress.visibility=if(state.active) View.VISIBLE else View.GONE
        holder.progress.isIndeterminate=job.status=="queued" || job.status=="processing"
        holder.progress.progress=job.progress
        thumbnails.bind(holder.image,YouTubeAdapter.videoIdFromUrl(job.url).orEmpty())
        return holder.root
    }
}
