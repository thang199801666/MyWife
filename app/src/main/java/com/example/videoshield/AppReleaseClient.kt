package com.example.videoshield

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AppReleaseClient(private val context: Context) {
    fun latest(): AppRelease? {
        val response = connection(AppRelease.LATEST_API, redirects = false)
        val release = try {
            if (response.responseCode == 404) return null
            check(response.responseCode == 200) { "GitHub HTTP ${response.responseCode}" }
            JSONObject(readBounded(response, 1_048_576).toString(Charsets.UTF_8))
        } finally { response.disconnect() }
        val manifest = connection(AppRelease.manifestUrl(release), redirects = true)
        val json = try {
            check(manifest.responseCode == 200)
            JSONObject(readBounded(manifest, 131_072).toString(Charsets.UTF_8))
        } finally { manifest.disconnect() }
        return AppRelease.parse(release, json, context.packageName)
    }

    fun download(release: AppRelease, progress: (Int) -> Unit): File {
        require(Build.VERSION.SDK_INT >= release.minSdk && release.abi in Build.SUPPORTED_ABIS)
        val directory = File(context.cacheDir, "app-updates").apply { mkdirs() }
        val target = File(directory, release.assetName)
        val staging = File(directory, "${release.assetName}.part")
        if (target.isFile) {
            if (runCatching { verify(target, release) }.isSuccess) return target
            target.delete()
        }
        val response = connection(release.apkUrl, redirects = true)
        try {
            check(response.responseCode == 200)
            var received = 0L
            var lastPercent = -1
            response.inputStream.use { input ->
                staging.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        check(!Thread.currentThread().isInterrupted) { "Update cancelled" }
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        check(received <= release.size) { "Update exceeds expected size" }
                        output.write(buffer, 0, count)
                        val percent = (received * 100 / release.size).toInt()
                        if (percent != lastPercent) { lastPercent = percent; progress(percent) }
                    }
                }
            }
            verify(staging, release)
            check(staging.renameTo(target)) { "Could not save update" }
            // Keep only the verified latest package in the private update cache.
            directory.listFiles()?.filter { it != target && it.extension == "apk" }?.forEach { it.delete() }
            return target
        } finally { response.disconnect(); staging.delete() }
    }

    @Suppress("DEPRECATION")
    fun verify(file: File, release: AppRelease) {
        check(file.length() == release.size)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                check(!Thread.currentThread().isInterrupted)
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) } == release.sha256) { "Checksum mismatch" }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("Invalid APK")
        check(archive.packageName == context.packageName && versionCode(archive) == release.versionCode)
        check(archive.versionName == release.versionName && release.versionCode > versionCode(installed))
        val currentSigners = signers(installed)
        check(currentSigners.isNotEmpty() && currentSigners == signers(archive)) { "Signing certificate mismatch" }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { it.toCharsString() }.toSet()
    }

    @Suppress("DEPRECATION")
    fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun readBounded(response: HttpURLConnection, limit: Int): ByteArray =
        response.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                check(!Thread.currentThread().isInterrupted)
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= limit)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }

    private fun connection(url: String, redirects: Boolean): HttpURLConnection {
        var current = URL(url)
        repeat(5) {
            if (redirects) require(AppRelease.trustedDownloadHop(current.toString()))
            else require(current.toString() == AppRelease.LATEST_API)
            val response = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", if (redirects) "application/octet-stream" else "application/vnd.github+json")
                setRequestProperty("User-Agent", "VoTui-Android")
            }
            val code = try { response.responseCode } catch (error: Exception) { response.disconnect(); throw error }
            if (code !in listOf(301, 302, 303, 307, 308)) return response
            val location = response.getHeaderField("Location")
            response.disconnect()
            check(redirects && !location.isNullOrBlank())
            current = URL(current, location)
        }
        error("Too many redirects")
    }
}
