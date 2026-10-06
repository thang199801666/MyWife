package com.example.videoshield

import org.junit.Assert.*
import org.junit.Test

class DownloadFailureTest {
    @Test fun explainsDnsFailureAndRetainsUnderlyingError() {
        val result = DownloadFailure.describe(Exception("ERROR: [Errno 7] No address associated with hostname"))
        assertTrue(result.startsWith("Không phân giải"))
        assertTrue(result.contains("[Errno 7]"))
    }
    @Test fun distinguishesUnavailableFormatFromNetworkAndDisk() {
        assertTrue(DownloadFailure.describe(Exception("Requested format is not available")).contains("chọn lại"))
        assertTrue(DownloadFailure.describe(Exception("No space left on device")).contains("dung lượng"))
        assertTrue(DownloadFailure.describe(Exception("Read timed out")).contains("mạng ổn định"))
    }
    @Test fun readsNestedCauseAndBoundsDiagnostics() {
        assertTrue(DownloadFailure.describe(Exception("Download failed", Exception("HTTP Error 403"))).contains("giới hạn lượt tải"))
        assertTrue(DownloadFailure.describe(Exception("x".repeat(10000))).length < 4200)
    }
    @Test fun distinguishesEmptySourceAndLiveVideo() {
        assertTrue(DownloadFailure.describe(Exception("Không có nguồn video tải được.")).contains("luồng video"))
        assertTrue(DownloadFailure.describe(Exception("Chưa hỗ trợ lưu livestream đang phát.")).startsWith("Chưa hỗ trợ"))
    }
}
