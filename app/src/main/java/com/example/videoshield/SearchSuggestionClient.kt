package com.example.videoshield

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** Only the typed keyword is sent to YouTube's public completion service. */
object SearchSuggestionClient {
    fun parse(payload: String, query: String): List<String> {
        val response = JSONArray(payload)
        if (response.optString(0) != query) return emptyList()
        val values = response.optJSONArray(1) ?: return emptyList()
        return (0 until minOf(values.length(), 20)).mapNotNull { index ->
            (values.opt(index) as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= 160 }
        }.distinct().take(10)
    }

    /**
     * A truly cancellable autocomplete request. Future.cancel(true) alone does not reliably
     * interrupt HttpURLConnection while it is connecting/reading, so query changes also
     * disconnect the active socket immediately.
     */
    class Request internal constructor(
        private val query: String,
        private val language: String
    ) {
        @Volatile private var connection: HttpURLConnection? = null
        @Volatile private var cancelled = false

        fun cancel() {
            cancelled = true
            runCatching { connection?.disconnect() }
            connection = null
        }

        fun execute(): List<String> {
            if (cancelled || Thread.currentThread().isInterrupted) return emptyList()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val locale = if (language == "en") "en" else "vi"
            val conn = URI("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=$locale&q=$encoded")
                .toURL().openConnection() as HttpURLConnection
            connection = conn
            try {
                conn.connectTimeout = 2500
                conn.readTimeout = 2500
                conn.instanceFollowRedirects = false
                if (cancelled || Thread.currentThread().isInterrupted) return emptyList()
                if (conn.responseCode != 200 || cancelled) return emptyList()
                val payload = conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val out = StringBuilder(4096)
                    val chunk = CharArray(2048)
                    while (!cancelled && !Thread.currentThread().isInterrupted) {
                        val read = reader.read(chunk)
                        if (read < 0) break
                        out.append(chunk, 0, read)
                        if (out.length > 32_768) return emptyList()
                    }
                    if (cancelled || Thread.currentThread().isInterrupted) return emptyList()
                    out.toString()
                }
                return parse(payload, query)
            } finally {
                if (connection === conn) connection = null
                conn.disconnect()
            }
        }
    }

    fun request(query: String, language: String = "vi"): Request = Request(query, language)

    fun load(query: String, language: String = "vi"): List<String> = request(query, language).execute()
}
