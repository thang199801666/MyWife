package com.example.videoshield

/** Lightweight native -> Watch renderer retention command. */
object PlayerMediaResourceScript {
    fun apply(mode: PlayerMediaRetentionMode, compactSession: Boolean = false): String {
        val value = when (mode) {
            PlayerMediaRetentionMode.ACTIVE -> "active"
            PlayerMediaRetentionMode.WARM_PAUSED -> "warm"
            PlayerMediaRetentionMode.LEAN_PAUSED -> "lean"
            PlayerMediaRetentionMode.COLD_PAUSED -> "cold"
        }
        val compact = if (compactSession) "true" else "false"
        return "window.__videoShieldSetMediaRetention && window.__videoShieldSetMediaRetention('$value',$compact);"
    }
}
