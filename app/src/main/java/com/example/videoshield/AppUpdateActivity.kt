package com.example.videoshield

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors

/** Updates are explicitly requested, downloaded privately, then handed to Android for approval. */
class AppUpdateActivity : LocalizedActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var client: AppReleaseClient
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var action: Button
    private lateinit var progress: ProgressBar
    private var release: AppRelease? = null
    private var downloaded: File? = null
    private var busy = false
    private var waitingForPermission = false
    private var autoDownloadRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = AppReleaseClient(applicationContext)
        autoDownloadRequested = intent.getBooleanExtra(EXTRA_AUTO_DOWNLOAD, false)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(AppTheme.background(this@AppUpdateActivity))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        SystemBarInsets.install(this)
        root.addView(TextView(this).apply {
            text = getString(R.string.app_update_title)
            textSize = 24f
            setTextColor(AppTheme.primary(this@AppUpdateActivity))
        })
        root.addView(TextView(this).apply {
            val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
            text = getString(R.string.app_update_current, version)
            textSize = 14f
            setTextColor(AppTheme.secondary(this@AppUpdateActivity))
            setPadding(0, dp(12), 0, dp(16))
        })
        status = TextView(this).apply { textSize = 18f; setTextColor(AppTheme.primary(this@AppUpdateActivity)) }
        root.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = android.view.View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(16) })
        details = TextView(this).apply {
            textSize = 14f
            setTextColor(AppTheme.secondary(this@AppUpdateActivity))
            setPadding(0, dp(16), 0, dp(16))
        }
        root.addView(details)
        action = Button(this).apply {
            isAllCaps = false
            text = getString(R.string.app_update_check)
            setOnClickListener {
                when {
                    busy -> Unit
                    downloaded != null -> requestInstall()
                    release != null -> download()
                    else -> checkRelease()
                }
            }
        }
        root.addView(action, LinearLayout.LayoutParams(-1, -2))
        root.addView(Button(this).apply {
            isAllCaps = false
            text = getString(R.string.app_update_releases)
            setOnClickListener {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppRelease.RELEASES_URL))) }
                    .onFailure { status.setText(R.string.app_update_browser_missing) }
            }
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(Button(this).apply {
            isAllCaps = false
            text = getString(R.string.ui_close)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, -2))
        checkRelease()
    }

    private fun checkRelease() {
        busy = true
        action.isEnabled = false
        progress.visibility = android.view.View.VISIBLE
        progress.isIndeterminate = true
        status.setText(R.string.app_update_checking)
        details.text = ""
        worker.execute {
            val result = runCatching { client.latest() }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                busy = false
                progress.visibility = android.view.View.GONE
                action.isEnabled = true
                if (result.isFailure) {
                    autoDownloadRequested = false
                    status.setText(R.string.app_update_check_failed)
                    action.setText(R.string.app_update_retry)
                    return@runOnUiThread
                }
                val next = result.getOrNull()
                val installed = client.versionCode(packageManager.getPackageInfo(packageName, 0))
                when (AppUpdatePolicy.availability(next, installed, Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.asList())) {
                    AppUpdateAvailability.NO_RELEASE -> {
                        autoDownloadRequested = false
                        status.setText(R.string.app_update_no_release)
                    }
                    AppUpdateAvailability.UP_TO_DATE -> {
                        autoDownloadRequested = false
                        status.setText(R.string.app_update_up_to_date)
                    }
                    AppUpdateAvailability.INCOMPATIBLE -> {
                        autoDownloadRequested = false
                        status.setText(R.string.app_update_incompatible)
                    }
                    AppUpdateAvailability.AVAILABLE -> {
                        val available = next ?: return@runOnUiThread
                        release = available
                        status.text = getString(R.string.app_update_available, available.versionName)
                        details.text = getString(R.string.app_update_size, available.size / 1024 / 1024) +
                            "\n\n" + available.notes
                        action.setText(R.string.app_update_download)
                        if (autoDownloadRequested) {
                            autoDownloadRequested = false
                            download()
                        }
                    }
                }
            }
        }
    }

    private fun download() {
        val next = release ?: return
        busy = true
        action.isEnabled = false
        progress.visibility = android.view.View.VISIBLE
        progress.isIndeterminate = false
        progress.progress = 0
        status.setText(R.string.app_update_downloading)
        worker.execute {
            val result = runCatching {
                client.download(next) { percent -> runOnUiThread {
                    if (!isDestroyed && !isFinishing) progress.progress = percent
                } }
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                busy = false
                action.isEnabled = true
                progress.visibility = android.view.View.GONE
                downloaded = result.getOrNull()
                if (downloaded == null) {
                    status.setText(R.string.app_update_download_failed)
                    action.setText(R.string.app_update_download)
                } else {
                    status.setText(R.string.app_update_ready)
                    action.setText(R.string.app_update_install)
                }
            }
        }
    }

    private fun requestInstall() {
        if (downloaded == null) return
        if (!packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this).setTitle(R.string.app_update_install)
                .setMessage(R.string.app_update_allow_install)
                .setNegativeButton(R.string.ui_cancel, null)
                .setPositiveButton(R.string.app_update_open_settings) { _, _ ->
                    runCatching {
                        waitingForPermission = true
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    }.onFailure { waitingForPermission = false; status.setText(R.string.app_update_install_failed) }
                }.show()
            return
        }
        runCatching {
            val file = downloaded ?: return
            val uri = FileProvider.getUriForFile(this, "$packageName.offline", file)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("update", uri)
            })
        }.onFailure { status.setText(R.string.app_update_install_failed) }
    }

    override fun onResume() {
        super.onResume()
        if (waitingForPermission) {
            waitingForPermission = false
            if (packageManager.canRequestPackageInstalls()) requestInstall()
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object {
        const val EXTRA_AUTO_DOWNLOAD = "com.example.videoshield.extra.AUTO_DOWNLOAD_UPDATE"
    }

}
