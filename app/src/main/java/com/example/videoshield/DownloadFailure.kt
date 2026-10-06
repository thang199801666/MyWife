package com.example.videoshield

object DownloadFailure {
    fun describe(error: Throwable, context: android.content.Context): String = localized(context,describe(error))
    fun localized(context: android.content.Context, message: String): String {
        val parts = message.split("\n\n",limit=2)
        val resource = when(parts[0]) {
            "Không phân giải được địa chỉ máy chủ. Kiểm tra kết nối mạng/DNS rồi thử lại." -> R.string.download_error_dns
            "Thiết bị không đủ dung lượng để tải và xử lý file." -> R.string.download_error_space
            "Kết nối tới nguồn tải quá chậm. Thử lại khi mạng ổn định hơn." -> R.string.download_error_timeout
            "Chất lượng đã chọn không còn có sẵn. Mở Download và chọn lại chất lượng nguồn." -> R.string.download_error_format
            "Nguồn yêu cầu đăng nhập hoặc có giới hạn truy cập; bộ tải hiện chưa dùng phiên đăng nhập trong app." -> R.string.download_error_access
            "Máy chủ từ chối hoặc giới hạn lượt tải. Thử lại sau hoặc đổi kết nối mạng." -> R.string.download_error_server
            "Không thể hoàn tất tải. Chi tiết từ nguồn tải ở bên dưới." -> R.string.download_error_generic
            "Nguồn chưa trả về luồng video tải được sau khi thử lại. Hãy thử lại sau; nếu vẫn lỗi, gửi link video và chi tiết bên dưới." -> R.string.download_error_source
            "Chưa hỗ trợ lưu livestream đang phát." -> R.string.download_error_live
            else -> return message
        }
        return context.getString(resource) + if(parts.size>1) "\n\n"+parts[1] else ""
    }
    fun describe(error: Throwable): String {
        val detail = generateSequence(error) { it.cause }.take(6)
            .mapNotNull { it.message }.distinct().joinToString("\n").takeLast(4000)
        val text = detail.lowercase()
        val reason = when {
            listOf("no address associated", "name or service not known", "unknown host", "unable to resolve host", "name resolution").any(text::contains) ->
                "Không phân giải được địa chỉ máy chủ. Kiểm tra kết nối mạng/DNS rồi thử lại."
            "no space left" in text || "enospc" in text -> "Thiết bị không đủ dung lượng để tải và xử lý file."
            "timed out" in text || "timeout" in text -> "Kết nối tới nguồn tải quá chậm. Thử lại khi mạng ổn định hơn."
            "requested format is not available" in text -> "Chất lượng đã chọn không còn có sẵn. Mở Download và chọn lại chất lượng nguồn."
            "sign in" in text || "private video" in text || "members-only" in text || "age-restricted" in text ->
                "Nguồn yêu cầu đăng nhập hoặc có giới hạn truy cập; bộ tải hiện chưa dùng phiên đăng nhập trong app."
            "403" in text || "429" in text -> "Máy chủ từ chối hoặc giới hạn lượt tải. Thử lại sau hoặc đổi kết nối mạng."
            "chưa hỗ trợ lưu livestream" in text -> "Chưa hỗ trợ lưu livestream đang phát."
            listOf("không có nguồn video tải được", "nguồn chưa cung cấp định dạng tải", "no video formats", "no formats found", "only images are available").any(text::contains) ->
                "Nguồn chưa trả về luồng video tải được sau khi thử lại. Hãy thử lại sau; nếu vẫn lỗi, gửi link video và chi tiết bên dưới."
            else -> "Không thể hoàn tất tải. Chi tiết từ nguồn tải ở bên dưới."
        }
        return "$reason\n\n${detail.ifBlank { error.javaClass.simpleName }}"
    }
}
