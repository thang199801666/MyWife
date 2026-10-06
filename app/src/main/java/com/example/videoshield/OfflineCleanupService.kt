package com.example.videoshield

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

class OfflineCleanupService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread { runCatching { OfflineStore(this).cleanup() }; jobFinished(params,false) }.start()
        return true
    }
    override fun onStopJob(params: JobParameters) = true
    companion object {
        fun schedule(context: Context) {
            val scheduler=context.getSystemService(JobScheduler::class.java)
            if(scheduler.getPendingJob(910)==null) scheduler.schedule(JobInfo.Builder(910,ComponentName(context,OfflineCleanupService::class.java))
                .setPeriodic(24L*60*60*1000).setPersisted(true).build())
        }
    }
}
