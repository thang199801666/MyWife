package com.example.videoshield

enum class AppUpdateAvailability {
    NO_RELEASE,
    UP_TO_DATE,
    INCOMPATIBLE,
    AVAILABLE
}

/** Shared decision logic for startup prompts and the manual update screen. */
object AppUpdatePolicy {
    fun availability(
        release: AppRelease?,
        installedVersionCode: Long,
        sdkInt: Int,
        supportedAbis: Collection<String>
    ): AppUpdateAvailability = when {
        release == null -> AppUpdateAvailability.NO_RELEASE
        release.versionCode <= installedVersionCode -> AppUpdateAvailability.UP_TO_DATE
        release.minSdk > sdkInt || release.abi !in supportedAbis -> AppUpdateAvailability.INCOMPATIBLE
        else -> AppUpdateAvailability.AVAILABLE
    }
}
