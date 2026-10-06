package com.example.videoshield

/** One alternate extraction, only for failures that can change with the client. */
internal object DownloadSourceRetry {
    const val ALTERNATE = "youtube:player_client=default,web_safari;webpage_client=web_safari"

    fun eligible(error: Throwable): Boolean {
        if (error is InterruptedException) return false
        val text = generateSequence(error) { it.cause }.take(6)
            .joinToString("\n") { it.message.orEmpty() }.lowercase()
        if (listOf("cancel", "429", "sign in", "private video", "members-only", "age-restricted",
                "not available in your country", "no space left", "timeout", "timed out",
                "unable to resolve", "name resolution", "no address associated").any(text::contains)) return false
        return listOf("requested format is not available", "no video formats", "no formats found",
            "only images are available", "http error 403", "signature extraction failed",
            "nsig extraction failed", "không có nguồn video tải được", "nguồn chưa cung cấp định dạng tải")
            .any(text::contains)
    }

    fun <T> run(cancelled: () -> Boolean, attempt: (String?) -> T): T {
        fun checkedAttempt(client: String?): T {
            if (cancelled()) throw InterruptedException("Cancelled")
            val result = attempt(client)
            if (cancelled()) throw InterruptedException("Cancelled")
            return result
        }
        try { return checkedAttempt(null) }
        catch (first: Exception) {
            if (cancelled()) throw InterruptedException("Cancelled")
            if (!eligible(first)) throw first
            try { return checkedAttempt(ALTERNATE) }
            catch (second: Exception) {
                if (cancelled()) throw InterruptedException("Cancelled")
                second.addSuppressed(first)
                throw second
            }
        }
    }
}
