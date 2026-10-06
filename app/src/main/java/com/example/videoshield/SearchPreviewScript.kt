package com.example.videoshield

/** Repair missing search thumbnails without extracting media or replacing result titles. */
object SearchPreviewScript {
    fun build(): String = """
        (() => {
          if (window.__voTuibeSearchPreviewInstalled) return;
          window.__voTuibeSearchPreviewInstalled=true;
          const selector='a[href*="/watch?"]';
          const active=()=>location.pathname==='/results' && document.visibilityState!=='hidden';
          const repaired=new WeakMap();
          const videoId=link=>{
            try {
              const url=new URL(link.href,location.href);
              const id=url.searchParams.get('v')||'';
              return /^(m\.|www\.)?youtube\.com$/.test(url.hostname) && url.pathname==='/watch' &&
                /^[A-Za-z0-9_-]{11}$/.test(id) ? id : '';
            } catch (_) { return ''; }
          };
          const repair=link=>{
            if (!active()) return;
            const id=videoId(link);
            const image=link.querySelector('img.video-thumbnail-img, .ytThumbnailViewModelImage img');
            if (!id || !image) return;
            const src=image.getAttribute('src')||'';
            if (src && !src.startsWith('data:') && !(image.complete && image.naturalWidth===0)) return;
            if (repaired.get(image)===id) return;
            repaired.set(image,id);
            image.loading='lazy'; image.decoding='async';
            image.src='https://i.ytimg.com/vi/'+id+'/mqdefault.jpg';
          };
          let observed=new WeakSet();
          const visible=new IntersectionObserver(entries=>{
            for(const entry of entries) if(entry.isIntersecting) repair(entry.target);
          },{rootMargin:'240px'});
          let pending=false;
          let observing=false;
          const scan=()=>{
            pending=false;
            if (!active()) return;
            for(const link of document.querySelectorAll(selector)) {
              if(!observed.has(link)) { observed.add(link); visible.observe(link); }
            }
          };
          const schedule=()=>{
            if(pending || !active()) return;
            pending=true; setTimeout(scan,250);
          };
          const mutations=new MutationObserver((records)=>{
            if (!active()) return;
            // YouTube replaces result trees during SPA search. Release detached
            // targets so the observer does not retain earlier keyword results.
            for(const record of records) for(const node of record.removedNodes) {
              const detached=[];
              if(node.matches?.(selector)) detached.push(node);
              if(node.querySelectorAll) detached.push(...node.querySelectorAll(selector));
              for(const link of detached) { visible.unobserve(link); observed.delete(link); }
            }
            schedule();
          });
          const syncObserver=()=>{
            if(active()) {
              if(!observing) {
                mutations.observe(document.documentElement,{childList:true,subtree:true});
                observing=true;
              }
              schedule();
            } else if(observing) {
              mutations.disconnect();
              visible.disconnect();
              observed=new WeakSet();
              observing=false;
            }
          };
          document.addEventListener('error',event=>{
            if(!active()) return;
            const link=event.target.closest?.(selector);
            if(link) repair(link);
          },true);
          document.addEventListener('visibilitychange',syncObserver);
          document.addEventListener('yt-navigate-finish',syncObserver,true);
          window.addEventListener('popstate',syncObserver);
          syncObserver();
        })();
    """.trimIndent()
}
