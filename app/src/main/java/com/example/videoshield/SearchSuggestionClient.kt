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

    fun load(query: String, language: String = "vi"): List<String> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val locale = if (language == "en") "en" else "vi"
        val connection = URI("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=$locale&q=$encoded")
            .toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 2500; connection.readTimeout = 2500
            connection.instanceFollowRedirects = false
            if (connection.responseCode != 200) return emptyList()
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(32_769)
                var count = 0
                while (count < buffer.size) {
                    val read = reader.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                if (count > 32_768) emptyList() else parse(String(buffer, 0, count), query)
            }
        } finally { connection.disconnect() }
    }
}
