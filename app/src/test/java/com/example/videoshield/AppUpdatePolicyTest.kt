package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdatePolicyTest {
    private val release = AppRelease(
        versionCode = 43,
        versionName = "0.1.2",
        minSdk = 26,
        abi = "arm64-v8a",
        assetName = "VoTui-v0.1.2-release-arm64-v8a.apk",
        apkUrl = "https://github.com/thang199801666/MyWife/releases/download/v0.1.2/VoTui-v0.1.2-release-arm64-v8a.apk",
        size = 10L * 1024 * 1024,
        sha256 = "0".repeat(64)
    )

    @Test fun newerCompatibleReleaseIsAvailable() {
        assertEquals(
            AppUpdateAvailability.AVAILABLE,
            AppUpdatePolicy.availability(release, 42, 37, listOf("arm64-v8a"))
        )
    }

    @Test fun sameOrOlderVersionDoesNotPrompt() {
        assertEquals(AppUpdateAvailability.UP_TO_DATE, AppUpdatePolicy.availability(release, 43, 37, listOf("arm64-v8a")))
        assertEquals(AppUpdateAvailability.UP_TO_DATE, AppUpdatePolicy.availability(release, 44, 37, listOf("arm64-v8a")))
    }

    @Test fun incompatibleReleaseDoesNotPrompt() {
        assertEquals(AppUpdateAvailability.INCOMPATIBLE, AppUpdatePolicy.availability(release, 42, 25, listOf("arm64-v8a")))
        assertEquals(AppUpdateAvailability.INCOMPATIBLE, AppUpdatePolicy.availability(release, 42, 37, listOf("x86_64")))
    }

    @Test fun missingReleaseIsHandled() {
        assertEquals(AppUpdateAvailability.NO_RELEASE, AppUpdatePolicy.availability(null, 42, 37, listOf("arm64-v8a")))
    }

    @Test fun declinedReleaseIsSuppressedButNewerReleaseCanPrompt() {
        assertEquals(true, AppStartupUpdateChecker.shouldSuppressPrompt(43, 43))
        assertEquals(true, AppStartupUpdateChecker.shouldSuppressPrompt(42, 43))
        assertEquals(false, AppStartupUpdateChecker.shouldSuppressPrompt(44, 43))
        assertEquals(false, AppStartupUpdateChecker.shouldSuppressPrompt(43, Long.MIN_VALUE))
    }
}
