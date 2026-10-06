package com.example.videoshield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import java.io.File
import java.util.concurrent.Executors

class DownloadService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var store: OfflineStore
    private val scheduled = mutableSetOf<String>()
    @Volatile private var active = ""
    @Volatile private var cancelled = ""
    private var latestStart = 0
    override fun onCreate() {
        super.onCreate(); running=true; store=OfflineStore(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("downloads",AppLanguage.wrap(this).getString(R.string.ui_downloads),NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStart=startId
        val id=intent?.getStringExtra("id").orEmpty()
        if(intent?.action=="cancel") {
            cancelled=id; DownloadEngine.cancel(id)
            store.get(id)?.takeIf { it.status in setOf("queued","downloading","processing") }?.let { store.put(it.copy(status="cancelled")) }
            if(scheduled.isEmpty()) stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(909,notification(AppLanguage.wrap(this).getString(R.string.ui_download_preparing),0))
        if(id.isBlank() || !scheduled.add(id)) return START_NOT_STICKY
        worker.execute {
            var job=store.get(id)
            if(job != null && job.status=="queued") {
                active=id; cancelled=""; var pendingExport: Uri?=null; var managedExport=false
                try {
                    job=job.copy(status="downloading",error=""); store.put(job)
                    val running=job; var lastUpdate=0L
                    val file=DownloadEngine.download(this,running,store.directory(id)) { percent ->
                        if(cancelled==id) throw InterruptedException("Cancelled")
                        val now=System.currentTimeMillis()
                        if(now-lastUpdate>=1000) {
                            lastUpdate=now; val value=percent.toInt().coerceIn(0,99)
                            store.put(running.copy(progress=value))
                            getSystemService(NotificationManager::class.java).notify(909,notification(running.title,value))
                        }
                    }
                    if(cancelled==id) throw InterruptedException("Cancelled")
                    store.put(running.copy(status="processing",progress=99))
                    val uri = if(running.temporary) {
                        androidx.core.content.FileProvider.getUriForFile(this,"$packageName.offline",file)
                    } else {
                        val target=if(running.uri.isNotBlank()) Uri.parse(running.uri) else if(Build.VERSION.SDK_INT>=29) {
                            contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME,DownloadPolicy.safeName(running.title)+"."+file.extension)
                                put(MediaStore.MediaColumns.MIME_TYPE,if(running.mp3) "audio/mpeg" else "video/x-matroska")
                                put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/VoTuibe")
                                put(MediaStore.MediaColumns.IS_PENDING,1)
                            }) ?: error("Không thể tạo file trong Downloads.")
                        } else error("Chọn vị trí lưu file trên thiết bị.")
                        pendingExport=target
                        managedExport=Build.VERSION.SDK_INT>=29 && target.authority==MediaStore.AUTHORITY
                        // Keep the destination recoverable if Android terminates
                        // the service during the copy to shared storage.
                        job=running.copy(status="processing",progress=99,uri=target.toString())
                        store.put(job)
                        contentResolver.openOutputStream(target,"w").use { output ->
                            requireNotNull(output); file.inputStream().use { input ->
                                val buffer=ByteArray(128*1024)
                                while(true) {
                                    if(cancelled==id) throw InterruptedException("Cancelled")
                                    val count=input.read(buffer); if(count<0) break
                                    output.write(buffer,0,count)
                                }
                            }
                        }
                        if(managedExport) contentResolver.update(target,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
                        pendingExport=null; store.clearFiles(id); target
                    }
                    store.put(running.copy(status="completed",progress=100,completedAt=System.currentTimeMillis(),uri=uri.toString()))
                } catch(e: Exception) {
                    if(managedExport) pendingExport?.let { runCatching { contentResolver.delete(it,null,null) } }
                    store.clearFiles(id)
                    job?.let { store.put(it.copy(status=if(cancelled==id) "cancelled" else "failed",uri=if(managedExport && pendingExport!=null) "" else it.uri,error=DownloadFailure.describe(e))) }
                } finally { active="" }
            }
            android.os.Handler(mainLooper).post {
                scheduled.remove(id)
                if(scheduled.isEmpty()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(latestStart) }
            }
        }
        return START_NOT_STICKY
    }
    private fun notification(title: String, progress: Int): Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,DownloadsActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=Notification.Builder(this,"downloads").setSmallIcon(R.drawable.ic_ui_download)
            .setContentTitle(title.take(100)).setContentText(AppLanguage.wrap(this).getString(R.string.download_progress,progress)).setContentIntent(open)
            .setOngoing(true).setProgress(100,progress,progress==0)
        if(active.isNotBlank()) {
            val cancel=PendingIntent.getService(this,1,Intent(this,DownloadService::class.java).setAction("cancel").putExtra("id",active),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null,AppLanguage.wrap(this).getString(R.string.ui_cancel),cancel).build())
        }
        return builder.build()
    }
    override fun onDestroy() { running=false; if(active.isNotBlank()) DownloadEngine.cancel(active); worker.shutdownNow(); super.onDestroy() }
    override fun onTimeout(startId: Int, fgsType: Int) { if(active.isNotBlank()) cancelled=active; stopSelf() }
    companion object {
        @Volatile var running=false
            private set
        fun enqueue(context: Context, id: String) { context.startForegroundService(Intent(context,DownloadService::class.java).putExtra("id",id)) }
    }
}
