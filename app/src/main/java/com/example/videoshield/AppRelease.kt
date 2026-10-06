package com.example.videoshield

import org.json.JSONObject
import java.net.URI

/** Public stable releases only; no GitHub token is embedded in the application. */
data class AppRelease(
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val abi: String,
    val assetName: String,
    val apkUrl: String,
    val size: Long,
    val sha256: String,
    val notes: String
) {
    companion object {
        const val REPOSITORY = "thang199801666/MyWife"
        const val RELEASES_URL = "https://github.com/$REPOSITORY/releases"
        const val LATEST_API = "https://api.github.com/repos/$REPOSITORY/releases/latest"
        const val MAX_APK_BYTES = 150L * 1024 * 1024

        fun manifestUrl(release: JSONObject): String {
            validateStable(release)
            return asset(release, "update.json").getString("browser_download_url").also {
                require(it == assetUrl(release.getString("tag_name"), "update.json"))
            }
        }

        fun parse(release: JSONObject, manifest: JSONObject, packageName: String): AppRelease {
            validateStable(release)
            require(manifest.getInt("schemaVersion") == 1)
            require(manifest.getString("packageName") == packageName)
            val name = manifest.getString("versionName")
            val tag = release.getString("tag_name")
            require(tag == "v$name")
            val code = manifest.getLong("versionCode")
            val sdk = manifest.getInt("minSdk")
            val abi = manifest.getString("abi")
            val file = manifest.getString("assetName")
            val size = manifest.getLong("size")
            val hash = manifest.getString("sha256").lowercase(java.util.Locale.ROOT)
            require(code > 0 && sdk >= 26 && abi == "arm64-v8a")
            require(file == "VoTui-v$name-release-$abi.apk")
            require(size in 1..MAX_APK_BYTES && hash.matches(Regex("[a-f0-9]{64}")))
            val apk = asset(release, file)
            val url = apk.getString("browser_download_url")
            require(url == assetUrl(tag, file) && apk.getLong("size") == size)
            return AppRelease(code, name, sdk, abi, file, url, size, hash,
                release.optString("body").take(6000))
        }

        private fun validateStable(release: JSONObject) {
            require(!release.optBoolean("draft") && !release.optBoolean("prerelease"))
            require(release.getString("tag_name").matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")))
        }

        private fun asset(release: JSONObject, name: String): JSONObject {
            val assets = release.getJSONArray("assets")
            val matches = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.optString("name") == name && it.optString("state") == "uploaded" }
            require(matches.size == 1) { "Release asset missing or duplicated" }
            return matches.single()
        }

        private fun assetUrl(tag: String, file: String) =
            "https://github.com/$REPOSITORY/releases/download/$tag/$file"

        fun trustedDownloadHop(url: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme == "https" && uri.userInfo == null && uri.fragment == null &&
                (uri.port == -1 || uri.port == 443) && uri.host in setOf(
                    "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
        }.getOrDefault(false)
    }
}
