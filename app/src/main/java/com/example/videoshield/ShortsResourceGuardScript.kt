package com.example.videoshield

/**
 * Keeps the long-running Shorts browse surface cheap without rebuilding the feed.
 *
 * YouTube owns the vertical list and may retain several media elements around the
 * viewport for smooth swipes. We never remove cards or media sources during normal
 * operation. Instead, distant inactive videos are paused and downgraded to preload=none;
 * on Android memory pressure an aggressive pass also resets buffered inactive media by
 * calling load(), allowing Chromium to release decoder/buffer resources.
 */
object ShortsResourceGuardScript {
    fun install(): String = """
        (() => {
          if (window.__votuibeShortsResourceGuardInstalled) return;
          window.__votuibeShortsResourceGuardInstalled=true;
          const isShorts=()=>location.pathname.startsWith('/shorts/');
          let activeVideo=null;

          // Capturing play is a cheap and reliable active-item signal from YouTube's recycler.
          // It lets normal trim passes avoid sorting every media element and forcing layout via
          // getBoundingClientRect() after the DOM has grown through a long viewing session.
          document.addEventListener('play',event=>{
            const video=event.target;
            if(isShorts() && video?.tagName==='VIDEO') activeVideo=video;
          },true);

          const resolveActive=videos=>{
            if(activeVideo && activeVideo.isConnected!==false && videos.includes(activeVideo)) return activeVideo;
            const playing=videos.find(video=>!video.paused && !video.ended && video.readyState>=1);
            if(playing) { activeVideo=playing; return playing; }
            // Paused/buffering initial Short: geometry is a rare fallback, not the hot path.
            let best=null,bestDistance=Number.MAX_SAFE_INTEGER;
            const center=Math.max(1,innerHeight)*0.5;
            for(const video of videos) {
              try {
                const r=video.getBoundingClientRect();
                if(r.width<=1 || r.height<=1 || r.bottom<=0 || r.top>=innerHeight) continue;
                const distance=Math.abs((r.top+r.bottom)*0.5-center);
                if(distance<bestDistance) { best=video; bestDistance=distance; }
              } catch (_) {}
            }
            if(best) activeVideo=best;
            return best;
          };

          const trim=(aggressive=false,trimImages=aggressive)=>{
            if (!isShorts() || document.visibilityState==='hidden') return {videos:0,trimmed:0};
            const videos=Array.from(document.querySelectorAll('video'));
            if (videos.length<=3 && !aggressive) return {videos:videos.length,trimmed:0};

            const current=resolveActive(videos);
            const activeIndex=current ? videos.indexOf(current) : -1;
            const keepRadius=aggressive ? 1 : 2;
            const trimRadius=aggressive ? 2 : 4;
            let trimmed=0;

            // DOM order follows the Shorts recycler order. Index distance is sufficient here
            // and avoids an O(N log N) sort plus N layout reads every sixth swipe.
            for (let index=0; index<videos.length; index++) {
              const video=videos[index];
              if(video===current || (!video.paused && !video.ended)) continue;
              if(activeIndex>=0) {
                const distance=Math.abs(index-activeIndex);
                if(distance<=keepRadius || distance<=trimRadius) continue;
              } else if(index<4 && !aggressive) continue;
              try {
                video.preload='none';
                if (!video.paused) video.pause();
                // Only under real Android memory pressure / periodic hard trim: reset a
                // distant inactive media element so Chromium can discard decoder buffers.
                // Do not remove src/blob URLs; YouTube can still reuse the recycler item.
                if (aggressive && video.readyState>1) video.load();
                trimmed++;
              } catch (_) {}
            }

            // Avoid walking every image during normal swipes. Only under real Android memory
            // pressure do a bounded image pass, and only over the most recently mounted nodes.
            if (aggressive && trimImages) {
              const images=document.querySelectorAll('img');
              const start=Math.max(0,images.length-160);
              for (let i=start;i<images.length;i++) {
                const image=images[i];
                try {
                  const r=image.getBoundingClientRect();
                  if (r.bottom < -innerHeight*2 || r.top > innerHeight*3) {
                    image.loading='lazy';
                    image.decoding='async';
                  }
                } catch (_) {}
              }
            }
            return {videos:videos.length,trimmed};
          };

          // Native route handling already knows when the active Short changes and calls
          // this every few swipes. Keep no extra mutation/navigation timer in JavaScript.
          window.__votuibeTrimShorts=trim;
        })();
    """.trimIndent()

    fun trim(aggressive: Boolean, trimImages: Boolean = aggressive): String =
        "window.__votuibeTrimShorts?.(${if (aggressive) "true" else "false"},${if (trimImages) "true" else "false"});"
}
