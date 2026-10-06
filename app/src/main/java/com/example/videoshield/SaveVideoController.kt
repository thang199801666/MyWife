package com.example.videoshield

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.UUID
import java.util.concurrent.Executors

class SaveVideoController(private val activity: Activity) {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var closed=false
    @Volatile private var inspection=""
    private var dialog: AlertDialog?=null
    private var sheet: android.app.Dialog?=null
    fun show(url: String, title: String) {
        sheet?.dismiss()
        sheet=ActionSheet.show(activity,activity.getString(R.string.ui_download),listOf(
            ActionSheet.Action(activity.getString(R.string.ui_save_to_device),activity.getString(R.string.offline_save_device),R.drawable.ic_ui_download),
            ActionSheet.Action(activity.getString(R.string.ui_temporary_save),activity.getString(R.string.ui_watch_offline_in_the_app_automatically_deleted_after_30_days),R.drawable.ic_ui_timer)
        ),title) { index ->
                val temporary=index==1
                sheet=ActionSheet.show(activity,if(temporary) activity.getString(R.string.ui_temporary_save) else activity.getString(R.string.ui_save_to_device),listOf(
                    ActionSheet.Action(activity.getString(R.string.ui_video),activity.getString(R.string.ui_choose_a_resolution_from_the_source),R.drawable.ic_ui_play),
                    ActionSheet.Action("MP3",activity.getString(R.string.ui_audio_only_choose_quality),R.drawable.ic_ui_audio)
                )) { type ->
                        if(type==1) choose(url,title,temporary,true,DownloadPolicy.mp3Qualities)
                        else inspect(url,title,temporary)
                }
        }
    }
    private fun inspect(url: String, title: String, temporary: Boolean) {
        val id=UUID.randomUUID().toString(); inspection=id
        dialog=AlertDialog.Builder(activity).setTitle(activity.getString(R.string.ui_reading_source_quality))
            .setMessage(activity.getString(R.string.ui_quality_is_read_from_this_video))
            .setNegativeButton(activity.getString(R.string.ui_cancel)) { _, _ -> inspection=""; DownloadEngine.cancel(id) }.create()
        dialog?.setOnCancelListener { inspection=""; DownloadEngine.cancel(id) }; dialog?.show()
        worker.execute {
            if(closed || inspection!=id) return@execute
            val result=runCatching { DownloadEngine.inspect(activity.applicationContext,url,id) }
            main.post {
                if(closed || activity.isDestroyed || activity.isFinishing || inspection!=id) return@post
                dialog?.dismiss(); inspection=""
                result.onSuccess { choose(url,it.title.ifBlank { title },temporary,false,it.qualities) }
                    .onFailure { AlertDialog.Builder(activity).setTitle(activity.getString(R.string.ui_could_not_read_the_download_source))
                        .setMessage(DownloadFailure.describe(it,activity)).setPositiveButton(activity.getString(R.string.ui_ok),null).show() }
            }
        }
    }
    private fun choose(url: String,title: String,temporary: Boolean,mp3: Boolean,qualities: List<DownloadQuality>) {
        sheet=ActionSheet.show(activity,if(mp3) activity.getString(R.string.ui_mp3_quality) else activity.getString(R.string.ui_video_quality),
            qualities.mapIndexed { index, quality -> ActionSheet.Action(LocalizedPresentation.quality(activity,quality.label),
                if(index==0) activity.getString(R.string.ui_highest_quality_from_source_file_may_be_larger) else "",
                if(mp3) R.drawable.ic_ui_audio else R.drawable.ic_ui_play) },title) { index ->
                val quality=qualities[index]
                val job=OfflineDownload(url=url,title=title,temporary=temporary,mp3=mp3,selector=quality.selector,
                    audioQuality=quality.audioQuality,quality=quality.label,
                    status=if(!temporary && Build.VERSION.SDK_INT<29) "destination" else "queued")
                worker.execute {
                    val store=OfflineStore(activity.applicationContext); store.cleanup(); store.put(job)
                    main.post {
                        if(closed || activity.isDestroyed) return@post
                        if(job.status=="destination") {
                            activity.getSharedPreferences("offline_ui",0).edit().putString("destination",job.id).apply()
                            activity.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE); type=if(mp3) "audio/mpeg" else "video/x-matroska"
                                putExtra(Intent.EXTRA_TITLE,DownloadPolicy.safeName(title)+if(mp3) ".mp3" else ".mkv")
                            },REQUEST_DESTINATION)
                        } else {
                            DownloadService.enqueue(activity,job.id)
                            Toast.makeText(activity,activity.getString(R.string.ui_added_to_downloads),Toast.LENGTH_SHORT).show()
                            activity.startActivity(Intent(activity,DownloadsActivity::class.java))
                        }
                    }
                }
            }
    }
    fun destination(result: Int,data: Intent?) {
        val preferences=activity.getSharedPreferences("offline_ui",0)
        val id=preferences.getString("destination","").orEmpty()
        preferences.edit().remove("destination").apply()
        worker.execute {
            val store=OfflineStore(activity.applicationContext); val job=store.get(id) ?: return@execute
            val uri=data?.data
            if(result!=Activity.RESULT_OK || uri==null) { store.put(job.copy(status="cancelled")); return@execute }
            runCatching { activity.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            store.put(job.copy(status="queued",uri=uri.toString()))
            main.post { if(!closed && !activity.isDestroyed) { DownloadService.enqueue(activity,id); activity.startActivity(Intent(activity,DownloadsActivity::class.java)) } }
        }
    }
    fun close() { closed=true; if(inspection.isNotBlank()) DownloadEngine.cancel(inspection); dialog?.dismiss(); sheet?.dismiss(); worker.shutdownNow(); main.removeCallbacksAndMessages(null) }
    companion object { const val REQUEST_DESTINATION=491 }
}
