package com.example.videoshield

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONArray

class DiscoveryBridge(private val allowed: () -> Boolean, private val accept: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var lastPayloadHash = 0
    @JavascriptInterface fun candidates(json: String?) {
        if (json == null || json.length > 60_000) return
        main.post {
            val hash = json.hashCode()
            if (allowed() && hash != lastPayloadHash) {
                lastPayloadHash = hash
                accept(json)
            }
        }
    }
    companion object {
        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
        fun parse(json: String, now: Long): List<VideoItem> {
            val array = JSONArray(json)
            return (0 until minOf(array.length(), 100)).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id")
                val title = obj.optString("title").trim().take(240)
                if (!VIDEO_ID.matches(id) || title.isBlank()) null
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
          const active=()=>window.__youTooBeeDiscoveryEnabled &&
            document.visibilityState!=='hidden' &&
            (location.pathname==='/' || location.pathname==='') &&
            typeof window.YouTooBeeDiscovery === 'object';
          function collect() {
            if (!active()) return;
            const cards = document.querySelectorAll('ytm-video-with-context-renderer,ytm-compact-video-renderer,ytm-rich-item-renderer,ytd-rich-item-renderer,ytd-compact-video-renderer');
            const rows = []; const seen = new Set();
            for (const card of Array.from(cards).slice(0,240)) {
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
              seen.add(id); if(rows.length>=100) break;
            }
            const key=rows.map(row=>row.id).join(',');
            if(!rows.length || key===lastPayloadKey) return;
            const since=Date.now()-lastSentAt;
            if(since<12000) { scheduleCollect(12000-since); return; }
            const payload=JSON.stringify(rows);
            if(payload.length<=60000) {
              lastPayloadKey=key;
              lastSentAt=Date.now();
              window.YouTooBeeDiscovery.candidates(payload);
            }
          }
          // Event-driven collection. Attach the high-frequency scroll listener only while
          // Home is actually active; Shorts/search scrolling should not execute discovery code.
          let collectTimer=0, fallbackTimer=0, lastPayloadKey='', lastSentAt=0, scrollBound=false;
          const onScroll=()=>scheduleCollect(1000);
          const scheduleCollect=(delay=1000)=>{
            if (!active()) return;
            clearTimeout(collectTimer);
            collectTimer=setTimeout(collect,delay);
          };
          const scheduleFallback=()=>{
            clearTimeout(fallbackTimer);
            fallbackTimer=0;
            if(!active()) return;
            fallbackTimer=setTimeout(()=>{ fallbackTimer=0; collect(); scheduleFallback(); },45000);
          };
          const sync=()=>{
            const enabled=active();
            if(enabled && !scrollBound) { addEventListener('scroll',onScroll,{passive:true}); scrollBound=true; }
            else if(!enabled && scrollBound) { removeEventListener('scroll',onScroll); scrollBound=false; }
            if(enabled) { scheduleCollect(700); scheduleFallback(); }
            else { clearTimeout(collectTimer); clearTimeout(fallbackTimer); collectTimer=0; fallbackTimer=0; }
          };
          addEventListener('yt-navigate-finish',sync,{passive:true});
          addEventListener('visibilitychange',sync,{passive:true});
          addEventListener('popstate',sync,{passive:true});
          sync();
        })();
    """.trimIndent()
}
