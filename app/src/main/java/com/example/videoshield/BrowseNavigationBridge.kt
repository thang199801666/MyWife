package com.example.videoshield

import android.webkit.JavascriptInterface

class BrowseNavigationBridge(private val navigate: (String) -> Unit) {
    @JavascriptInterface
    fun openVideo(url: String) {
        if (url.length <= 4096 && YouTubeAdapter.isTrustedBridgeUrl(url)) navigate(url)
    }
}

/** Route changes in YouTube's SPA must enter the native playback surface too. */
object BrowseNavigationScript {
    fun build(): String = """
        (() => {
          if (window.__voTuibeNavigationInstalled) return;
          window.__voTuibeNavigationInstalled=true;
          const playback=url=>url.protocol==='https:' && /^(m\.|www\.)?youtube\.com$/.test(url.hostname) &&
            ((url.pathname==='/watch' && !!url.searchParams.get('v')) || /^\/shorts\/[^/]+/.test(url.pathname));
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
          for (const name of ['pushState','replaceState']) {
            const original=history[name];
            history[name]=function(...args){ const result=original.apply(this,args); report(); return result; };
          }
          window.addEventListener('popstate',report);
          document.addEventListener('yt-navigate-finish',report,true);
          // Some experiments replace the history methods after installation.
          setInterval(report,1000);
          report();
        })();
    """.trimIndent()
}
