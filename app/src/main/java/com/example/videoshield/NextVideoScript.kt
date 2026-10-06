package com.example.videoshield

/** Read one visible related-video link only when native playback has ended. */
object NextVideoScript {
    fun build(completedId: String): String = """
        (() => {
          const current=${org.json.JSONObject.quote(completedId)};
          const selectors='ytm-video-with-context-renderer a[href],ytm-compact-video-renderer a[href],ytd-compact-video-renderer a[href]';
          for(const link of Array.from(document.querySelectorAll(selectors)).slice(0,200)) {
            if(link.closest('ytm-promoted-video-renderer,ytd-promoted-video-renderer,[data-ad-slot]')) continue;
            let url;try {url=new URL(link.href,location.href);}catch(_){continue;}
            if(url.protocol!=='https:' || !/(^|\.)youtube\.com${'$'}/i.test(url.hostname) || url.pathname!=='/watch') continue;
            const id=url.searchParams.get('v');
            if(!id || id===current || !/^[A-Za-z0-9_-]{6,32}${'$'}/.test(id)) continue;
            const row=link.closest('ytm-video-with-context-renderer,ytm-compact-video-renderer,ytd-compact-video-renderer');
            const title=(row?.querySelector('h3,#video-title')?.textContent || link.getAttribute('aria-label') || link.textContent || '').trim().slice(0,240);
            return JSON.stringify({url:'https://m.youtube.com/watch?v='+id,title});
          }
          return null;
        })()
    """.trimIndent()
}
