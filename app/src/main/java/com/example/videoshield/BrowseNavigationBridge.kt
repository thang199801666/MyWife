package com.example.videoshield

import android.webkit.JavascriptInterface

class BrowseNavigationBridge(private val navigate: (String) -> Unit) {
    @JavascriptInterface
    fun openVideo(url: String) {
        if (url.length <= 4096 && YouTubeAdapter.isTrustedBridgeUrl(url)) navigate(url)
    }
}

/** Classic watch-page route changes in YouTube's SPA enter the native playback surface. Shorts stay in browse. */
object BrowseNavigationScript {
    fun build(): String = """
        (() => {
          if (window.__voTuibeNavigationInstalled) return;
          window.__voTuibeNavigationInstalled=true;
          // Keep Shorts inside the browse surface. A Shorts swipe updates history to
          // /shorts/<id>; treating that as a player navigation tears down the vertical
          // feed and reloads it at item 0. Only classic /watch links are promoted.
          const playback=url=>url.protocol==='https:' && /^(m\.|www\.)?youtube\.com$/.test(url.hostname) &&
            url.pathname==='/watch' && !!url.searchParams.get('v');
          const open=href=>{
            try {
              const url=new URL(href,location.href);
              if (!playback(url) || !window.VoTuibeNavigation) return false;
              VoTuibeNavigation.openVideo(url.href); return true;
            } catch (_) { return false; }
          };
          document.addEventListener('click',event=>{
            if (event.button>0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            const link=event.target.closest?.('a[href]');
            if (link && open(link.href)) { event.preventDefault(); event.stopImmediatePropagation(); }
          },true);
          let previous='';
          const report=()=>{ const url=location.href; if(url===previous)return; previous=url; open(url); };
          const isShorts=url=>{
            try { const path=new URL(url,location.href).pathname; return path==='/shorts' || path.startsWith('/shorts/'); }
            catch (_) { return false; }
          };
          const installHistoryHooks=()=>{
            if (history.pushState?.__votuibeShortsBound && history.replaceState?.__votuibeShortsBound) return;
            const nativePush=history.pushState.bind(history);
            const nativeReplace=history.replaceState.bind(history);
            // Keep one browser-history entry for the vertical Shorts session. YouTube may
            // push a new SPA entry for every swipe; hundreds of entries inflate WebView
            // history/saveState and can eventually contribute to renderer/process pressure.
            const wrappedPush=function(...args){
              const next=args.length>=3 ? args[2] : null;
              const result=(isShorts(location.href) && next!=null && isShorts(next))
                ? nativeReplace(...args) : nativePush(...args);
              report(); return result;
            };
            const wrappedReplace=function(...args){ const result=nativeReplace(...args); report(); return result; };
            try {
              Object.defineProperty(wrappedPush,'__votuibeShortsBound',{value:true});
              Object.defineProperty(wrappedReplace,'__votuibeShortsBound',{value:true});
            } catch (_) {}
            history.pushState=wrappedPush;
            history.replaceState=wrappedReplace;
          };
          installHistoryHooks();
          window.addEventListener('popstate',report);
          document.addEventListener('yt-navigate-finish',report,true);
          // Some experiments replace the history methods after installation. The normal
          // history + yt-navigate hooks are immediate, so this is only a low-frequency
          // fallback instead of a permanent 1 Hz wake-up.
          let fallbackTimer=0;
          const scheduleFallback=()=>{
            clearTimeout(fallbackTimer); fallbackTimer=0;
            // The periodic hook check exists only for YouTube experiments that replace
            // history.pushState while the vertical Shorts recycler is alive. Home/search
            // navigation is already covered by click + yt-navigate-finish and needs no timer.
            if(document.visibilityState==='hidden' || !isShorts(location.href)) return;
            fallbackTimer=setTimeout(()=>{
              fallbackTimer=0;
              installHistoryHooks();
              report();
              scheduleFallback();
            },15000);
          };
          document.addEventListener('visibilitychange',()=>{
            if(document.visibilityState==='hidden') { clearTimeout(fallbackTimer); fallbackTimer=0; }
            else { installHistoryHooks(); report(); scheduleFallback(); }
          },true);
          scheduleFallback();
          report();
        })();
    """.trimIndent()
}
