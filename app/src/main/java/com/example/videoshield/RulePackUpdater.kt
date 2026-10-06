package com.example.videoshield

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL

object RulePackUpdater {
    private const val MAX_BYTES = 256 * 1024

    fun update(urlText: String, manager: RulePackManager, callback: (RulePackManager.InstallResult) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        Thread {
            val result = try {
                val url = URL(urlText.trim())
                require(url.protocol.equals("https", true)) { "Rule updates require HTTPS" }
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "YouTooBee/0.3")
                }
                try {
                    val code = connection.responseCode
                    require(code in 200..299) { "HTTP $code" }
                    val declared = connection.contentLengthLong
                    require(declared <= 0 || declared <= MAX_BYTES) { "Rule pack is too large" }
                    val bytes = connection.inputStream.use { input ->
                        val buffer = ByteArray(8 * 1024)
                        val out = java.io.ByteArrayOutputStream()
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            out.write(buffer, 0, n)
                            require(out.size() <= MAX_BYTES) { "Rule pack is too large" }
                        }
                        out.toByteArray()
                    }
                    manager.install(bytes.toString(Charsets.UTF_8))
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                RulePackManager.InstallResult(false, manager.active().ruleVersion, "Update failed: ${e.message.orEmpty()}")
            }
            main.post { callback(result) }
        }.start()
    }
}
