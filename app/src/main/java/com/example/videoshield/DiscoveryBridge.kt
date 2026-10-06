package com.example.videoshield

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONArray

class DiscoveryBridge(private val allowed: () -> Boolean, private val accept: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var lastAccepted = 0L
    @JavascriptInterface fun candidates(json: String?) {
        if (json == null || json.length > 30_000) return
        main.post {
            val now = android.os.SystemClock.elapsedRealtime()
            if (allowed() && now - lastAccepted >= 10_000) { lastAccepted = now; accept(json) }
        }
    }
    companion object {
        fun parse(json: String, now: Long): List<VideoItem> {
            val array = JSONArray(json)
            return (0 until minOf(array.length(), 50)).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id")
                val title = obj.optString("title").trim().take(240)
                if (!Regex("[A-Za-z0-9_-]{11}").matches(id) || title.isBlank()) null
                else VideoItem(id, title, obj.optString("channel").trim().take(180), "https://m.youtube.com/watch?v=$id", now)
            }.distinctBy { it.videoId }
        }
    }
}

object DiscoveryScript {
    fun build(enabled: Boolean): String = """
        (() => {
          window.__youTooBeeDiscoveryEnabled = $enabled;
          if (window.__youTooBeeDiscoveryInstalled) return;
          window.__youTooBeeDiscoveryInstalled = true;
          function collect() {
            if (!window.__youTooBeeDiscoveryEnabled || typeof window.YouTooBeeDiscovery !== 'object') return;
            const cards = document.querySelectorAll('ytm-video-with-context-renderer,ytm-compact-video-renderer,ytm-rich-item-renderer,ytd-rich-item-renderer,ytd-compact-video-renderer');
            const rows = []; const seen = new Set();
            for (const card of Array.from(cards).slice(0,100)) {
              if (card.closest('ytm-ad-slot-renderer,ytm-promoted-video-renderer,ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,ytd-ad-slot-renderer,ytd-display-ad-renderer,ytd-promoted-video-renderer,ytd-promoted-sparkles-web-renderer,[data-ad-impressions],[data-videoshield-reason="ads"]')) continue;
              const anchor = card.querySelector('a[href*="/watch?"]');
              if (!anchor) continue;
              let id;
              try { const url = new URL(anchor.href, location.href); if (!['m.youtube.com','www.youtube.com','youtube.com'].includes(url.hostname)) continue; id=url.searchParams.get('v'); } catch (_) { continue; }
              if (!/^[A-Za-z0-9_-]{11}$/.test(id || '') || seen.has(id)) continue;
              const titleNode = card.querySelector('h3,h4,#video-title,.compact-media-item-headline');
              const title = (titleNode?.textContent || anchor.getAttribute('aria-label') || anchor.getAttribute('title') || '').trim().slice(0,240);
              if (!title) continue;
              const channelLink = card.querySelector('a[href^="/@"]');
              const channelNode = channelLink || card.querySelector('.ytm-badge-and-byline-item-byline,.compact-media-item-byline,#channel-name');
              const handle = channelLink?.getAttribute('href')?.split('/')[1];
              rows.push({id,title,channel:(handle || channelNode?.textContent || '').trim().slice(0,180)});
              seen.add(id); if(rows.length>=50) break;
            }
            const payload=JSON.stringify(rows);
            // Retry on the next bounded interval if native throttling rejected a navigation burst.
            if(rows.length && payload.length<=30000) window.YouTooBeeDiscovery.candidates(payload);
          }
          // Bounded, infrequent collection; no additional DOM mutation observer or layout reads.
          setTimeout(collect,3000);
          setInterval(collect,20000);
        })();
    """.trimIndent()
}
