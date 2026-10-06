package com.example.videoshield

import android.content.Context

/** Translate derived display labels without changing stored IDs or playback policy. */
object LocalizedPresentation {
    fun health(context: Context, state: PlaybackHealthState): String = context.getString(when(state) {
        PlaybackHealthState.IDLE -> R.string.health_idle
        PlaybackHealthState.NAVIGATING -> R.string.health_navigating
        PlaybackHealthState.HEALTHY -> R.string.health_healthy
        PlaybackHealthState.OFFLINE -> R.string.health_offline
        PlaybackHealthState.STALLED -> R.string.health_stalled
        PlaybackHealthState.RECOVERING -> R.string.health_recovering
        PlaybackHealthState.FAILED -> R.string.health_failed
    })
    fun quality(context: Context, value: String): String {
        val height = Regex("^Highest source \\((\\d+)p\\)$").matchEntire(value)?.groupValues?.get(1)
        return when {
            height != null -> context.getString(R.string.download_highest, height)
            value == "Highest source" -> context.getString(R.string.download_highest_unknown)
            value == "Best source → MP3 (VBR)" -> context.getString(R.string.download_best_mp3)
            else -> value
        }
    }
    fun recommendation(context: Context, value: String): String = when {
        value == "From a channel you follow" -> context.getString(R.string.recommend_followed)
        value.startsWith("Because you watch ") -> context.getString(R.string.recommend_watched,value.removePrefix("Because you watch "))
        value.startsWith("Matches your interest: ") -> context.getString(R.string.recommend_interest,value.removePrefix("Matches your interest: "))
        value.startsWith("Matches a recent search: ") -> context.getString(R.string.recommend_search,value.removePrefix("Matches a recent search: "))
        value.startsWith("Similar to: ") -> context.getString(R.string.recommend_similar,value.removePrefix("Similar to: "))
        value == "New discovery from pages you browsed" -> context.getString(R.string.recommend_new)
        else -> value
    }
    /** Localize human-readable diagnostic summaries while preserving technical identifiers. */
    fun diagnosticDetail(context: Context, value: String): String {
        if (AppLanguage.tag(context) == "en" || value.isBlank()) return value
        var text = value
        val replacements = listOf(
            "fixtures passed" to "kiểm tra mẫu đạt",
            "runtime checks passed" to "kiểm tra hoạt động đạt",
            "release stress checks passed" to "kiểm tra độ ổn định đạt",
            "regression fixtures passed" to "kiểm tra hồi quy đạt",
            "passed • failed:" to "đạt • lỗi:",
            "rules v" to "quy tắc v",
            "iterations=" to "số vòng=",
            "states=" to "trạng thái=",
            "invalid queue=" to "hàng đợi lỗi=",
            "invalid history=" to "lịch sử lỗi=",
            "queue=" to "hàng đợi=",
            "conservative background policy" to "chính sách nền thận trọng",
            "resume " to "tiếp tục ",
            "grace " to "thời gian đệm ",
            "Trim memory level" to "Mức thu hồi bộ nhớ",
            "Low-memory callback" to "Cảnh báo thiếu bộ nhớ",
            "Activity foreground" to "Ứng dụng ở foreground",
            "Activity background" to "Ứng dụng ở background",
            "Playback task removed" to "Tác vụ phát đã bị xóa",
            "Stopped stale playback service" to "Đã dừng dịch vụ phát cũ",
            "Device policy detected" to "Đã nhận diện chính sách thiết bị",
            "Runtime self-test PASS" to "Tự kiểm tra hoạt động ĐẠT",
            "Runtime self-test FAIL" to "Tự kiểm tra hoạt động LỖI",
            "Release stress test PASS" to "Kiểm tra độ ổn định bản phát hành ĐẠT",
            "Release stress test FAIL" to "Kiểm tra độ ổn định bản phát hành LỖI",
            "Library check OK" to "Kiểm tra thư viện ĐẠT",
            "Library check FAIL" to "Kiểm tra thư viện LỖI",
            "WebView renderer crashed" to "Renderer WebView bị lỗi",
            "WebView renderer was terminated" to "Renderer WebView đã bị hệ thống kết thúc",
            "Waiting for network" to "Đang chờ kết nối mạng",
            "Connection restored" to "Đã khôi phục kết nối",
            "No validated internet connection" to "Không có kết nối Internet hợp lệ",
            "Player heartbeat stopped" to "Tín hiệu trình phát đã dừng",
            "Automatic recovery exhausted" to "Đã hết số lần tự khôi phục",
            "Main page failed to load" to "Không thể tải trang chính",
            "Playback active" to "Đang phát",
            "Player ready" to "Trình phát đã sẵn sàng",
            "Retrying" to "Đang thử lại"
        )
        replacements.forEach { (from, to) -> text = text.replace(from, to, ignoreCase = false) }
        return text
    }

    fun safeModeReason(context: Context, value: String): String {
        val text = value.trim()
        if (text.isBlank()) return text
        if (text == "Renderer crash-loop protection is active; automatic playback reload recovery is temporarily paused.") {
            return context.getString(R.string.safe_reason_renderer_guard)
        }
        if (text.startsWith("Compatibility guard triggered")) {
            val detail = when {
                "rule version mismatch" in text -> context.getString(R.string.safe_reason_rule_mismatch)
                "video element not detected" in text -> context.getString(R.string.safe_reason_video_not_detected)
                "player not detected" in text -> context.getString(R.string.safe_reason_player_not_detected)
                "script errors:" in text -> {
                    val count = Regex("script errors: (\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                    context.getString(R.string.safe_reason_script_errors, count)
                }
                else -> ""
            }
            return context.getString(R.string.safe_reason_compatibility_guard) + if (detail.isBlank()) "" else " ($detail)"
        }
        return diagnosticDetail(context, text)
    }

}
