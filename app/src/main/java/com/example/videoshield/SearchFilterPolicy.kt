package com.example.videoshield

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/**
 * Small, dependency-free representation of YouTube search filters.
 *
 * YouTube exposes search filter state through the opaque `sp` query parameter. The short
 * tokens used for the common filters are a tiny protobuf message. Encoding the small subset
 * we expose here lets the native search chrome stay in sync without scraping result cards or
 * issuing a second search request.
 */
enum class SearchResultType(val wireValue: Int) {
    ALL(0), VIDEOS(1), CHANNELS(2), PLAYLISTS(3), SHORTS(9)
}

enum class SearchPrioritize(val wireValue: Int) {
    RELEVANCE(0), NEWEST(2), POPULARITY(3)
}

enum class SearchUploadDate(val wireValue: Int) {
    ANY(0), TODAY(2), THIS_WEEK(3), THIS_MONTH(4), THIS_YEAR(5)
}

enum class SearchDuration(val wireValue: Int) {
    ANY(0), UNDER_3_MIN(1), OVER_20_MIN(2), THREE_TO_20_MIN(3)
}

data class SearchFilterState(
    val type: SearchResultType = SearchResultType.ALL,
    val prioritize: SearchPrioritize = SearchPrioritize.RELEVANCE,
    val uploadDate: SearchUploadDate = SearchUploadDate.ANY,
    val duration: SearchDuration = SearchDuration.ANY
) {
    val isDefault: Boolean get() = this == SearchFilterState()
}

object SearchFilterPolicy {
    fun buildUrl(query: String, state: SearchFilterState): String {
        val clean = query.trim().replace(Regex("\\s+"), " ").take(160)
        if (clean.isBlank()) return "https://m.youtube.com/"
        val base = "https://m.youtube.com/results?search_query=" + URLEncoder.encode(clean, "UTF-8")
        val token = encode(state)
        return if (token.isBlank()) base else "$base&sp=" + URLEncoder.encode(token, "UTF-8")
    }

    fun encode(state: SearchFilterState): String {
        if (state.isDefault) return ""
        val outer = ArrayList<Byte>(12)
        if (state.prioritize != SearchPrioritize.RELEVANCE) {
            outer += 0x08.toByte()
            outer += state.prioritize.wireValue.toByte()
        }

        val filters = ArrayList<Byte>(8)
        if (state.uploadDate != SearchUploadDate.ANY) {
            filters += 0x08.toByte()
            filters += state.uploadDate.wireValue.toByte()
        }
        if (state.type != SearchResultType.ALL) {
            filters += 0x10.toByte()
            filters += state.type.wireValue.toByte()
        }
        if (state.duration != SearchDuration.ANY) {
            filters += 0x18.toByte()
            filters += state.duration.wireValue.toByte()
        }
        if (filters.isNotEmpty()) {
            outer += 0x12.toByte()
            outer += filters.size.toByte()
            outer.addAll(filters)
        }
        if (outer.isEmpty()) return ""
        return Base64.getEncoder().encodeToString(outer.toByteArray())
    }

    fun decode(rawToken: String?): SearchFilterState {
        val raw = rawToken.orEmpty().trim().replace(' ', '+')
        val token = if ('%' in raw) runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw) else raw
        if (token.isBlank()) return SearchFilterState()
        val bytes = runCatching { Base64.getDecoder().decode(token) }.getOrNull() ?: return SearchFilterState()
        var prioritize = SearchPrioritize.RELEVANCE
        var type = SearchResultType.ALL
        var upload = SearchUploadDate.ANY
        var duration = SearchDuration.ANY

        var index = 0
        while (index < bytes.size) {
            val tag = readVarint(bytes, index) ?: break
            index = tag.next
            val field = tag.value ushr 3
            val wire = tag.value and 7
            when {
                field == 1 && wire == 0 -> {
                    val value = readVarint(bytes, index) ?: break
                    index = value.next
                    prioritize = SearchPrioritize.values().firstOrNull { it.wireValue == value.value } ?: prioritize
                }
                field == 2 && wire == 2 -> {
                    val length = readVarint(bytes, index) ?: break
                    index = length.next
                    val end = (index + length.value).coerceAtMost(bytes.size)
                    var nested = index
                    while (nested < end) {
                        val nestedTag = readVarint(bytes, nested) ?: break
                        nested = nestedTag.next
                        if ((nestedTag.value and 7) != 0) {
                            nested = skip(bytes, nested, nestedTag.value and 7, end) ?: end
                            continue
                        }
                        val value = readVarint(bytes, nested) ?: break
                        nested = value.next
                        when (nestedTag.value ushr 3) {
                            1 -> upload = SearchUploadDate.values().firstOrNull { it.wireValue == value.value } ?: upload
                            2 -> type = SearchResultType.values().firstOrNull { it.wireValue == value.value } ?: type
                            3 -> duration = SearchDuration.values().firstOrNull { it.wireValue == value.value } ?: duration
                        }
                    }
                    index = end
                }
                else -> index = skip(bytes, index, wire, bytes.size) ?: break
            }
        }
        return SearchFilterState(type, prioritize, upload, duration)
    }

    private data class Varint(val value: Int, val next: Int)

    private fun readVarint(bytes: ByteArray, start: Int): Varint? {
        var result = 0
        var shift = 0
        var index = start
        while (index < bytes.size && shift <= 28) {
            val value = bytes[index].toInt() and 0xFF
            result = result or ((value and 0x7F) shl shift)
            index++
            if (value and 0x80 == 0) return Varint(result, index)
            shift += 7
        }
        return null
    }

    private fun skip(bytes: ByteArray, start: Int, wire: Int, limit: Int): Int? = when (wire) {
        0 -> readVarint(bytes, start)?.next
        1 -> (start + 8).takeIf { it <= limit }
        2 -> readVarint(bytes, start)?.let { (it.next + it.value).takeIf { end -> end <= limit } }
        5 -> (start + 4).takeIf { it <= limit }
        else -> null
    }
}
