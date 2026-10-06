package com.example.videoshield

import android.os.Build

/**
 * Conservative OEM/runtime policy selector. It does not bypass Android power management;
 * it only tunes VideoShield's own resume/recovery timing and exposes a diagnostic profile.
 */
data class DeviceCompatibilityPolicy(
    val profile: String,
    val manufacturer: String,
    val model: String,
    val webViewResumeDelayMs: Long,
    val recoveryGraceMs: Long,
    val backgroundCaution: Boolean
) {
    val summary: String
        get() = "$profile • ${manufacturer.ifBlank { "unknown" }} ${model.ifBlank { "device" }} • resume ${webViewResumeDelayMs}ms • grace ${recoveryGraceMs}ms" +
            if (backgroundCaution) " • conservative background policy" else ""

    companion object {
        fun detect(): DeviceCompatibilityPolicy {
            val manufacturer = Build.MANUFACTURER.orEmpty().trim()
            val model = Build.MODEL.orEmpty().trim()
            val key = manufacturer.lowercase()
            return when {
                key.contains("xiaomi") || key.contains("redmi") || key.contains("poco") ->
                    DeviceCompatibilityPolicy("OEM-conservative", manufacturer, model, 180L, 2_500L, true)
                key.contains("oppo") || key.contains("oneplus") || key.contains("realme") || key.contains("vivo") ->
                    DeviceCompatibilityPolicy("OEM-conservative", manufacturer, model, 160L, 2_250L, true)
                key.contains("huawei") || key.contains("honor") ->
                    DeviceCompatibilityPolicy("OEM-conservative", manufacturer, model, 180L, 2_500L, true)
                key.contains("samsung") ->
                    DeviceCompatibilityPolicy("Samsung-balanced", manufacturer, model, 100L, 1_750L, false)
                key.contains("google") ->
                    DeviceCompatibilityPolicy("Pixel-reference", manufacturer, model, 0L, 1_250L, false)
                else ->
                    DeviceCompatibilityPolicy("Android-default", manufacturer, model, 60L, 1_500L, false)
            }
        }
    }
}
