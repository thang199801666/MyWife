package com.example.videoshield

import org.junit.Assert.*
import org.junit.Test

class DownloadSourceRetryTest {
    @Test fun successfulFirstAttemptDoesNotMakeExtraRequests() {
        val clients=mutableListOf<String?>()
        assertEquals("source",DownloadSourceRetry.run({false}) { clients.add(it); "source" })
        assertEquals(listOf<String?>(null),clients)
    }
    @Test fun missingFormatsUseExactlyOneAlternateClient() {
        val clients=mutableListOf<String?>()
        val result=DownloadSourceRetry.run({false}) {
            clients.add(it)
            if(it==null) throw IllegalArgumentException("Không có nguồn video tải được.")
            "source"
        }
        assertEquals("source",result)
        assertEquals(listOf(null,DownloadSourceRetry.ALTERNATE),clients)
    }
    @Test fun alternateFailureStopsAndRetainsFirstFailure() {
        var attempts=0
        val failure=runCatching { DownloadSourceRetry.run({false}) {
            attempts++; throw IllegalArgumentException("Requested format is not available $attempts")
        } }.exceptionOrNull()!!
        assertEquals(2,attempts)
        assertTrue(failure.message!!.endsWith("2"))
        assertTrue(failure.suppressed.single().message!!.endsWith("1"))
    }
    @Test fun cancellationBetweenAttemptsNeverStartsFallback() {
        var cancelled=false; var attempts=0
        val failure=runCatching { DownloadSourceRetry.run({cancelled}) {
            attempts++; cancelled=true; throw Exception("HTTP Error 403")
        } }.exceptionOrNull()
        assertTrue(failure is InterruptedException)
        assertEquals(1,attempts)
    }
    @Test fun accessNetworkStorageAndRateLimitsDoNotTriggerExtraTraffic() {
        listOf("Sign in to confirm you are not a bot", "Private video", "age-restricted",
            "HTTP Error 429", "Read timed out", "No address associated with hostname",
            "No space left on device", "Video not available in your country").forEach { message ->
            var attempts=0
            runCatching { DownloadSourceRetry.run({false}) { attempts++; throw Exception(message) } }
            assertEquals(message,1,attempts)
        }
        assertTrue(DownloadSourceRetry.eligible(Exception("HTTP Error 403")))
        assertFalse(DownloadSourceRetry.eligible(InterruptedException()))
    }
}
