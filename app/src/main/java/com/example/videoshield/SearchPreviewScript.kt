package com.example.videoshield

/** Repair missing search thumbnails without extracting media or replacing result titles. */
object SearchPreviewScript {
    fun build(): String = """
        (() => {
          if (window.__voTuibeSearchPreviewInstalled) return;
          window.__voTuibeSearchPreviewInstalled=true;
          const style=document.createElement('style');
          style.id='votuibe-search-surface-style';
          style.textContent=`
            html.votuibe-search-active ytm-video-with-context-renderer,
            html.votuibe-search-active ytm-channel-renderer,
            html.votuibe-search-active ytm-playlist-renderer,
            html.votuibe-search-active ytm-shorts-lockup-view-model { content-visibility:auto; contain:layout paint style; }
            html.votuibe-search-active ytm-video-with-context-renderer { contain-intrinsic-size: 330px; }
            html.votuibe-search-active ytm-channel-renderer { contain-intrinsic-size: 104px; }
            html.votuibe-search-active ytm-playlist-renderer { contain-intrinsic-size: 220px; }
            html.votuibe-search-active ytm-shorts-lockup-view-model { contain-intrinsic-size: 220px 390px; }
            html.votuibe-search-active ytm-video-with-context-renderer img,
            html.votuibe-search-active ytm-playlist-renderer img,
            html.votuibe-search-active .ytThumbnailViewModelImage img,
            html.votuibe-search-active img.video-thumbnail-img { border-radius:12px !important; }
          `;
          (document.head||document.documentElement).appendChild(style);
          const selector='a[href*="/watch?"]';
          const active=()=>location.pathname==='/results' && document.visibilityState!=='hidden';
          const routeKey=()=>active()?location.pathname+location.search:'';
          let repaired=new WeakMap();
          let targetCache=new WeakMap();
          const videoIdFromHref=href=>{
            try {
              const url=new URL(href||'',location.href);
              const id=url.searchParams.get('v')||'';
              return /^(m\.|www\.)?youtube\.com$/.test(url.hostname) && url.pathname==='/watch' &&
                /^[A-Za-z0-9_-]{11}$/.test(id) ? id : '';
            } catch (_) { return ''; }
          };
          const resolveTarget=link=>{
            if(!link?.isConnected) return null;
            const href=link.getAttribute?.('href')||link.href||'';
            const cached=targetCache.get(link);
            if(cached && cached.href===href && cached.image?.isConnected && link.contains(cached.image)) {
              stats.cacheHits++; return cached;
            }
            stats.cacheMisses++;
            const target={href,id:videoIdFromHref(href),image:link.querySelector('img.video-thumbnail-img, .ytThumbnailViewModelImage img')};
            targetCache.set(link,target);
            return target;
          };
          const repair=link=>{
            if (!active() || !link?.isConnected) return;
            const target=resolveTarget(link);
            const id=target?.id||'';
            const image=target?.image||null;
            if (!id || !image) return;
            const src=image.getAttribute('src')||'';
            if (src && !src.startsWith('data:') && !(image.complete && image.naturalWidth===0)) { stats.repairSkips++; return; }
            if (repaired.get(image)===id) { stats.repairSkips++; return; }
            repaired.set(image,id);
            if(image.loading!=='lazy') image.loading='lazy';
            if(image.decoding!=='async') image.decoding='async';
            const priority=velocityBand==='slow'?'auto':'low';
            if(image.fetchPriority!==priority) image.fetchPriority=priority;
            image.src='https://i.ytimg.com/vi/'+id+'/mqdefault.jpg';
            stats.repairs++;
          };
          let observed=new Set();
          let visible=null;
          let prunedSinceStateReset=0;
          let lastScanTotal=-1;
          let lastScanStart=-1;
          let lastScanEnd=-1;
          let lastScanFirst=null;
          let lastScanMiddle=null;
          let lastScanLast=null;
          let idleToken=0;
          let interactionQuietToken=0;
          let lastInteractionAt=0;
          const interactionQuietMs=180;
          let lastScrollY=window.scrollY||0;
          let scrollSampleY=lastScrollY;
          let scrollSampleAt=(performance?.now?.()||Date.now());
          let scrollVelocity=0;
          let scrollDirection=0;
          let velocityBand='slow';
          let memoryPressure='normal';
          const deferredRepair=new Set();
          const stats={repairs:0,deferred:0,velocityTransitions:0,flushes:0,pressureTransitions:0,pressureDrops:0,cacheHits:0,cacheMisses:0,repairSkips:0,localityHits:0,layoutReadsSaved:0,observerPrunes:0,agingPasses:0,stateCompactions:0,observedPeak:0};
          let currentRouteKey='';
          const interactionBusy=()=>lastInteractionAt>0 && (Date.now()-lastInteractionAt)<interactionQuietMs;
          const classifyVelocity=value=>value>=2200?'fast':value>=900?'medium':'slow';
          const sampleScrollVelocity=()=>{
            const now=(performance?.now?.()||Date.now());
            const y=window.scrollY||0;
            const dt=Math.max(16,now-scrollSampleAt);
            const dy=y-scrollSampleY;
            if(Math.abs(dy)>=2) {
              const instant=Math.min(8000,(Math.abs(dy)*1000)/dt);
              scrollVelocity=(scrollVelocity*0.58)+(instant*0.42);
              if(Math.abs(dy)>=6) scrollDirection=dy>0?1:-1;
              const next=classifyVelocity(scrollVelocity);
              if(next!==velocityBand) { velocityBand=next; stats.velocityTransitions++; }
            }
            scrollSampleY=y; scrollSampleAt=now;
          };
          const pressureRank=()=>memoryPressure==='critical'?3:memoryPressure==='low'?2:memoryPressure==='moderate'?1:0;
          const observerAgingBudget=()=>{
            const pressure=pressureRank();
            if(pressure>=3) return {scan:48,before:8,after:10,hardCap:72,stateReset:160};
            if(pressure>=2) return {scan:64,before:10,after:14,hardCap:96,stateReset:240};
            if(pressure>=1) return {scan:88,before:14,after:20,hardCap:128,stateReset:320};
            return {scan:112,before:18,after:24,hardCap:160,stateReset:420};
          };
          const requestBudget=()=>{
            const pressure=pressureRank();
            if(pressure>=3) return 1;
            if(velocityBand==='fast') return 1;
            if(velocityBand==='medium') return pressure>=2?2:3;
            return pressure>=2?2:pressure>=1?4:6;
          };
          const nearRect=rect=>{
            if(!rect) return false;
            const viewport=Math.max(1,window.innerHeight||720);
            const pressure=pressureRank();
            const ahead=velocityBand==='fast'?Math.min(pressure>=2?180:260,viewport*(pressure>=2?0.24:0.34)):velocityBand==='medium'?Math.min(pressure>=2?320:460,viewport*(pressure>=2?0.46:0.64)):viewport*(pressure>=3?0.42:pressure>=2?0.62:pressure>=1?0.78:0.95);
            const behind=velocityBand==='fast'?(pressure>=2?48:72):velocityBand==='medium'?(pressure>=2?96:150):viewport*(pressure>=2?0.24:0.42);
            if(scrollDirection<0) return rect.bottom>=-ahead && rect.top<=viewport+behind;
            return rect.bottom>=-behind && rect.top<=viewport+ahead;
          };
          const nearByVelocity=link=>{
            if(!link?.isConnected) return false;
            try { return nearRect(link.getBoundingClientRect()); }
            catch (_) { return false; }
          };
          const rememberDeferred=link=>{
            if(!link?.isConnected) return;
            if(deferredRepair.size>=32) deferredRepair.delete(deferredRepair.values().next().value);
            deferredRepair.add(link); stats.deferred++;
          };
          const flushDeferred=()=>{
            if(!active() || !deferredRepair.size) return;
            let remaining=6;
            for(const link of Array.from(deferredRepair)) {
              if(!link?.isConnected) { deferredRepair.delete(link); continue; }
              if(remaining<=0) break;
              if(!nearByVelocity(link)) continue;
              deferredRepair.delete(link); repair(link); remaining--;
            }
            stats.flushes++;
          };
          const setMemoryPressure=value=>{
            const next=value==='critical'?'critical':value==='low'?'low':value==='moderate'?'moderate':'normal';
            if(next===memoryPressure) return false;
            memoryPressure=next; stats.pressureTransitions++;
            if(pressureRank()>=2 && deferredRepair.size) { stats.pressureDrops+=deferredRepair.size; deferredRepair.clear(); }
            if(pressureRank()>=3) compact(true); else resetScanLocality();
            if(active()) schedule();
            return true;
          };
          const markInteraction=()=>{
            lastInteractionAt=Date.now();
            if(interactionQuietToken) return;
            const settle=()=>{
              const remaining=interactionQuietMs-(Date.now()-lastInteractionAt);
              if(remaining>0) { interactionQuietToken=setTimeout(settle,remaining); return; }
              interactionQuietToken=0;
              scrollVelocity=0; velocityBand='slow';
              flushDeferred();
              schedule();
            };
            interactionQuietToken=setTimeout(settle,interactionQuietMs);
          };
          const resetScanLocality=()=>{
            lastScanTotal=-1; lastScanStart=-1; lastScanEnd=-1;
            lastScanFirst=null; lastScanMiddle=null; lastScanLast=null;
          };
          const compactState=()=>{
            targetCache=new WeakMap(); repaired=new WeakMap(); prunedSinceStateReset=0; stats.stateCompactions++;
          };
          const compact=(resetState=false)=>{
            try { visible?.disconnect(); } catch (_) {}
            visible=null; observed=new Set(); deferredRepair.clear(); resetScanLocality();
            if(resetState) compactState();
          };
          const ageObserver=(links,start,end,observer)=>{
            if(!observer || !links) return 0;
            const budget=observerAgingBudget();
            const keepStart=Math.max(0,start-budget.before);
            const keepEnd=Math.min(links.length,end+budget.after);
            const keep=new Set();
            for(let index=keepStart;index<keepEnd;index++) {
              const link=links[index];
              if(link?.isConnected) keep.add(link);
            }
            let pruned=0;
            for(const link of Array.from(observed)) {
              if(link?.isConnected && keep.has(link)) continue;
              try { observer.unobserve(link); } catch (_) {}
              observed.delete(link); deferredRepair.delete(link); pruned++;
            }
            if(observed.size>budget.hardCap) {
              for(const link of Array.from(observed)) {
                if(observed.size<=budget.hardCap) break;
                try { observer.unobserve(link); } catch (_) {}
                observed.delete(link); deferredRepair.delete(link); pruned++;
              }
            }
            stats.agingPasses++;
            if(pruned>0) {
              stats.observerPrunes+=pruned; prunedSinceStateReset+=pruned;
              if(prunedSinceStateReset>=budget.stateReset) compactState();
            }
            stats.observedPeak=Math.max(stats.observedPeak,observed.size);
            return pruned;
          };
          const ensureObserver=()=>{
            if(visible) return visible;
            // Observe a broad envelope once; velocity gates decide how many missing thumbnails
            // are allowed to start a request while the user is moving quickly.
            visible=new IntersectionObserver(entries=>{
              let remaining=requestBudget();
              const ordered=Array.from(entries).sort((a,b)=>{
                try {
                  const da=scrollDirection<0?Math.abs((window.innerHeight||720)-a.boundingClientRect.bottom):Math.abs(a.boundingClientRect.top);
                  const db=scrollDirection<0?Math.abs((window.innerHeight||720)-b.boundingClientRect.bottom):Math.abs(b.boundingClientRect.top);
                  return da-db;
                } catch (_) { return 0; }
              });
              for(const entry of ordered) {
                stats.layoutReadsSaved++;
                if(!entry.isIntersecting || !nearRect(entry.boundingClientRect)) continue;
                if(remaining>0) { repair(entry.target); remaining--; }
                else rememberDeferred(entry.target);
              }
            },{rootMargin:'620px 0px',threshold:0.01});
            return visible;
          };
          const windowStart=(total,count)=>{
            if(total<=count) return 0;
            const scroller=document.scrollingElement||document.documentElement;
            const maxScroll=Math.max(1,(scroller?.scrollHeight||0)-(window.innerHeight||0));
            const ratio=Math.max(0,Math.min(1,(window.scrollY||0)/maxScroll));
            const center=Math.round(ratio*(total-1));
            return Math.max(0,Math.min(total-count,center-Math.floor(count*0.34)));
          };
          const scan=()=>{
            idleToken=0;
            if (!active()) return;
            const links=document.querySelectorAll(selector);
            const aging=observerAgingBudget();
            const max=aging.scan;
            const observer=ensureObserver();
            const total=links.length;
            const start=windowStart(total,Math.min(max,total));
            const end=Math.min(total,start+max);
            const middle=start<end?start+Math.floor((end-start-1)/2):-1;
            const firstNode=start<end?links[start]:null;
            const middleNode=middle>=0?links[middle]:null;
            const lastNode=start<end?links[end-1]:null;
            ageObserver(links,start,end,observer);
            if(total===lastScanTotal && start===lastScanStart && end===lastScanEnd &&
                firstNode===lastScanFirst && middleNode===lastScanMiddle && lastNode===lastScanLast) {
              stats.localityHits++; return;
            }
            lastScanTotal=total; lastScanStart=start; lastScanEnd=end;
            lastScanFirst=firstNode; lastScanMiddle=middleNode; lastScanLast=lastNode;
            for(let index=start;index<end;index++) {
              const link=links[index];
              if(!link || !link.isConnected || observed.has(link)) continue;
              observed.add(link); observer.observe(link); stats.observedPeak=Math.max(stats.observedPeak,observed.size);
            }
          };
          const schedule=()=>{
            if(idleToken || !active() || interactionBusy()) return;
            const run=()=>scan();
            try {
              if(typeof requestIdleCallback==='function') idleToken=requestIdleCallback(run,{timeout:900});
              else idleToken=setTimeout(run,180);
            } catch (_) { idleToken=setTimeout(run,180); }
          };
          const reset=(resetState=false)=>{
            if(idleToken) {
              try { if(typeof cancelIdleCallback==='function') cancelIdleCallback(idleToken); } catch (_) {}
              try { clearTimeout(idleToken); } catch (_) {}
              idleToken=0;
            }
            if(interactionQuietToken) { try { clearTimeout(interactionQuietToken); } catch (_) {} interactionQuietToken=0; }
            compact(resetState);
          };
          const syncObserver=()=>{
            const enabled=active();
            const nextKey=routeKey();
            const changed=enabled && currentRouteKey && currentRouteKey!==nextKey;
            document.documentElement.classList.toggle('votuibe-search-active',enabled);
            if(!enabled) { currentRouteKey=''; reset(true); return; }
            if(changed) reset(true);
            currentRouteKey=nextKey;
            lastScrollY=window.scrollY||0;
            schedule();
          };
          window.addEventListener('touchstart',markInteraction,{passive:true});
          window.addEventListener('pointerdown',markInteraction,{passive:true});
          window.addEventListener('scroll',()=>{
            if(!active()) return;
            sampleScrollVelocity();
            markInteraction();
            const y=window.scrollY||0;
            const threshold=Math.max(560,Math.round((window.innerHeight||720)*0.75));
            if(Math.abs(y-lastScrollY)<threshold) return;
            lastScrollY=y; schedule();
          },{passive:true});
          document.addEventListener('error',event=>{
            if(!active()) return;
            const link=event.target.closest?.(selector);
            if(link) repair(link);
          },true);
          document.addEventListener('visibilitychange',syncObserver);
          document.addEventListener('yt-navigate-finish',syncObserver,true);
          window.addEventListener('popstate',syncObserver,{passive:true});
          window.addEventListener('pagehide',()=>reset(true),{passive:true});
          window.__votuibeSetSearchMemoryPressure=setMemoryPressure;
          window.__votuibeSearchPreviewDiagnostics=()=>({...stats,velocityBand,velocityPxPerSec:Math.round(scrollVelocity),scrollDirection,memoryPressure,deferredPending:deferredRepair.size,observerBudget:observerAgingBudget(),observedTargets:observed.size});
          syncObserver();
        })();
    """.trimIndent()

    fun memoryPressure(tier: MemoryPressureTier): String =
        "window.__votuibeSetSearchMemoryPressure?.('${tier.name.lowercase()}');"
}
