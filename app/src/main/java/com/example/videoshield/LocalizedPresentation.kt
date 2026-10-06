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
        value.startsWith("Similar to: ") -> context.getString(R.string.recommend_similar,value.removePrefix("Similar to: "))
        value == "New discovery from pages you browsed" -> context.getString(R.string.recommend_new)
        else -> value
    }
}
