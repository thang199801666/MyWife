package com.example.videoshield

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Performs one non-blocking update check for the current app process.
 *
 * A failed check is deliberately silent: connectivity problems must never block startup.
 * Manual update checking remains available from Settings/Library.
 */
object AppStartupUpdateChecker {
    private const val NO_VERSION = Long.MIN_VALUE
    private const val PREFS = "app_update_prompt"
    private const val KEY_DECLINED_VERSION_CODE = "declined_version_code"

    private val checkStarted = AtomicBoolean(false)
    private val promptedVersionCode = AtomicLong(NO_VERSION)
    private val pendingRelease = AtomicReference<AppRelease?>(null)
    private val currentHost = AtomicReference(WeakReference<Activity>(null))
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "app-update-check").apply { isDaemon = true }
    }

    fun check(activity: Activity) {
        currentHost.set(WeakReference(activity))
        pendingRelease.get()?.let { postPromptIfPossible(it) }
        if (!checkStarted.compareAndSet(false, true)) return

        val appContext = activity.applicationContext
        worker.execute {
            val client = AppReleaseClient(appContext)
            val release = runCatching { client.latest() }.getOrNull() ?: return@execute
            val installed = runCatching {
                client.versionCode(appContext.packageManager.getPackageInfo(appContext.packageName, 0))
            }.getOrNull() ?: return@execute

            if (AppUpdatePolicy.availability(
                    release, installed, Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.asList()
                ) != AppUpdateAvailability.AVAILABLE
            ) return@execute

            // "Để sau" means do not ask again for this exact release.
            // A newer release still gets one opportunity to prompt.
            if (isDeclined(appContext, release.versionCode)) return@execute

            pendingRelease.set(release)
            postPromptIfPossible(release)
        }
    }

    private fun postPromptIfPossible(release: AppRelease) {
        val host = currentHost.get().get() ?: return
        host.runOnUiThread {
            val activity = currentHost.get().get() ?: return@runOnUiThread
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            if (!promptedVersionCode.compareAndSet(NO_VERSION, release.versionCode)) return@runOnUiThread
            showPrompt(activity, release)
        }
    }

    private fun showPrompt(activity: Activity, release: AppRelease) {
        val sizeMb = ((release.size + 1024L * 1024L - 1L) / (1024L * 1024L)).coerceAtLeast(1L)
        val message = activity.getString(
            R.string.app_update_startup_message,
            release.versionName,
            sizeMb
        )

        var handled = false
        AlertDialog.Builder(activity)
            .setTitle(R.string.app_update_startup_title)
            .setMessage(message)
            .setNegativeButton(R.string.app_update_later) { _, _ ->
                handled = true
                rememberDeclined(activity, release.versionCode)
            }
            .setPositiveButton(R.string.app_update_now) { _, _ ->
                handled = true
                activity.startActivity(Intent(activity, AppUpdateActivity::class.java).apply {
                    putExtra(AppUpdateActivity.EXTRA_AUTO_DOWNLOAD, true)
                })
            }
            .setOnCancelListener {
                // Back/outside dismiss is equivalent to declining the startup prompt.
                if (!handled) rememberDeclined(activity, release.versionCode)
            }
            .show()
    }

    internal fun shouldSuppressPrompt(releaseVersionCode: Long, declinedVersionCode: Long): Boolean =
        declinedVersionCode != NO_VERSION && releaseVersionCode <= declinedVersionCode

    private fun isDeclined(activity: android.content.Context, releaseVersionCode: Long): Boolean {
        val declined = activity.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getLong(KEY_DECLINED_VERSION_CODE, NO_VERSION)
        return shouldSuppressPrompt(releaseVersionCode, declined)
    }

    private fun rememberDeclined(activity: android.content.Context, releaseVersionCode: Long) {
        val prefs = activity.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val previous = prefs.getLong(KEY_DECLINED_VERSION_CODE, NO_VERSION)
        if (releaseVersionCode > previous) {
            prefs.edit().putLong(KEY_DECLINED_VERSION_CODE, releaseVersionCode).apply()
        }
        pendingRelease.set(null)
    }

}
