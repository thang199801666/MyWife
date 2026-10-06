package com.example.videoshield

import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Single gateway for native -> HTML5 player commands.
 * Keeping JavaScript command generation here prevents UI classes from duplicating
 * player-control snippets and makes future player adapters easier to swap.
 */
class PlayerController(private val webView: WebView) {
    @Volatile
    private var released = false

    fun play() = command("play")
    fun pause() = command("pause")
    fun toggle() = command("toggle")
    fun seekBack() = command("seekBack")
    fun seekForward() = command("seekForward")

    fun seekToMs(positionMs: Long) {
        val seconds = positionMs.coerceAtLeast(0L) / 1000.0
        val value = String.format(Locale.US, "%.3f", seconds)
        evaluate("window.__videoShieldSetPosition && window.__videoShieldSetPosition($value)")
    }

    fun setRepeatEnabled(enabled: Boolean) {
        evaluate("window.__videoShieldSetRepeat && window.__videoShieldSetRepeat(${if (enabled) "true" else "false"})")
    }

    fun setPlaybackRate(rate: Float) {
        val safe = rate.coerceIn(0.25f, 4.0f)
        val value = String.format(Locale.US, "%.2f", safe)
        evaluate(
            """(() => {
              const rate = $value;
              try {
                if (!window.__videoShieldCfg) window.__videoShieldCfg = {};
                window.__videoShieldCfg.playbackSpeed = rate;
                if (typeof window.__videoShieldSetRate === 'function') {
                  return window.__videoShieldSetRate(rate);
                }
                const player = document.querySelector('.html5-video-player');
                const video = player?.querySelector('video') || document.querySelector('video');
                if (player && typeof player.setPlaybackRate === 'function') {
                  try { player.setPlaybackRate(rate); } catch (_) {}
                }
                if (!video) return false;
                video.defaultPlaybackRate = rate;
                video.playbackRate = rate;
                return true;
              } catch (_) { return false; }
            })()""".trimIndent()
        )
    }

    fun setCommunitySegments(videoId: String, segments: List<CommunitySegment>) {
        val json = JSONArray().apply {
            segments.take(256).forEach { segment ->
                put(JSONObject().apply {
                    put("start", segment.startMs.coerceAtLeast(0L) / 1000.0)
                    put("end", segment.endMs.coerceAtLeast(segment.startMs + 1L) / 1000.0)
                    put("category", segment.category.take(40))
                })
            }
        }
        evaluate(
            "window.__videoShieldSetSegments && window.__videoShieldSetSegments(" +
                JSONObject.quote(videoId.take(64)) + "," + json.toString() + ")"
        )
    }

    fun clearCommunitySegments() {
        evaluate("window.__videoShieldSetSegments && window.__videoShieldSetSegments('',[])")
    }

    fun release() {
        released = true
    }

    private fun command(command: String) {
        evaluate("window.__videoShieldControl && window.__videoShieldControl('$command')")
    }

    private fun evaluate(script: String) {
        if (released) return
        webView.post {
            if (released) return@post
            try {
                webView.evaluateJavascript(script, null)
            } catch (_: IllegalStateException) {
                // The WebView may have been destroyed after this Runnable was queued.
            }
        }
    }
}
