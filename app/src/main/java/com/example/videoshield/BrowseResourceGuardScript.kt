package com.example.videoshield

/**
 * Event-driven media cleanup, bounded feed virtualization, and feed scroll retention for browse
 * surfaces other than Shorts.
 *
 * Browse pages can create transient <video>/<audio> previews while the SPA remains alive. When
 * the route changes or the dedicated watch surface covers the browse WebView, those media nodes
 * should stop competing for decoder/buffer memory. The feed guard keeps Home/Search/
 * Subscriptions cards cheap outside the viewport without reordering/removing YouTube-owned DOM.
 * Observer targets age out around the active viewport so long SPA sessions retain only a bounded card window.
 * It never tears down MediaSource, never calls load(), and never resumes media automatically.
 */
object BrowseResourceGuardScript {
    fun install(): String = """
        (() => {
          if (window.__votuibeBrowseResourceGuardInstalled) return;
          window.__votuibeBrowseResourceGuardInstalled=true;
          let transitionFrame=0;
          let feedIdleToken=0;
          let interactionQuietToken=0;
          let lastInteractionAt=0;
          const interactionQuietMs=180;
          let scrollSampleY=window.scrollY||0;
          let scrollSampleAt=(performance?.now?.()||Date.now());
          let scrollVelocity=0;
          let scrollDirection=0;
          let velocityBand='slow';
          let memoryPressure='normal';
          let feedObserver=null;
          let feedObserverMargin='';
          let feedObserved=new Set();
          let prunedSinceStateReset=0;
          let feedCardState=new WeakMap();
          let lastScanTotal=-1;
          let lastScanStart=-1;
          let lastScanEnd=-1;
          let lastScanFirst=null;
          let lastScanMiddle=null;
          let lastScanLast=null;
          let feedSuspended=false;
          let activeFeedKey='';
          let lastFeedScrollY=window.scrollY||0;
          let lastStoredScrollY=-1;
          let lastStoredScrollAt=0;
          let restoreGeneration=0;
          const feedStats={scans:0,observed:0,coalesced:0,coldPauses:0,compactions:0,restores:0,interactionDeferrals:0,velocityTransitions:0,decodePromotions:0,decodeDeferrals:0,flingSettles:0,pressureTransitions:0,pressureTrims:0,reuseHits:0,retunes:0,identityChanges:0,mutationSkips:0,localityHits:0,layoutReadsSaved:0,observerPrunes:0,agingPasses:0,stateCompactions:0,observedPeak:0,scrollSnapshotPrunes:0};
          const scrollPrefix='__votuibeFeedScroll:';
          const scrollIndexKey='__votuibeFeedScrollIndex';
          const isShorts=()=>/^\/shorts(?:\/|$)/.test(location.pathname||'');
          const isFeed=()=>!!feedKey();
          const constrained=()=>!!window.__videoShieldPowerConstrained;
          const sanitizePreload=value=>value==='none'||value==='metadata'||value==='auto'?value:'metadata';
          const feedKey=()=>{
            const p=location.pathname||'';
            if(p===''||p==='/') return 'HOME';
            if(p==='/feed/subscriptions') return 'SUBSCRIPTIONS';
            if(p==='/results') {
              try {
                const u=new URL(location.href);
                return 'SEARCH:'+(u.searchParams.get('search_query')||'')+':'+(u.searchParams.get('sp')||'');
              } catch (_) { return 'SEARCH'; }
            }
            return '';
          };
          const storageKey=key=>scrollPrefix+encodeURIComponent(key).slice(0,640);
          const scrollSnapshotLimit=()=>memoryPressure==='critical'?2:memoryPressure==='low'?4:memoryPressure==='moderate'?6:8;
          const compactScrollSnapshots=()=>{
            try {
              const parsed=JSON.parse(sessionStorage.getItem(scrollIndexKey)||'[]');
              const old=Array.isArray(parsed)?parsed:[];
              const next=old.slice(0,scrollSnapshotLimit());
              if(next.length!==old.length) {
                sessionStorage.setItem(scrollIndexKey,JSON.stringify(next));
                for(const stale of old.slice(next.length)) sessionStorage.removeItem(stale);
                feedStats.scrollSnapshotPrunes+=Math.max(0,old.length-next.length);
              }
            } catch (_) {}
          };
          const rememberScrollIndex=key=>{
            try {
              const stored=storageKey(key);
              const parsed=JSON.parse(sessionStorage.getItem(scrollIndexKey)||'[]');
              const old=Array.isArray(parsed)?parsed:[];
              const next=[stored,...old.filter(item=>item!==stored)].slice(0,scrollSnapshotLimit());
              sessionStorage.setItem(scrollIndexKey,JSON.stringify(next));
              for(const stale of old) if(!next.includes(stale)) sessionStorage.removeItem(stale);
            } catch (_) {}
          };
          const storeScroll=(force=false,key=activeFeedKey)=>{
            if(!key) return false;
            const y=Math.max(0,Math.round(window.scrollY||0));
            const now=Date.now();
            if(!force && Math.abs(y-lastStoredScrollY)<128 && now-lastStoredScrollAt<900) return false;
            try {
              sessionStorage.setItem(storageKey(key),JSON.stringify({y,t:now}));
              rememberScrollIndex(key);
              lastStoredScrollY=y; lastStoredScrollAt=now;
              return true;
            } catch (_) { return false; }
          };
          const storedScroll=key=>{
            if(!key) return 0;
            try {
              const item=JSON.parse(sessionStorage.getItem(storageKey(key))||'null');
              return item && Number.isFinite(item.y) ? Math.max(0,Math.round(item.y)) : 0;
            } catch (_) { return 0; }
          };
          const restoreScroll=key=>{
            const wanted=storedScroll(key);
            if(!key || wanted<=8 || (window.scrollY||0)>24) return false;
            const generation=++restoreGeneration;
            const delays=[0,90,220,460,760];
            let lastApplied=0;
            const attempt=index=>{
              const current=Math.max(0,Math.round(window.scrollY||0));
              if(generation!==restoreGeneration || feedSuspended || feedKey()!==key) return;
              // After the first attempt, stop only when the user/YouTube moved materially away
              // from the position this restore sequence last applied. A clamped position may
              // legitimately be >32px while the SPA is still growing toward the saved offset.
              if(index>0 && Math.abs(current-lastApplied)>56) return;
              const scroller=document.scrollingElement||document.documentElement;
              const maxScroll=Math.max(0,(scroller?.scrollHeight||0)-(window.innerHeight||0));
              const target=Math.min(wanted,maxScroll);
              lastApplied=target;
              if(target>0) {
                try { window.scrollTo(0,target); } catch (_) {}
              }
              if(target>=wanted-24 || index>=delays.length-1) {
                if(target>0) feedStats.restores++;
                return;
              }
              setTimeout(()=>attempt(index+1),delays[index+1]-delays[index]);
            };
            try { requestAnimationFrame(()=>attempt(0)); }
            catch (_) { setTimeout(()=>attempt(0),24); }
            return true;
          };
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
              if(next!==velocityBand) { velocityBand=next; feedStats.velocityTransitions++; }
            }
            scrollSampleY=y; scrollSampleAt=now;
            return velocityBand;
          };
          const pressureRank=()=>memoryPressure==='critical'?3:memoryPressure==='low'?2:memoryPressure==='moderate'?1:0;
          const observerAgingBudget=()=>{
            const pressure=pressureRank();
            if(pressure>=3) return {scan:56,before:8,after:12,hardCap:80,stateReset:200};
            if(pressure>=2) return {scan:80,before:12,after:16,hardCap:112,stateReset:320};
            if(pressure>=1) return {scan:112,before:20,after:24,hardCap:160,stateReset:480};
            if(constrained()) return {scan:96,before:16,after:24,hardCap:144,stateReset:480};
            return {scan:144,before:24,after:32,hardCap:200,stateReset:640};
          };
          const velocityBudget=()=>{
            const viewport=Math.max(1,window.innerHeight||720);
            const pressure=pressureRank();
            if(velocityBand==='fast') return {ahead:Math.min(pressure>=2?220:320,viewport*(pressure>=2?0.28:0.38)),behind:pressure>=2?64:96,cards:pressure>=3?1:2,images:0};
            if(velocityBand==='medium') return {ahead:Math.min(pressure>=2?360:560,viewport*(pressure>=2?0.48:0.72)),behind:pressure>=2?112:180,cards:pressure>=3?2:pressure>=2?3:5,images:pressure>=3?1:pressure>=2?2:pressure>=1?4:6};
            if(pressure>=3) return {ahead:viewport*0.42,behind:160,cards:3,images:2};
            if(pressure>=2) return {ahead:viewport*0.58,behind:220,cards:5,images:6};
            if(pressure>=1) return {ahead:viewport*0.82,behind:280,cards:8,images:12};
            return {ahead:constrained()?viewport*0.72:viewport*1.18,behind:constrained()?320:viewport*0.58,cards:12,images:24};
          };
          const cardNearRect=rect=>{
            if(!rect) return false;
            const viewport=Math.max(1,window.innerHeight||720);
            const budget=velocityBudget();
            if(scrollDirection<0) return rect.bottom>=-budget.ahead && rect.top<=viewport+budget.behind;
            if(scrollDirection>0) return rect.bottom>=-budget.behind && rect.top<=viewport+budget.ahead;
            return rect.bottom>=-budget.behind && rect.top<=viewport+budget.ahead;
          };
          const cardNearByVelocity=card=>{
            if(!card?.isConnected) return false;
            try { return cardNearRect(card.getBoundingClientRect()); }
            catch (_) { return false; }
          };
          const settleFling=previousBand=>{
            if(previousBand==='slow' || feedSuspended || !isFeed()) return false;
            const viewport=Math.max(1,window.innerHeight||720);
            const cards=document.querySelectorAll(feedSelector);
            const total=cards.length;
            const max=Math.min(total,previousBand==='fast'?112:88);
            const start=windowStart(total,max);
            const end=Math.min(total,start+max);
            const keepTop=-Math.round(viewport*0.24);
            const keepBottom=Math.round(viewport*1.18);
            let touched=0;
            for(let index=start;index<end;index++) {
              const card=cards[index];
              if(!card?.isConnected) continue;
              try {
                const rect=card.getBoundingClientRect();
                const keep=rect.bottom>=keepTop && rect.top<=keepBottom;
                // Fling settling is deliberately demotion-only: do not launch new image work
                // immediately after a fast gesture. A later idle scan may promote nearby cards.
                tuneCard(card,keep,0); touched++;
              } catch (_) {}
            }
            if(touched>0) feedStats.flingSettles++;
            return touched>0;
          };
          const trimFeedForPressure=()=>{
            if(feedSuspended || !isFeed()) return false;
            const viewport=Math.max(1,window.innerHeight||720);
            const cards=document.querySelectorAll(feedSelector);
            const total=cards.length;
            const max=Math.min(total,pressureRank()>=3?128:96);
            const start=windowStart(total,max);
            const end=Math.min(total,start+max);
            let touched=0;
            for(let index=start;index<end;index++) {
              const card=cards[index];
              if(!card?.isConnected) continue;
              try {
                const rect=card.getBoundingClientRect();
                const visible=rect.bottom>=-48 && rect.top<=viewport+48;
                tuneCard(card,visible,0); touched++;
              } catch (_) {}
            }
            if(touched>0) feedStats.pressureTrims++;
            return touched>0;
          };
          const setMemoryPressure=value=>{
            const next=value==='critical'?'critical':value==='low'?'low':value==='moderate'?'moderate':'normal';
            if(next===memoryPressure) return false;
            memoryPressure=next; feedStats.pressureTransitions++;
            compactScrollSnapshots();
            if(pressureRank()>=3) compactFeedObserver(true); else resetScanLocality();
            if(pressureRank()>=1) trimFeedForPressure();
            if(!feedSuspended && isFeed() && document.visibilityState!=='hidden') scheduleFeedScan();
            return true;
          };
          const markInteraction=()=>{
            lastInteractionAt=Date.now();
            if(interactionQuietToken) return;
            const settle=()=>{
              const remaining=interactionQuietMs-(Date.now()-lastInteractionAt);
              if(remaining>0) { interactionQuietToken=setTimeout(settle,remaining); return; }
              interactionQuietToken=0;
              const previousBand=velocityBand;
              const wasMoving=previousBand!=='slow';
              scrollVelocity=0; velocityBand='slow';
              if(wasMoving) { settleFling(previousBand); resetScanLocality(); }
              if(!feedSuspended && isFeed() && document.visibilityState!=='hidden') scheduleFeedScan();
            };
            interactionQuietToken=setTimeout(settle,interactionQuietMs);
          };
          const feedSelector=[
            'ytm-rich-item-renderer',
            'ytm-video-with-context-renderer',
            'ytm-compact-video-renderer',
            'ytm-channel-renderer',
            'ytm-playlist-renderer',
            'ytm-shorts-lockup-view-model',
            'ytm-subscription-list-item-renderer'
          ].join(',');
          const intrinsicSize=card=>{
            const tag=(card?.tagName||'').toLowerCase();
            if(tag.includes('channel')||tag.includes('subscription')) return '112px';
            if(tag.includes('playlist')) return '230px';
            if(tag.includes('shorts')) return '220px 390px';
            return '330px';
          };
          const cardIdentity=(card,previous)=>{
            let link=previous?.identityLink||null;
            try {
              if(!link?.isConnected || !card.contains(link)) {
                link=card.querySelector?.('a[href*="/watch?"],a[href*="/shorts/"]')||null;
              }
              const href=link?.getAttribute?.('href')||'';
              const explicit=card.getAttribute?.('data-video-id')||card.getAttribute?.('data-content-id')||'';
              return {value:(card.tagName||'')+'|'+explicit+'|'+href,link};
            } catch (_) { return {value:(card.tagName||'')+'|',link:null}; }
          };
          const tuneCard=(card,near=false,promoteImages=0)=>{
            if(!card || !card.isConnected) return false;
            const previous=feedCardState.get(card);
            const identity=cardIdentity(card,previous);
            const requestedPromotions=Math.max(0,promoteImages|0);
            const targetSignature=(near?'1':'0')+'|'+Math.min(8,requestedPromotions)+'|'+memoryPressure;
            const now=Date.now();
            if(previous && previous.identity===identity.value && previous.signature===targetSignature &&
                now-previous.at<2400) {
              previous.identityLink=identity.link; feedStats.reuseHits++;
              return false;
            }
            if(previous && previous.identity!==identity.value) feedStats.identityChanges++;
            feedStats.retunes++;
            try {
              if(card.style.contentVisibility!=='auto') card.style.contentVisibility='auto'; else feedStats.mutationSkips++;
              if(card.style.contain!=='layout paint style') card.style.contain='layout paint style'; else feedStats.mutationSkips++;
              if(!card.style.containIntrinsicSize) card.style.containIntrinsicSize=intrinsicSize(card); else feedStats.mutationSkips++;
            } catch (_) {}
            const images=Array.from(card.querySelectorAll?.('img')||[]).slice(0,8);
            let imagePromotions=requestedPromotions;
            for(const image of images) {
              try {
                if(image.loading!=='lazy') image.loading='lazy'; else feedStats.mutationSkips++;
                if(image.decoding!=='async') image.decoding='async'; else feedStats.mutationSkips++;
                const priority=near && imagePromotions>0?'auto':'low';
                if(image.fetchPriority!==priority) image.fetchPriority=priority; else feedStats.mutationSkips++;
                if(priority==='auto') { imagePromotions--; feedStats.decodePromotions++; }
                else feedStats.decodeDeferrals++;
              } catch (_) {}
            }
            const media=Array.from(card.querySelectorAll?.('video,audio')||[]).slice(0,4);
            for(const node of media) {
              try {
                if(!near && !node.paused && !node.ended) { node.pause(); feedStats.coldPauses++; }
                if(node.tagName==='VIDEO') {
                  const next=near?'metadata':'none';
                  if(sanitizePreload(node.preload)!==next) node.preload=next; else feedStats.mutationSkips++;
                  if(node.getAttribute('preload')!==next) node.setAttribute('preload',next); else feedStats.mutationSkips++;
                }
              } catch (_) {}
            }
            feedCardState.set(card,{identity:identity.value,identityLink:identity.link,signature:targetSignature,at:now});
            return true;
          };
          const resetScanLocality=()=>{
            lastScanTotal=-1; lastScanStart=-1; lastScanEnd=-1;
            lastScanFirst=null; lastScanMiddle=null; lastScanLast=null;
          };
          const compactFeedState=()=>{
            feedCardState=new WeakMap(); prunedSinceStateReset=0; feedStats.stateCompactions++;
          };
          const compactFeedObserver=(resetState=false)=>{
            const hadTargets=!!feedObserver || feedObserved.size>0;
            try { feedObserver?.disconnect(); } catch (_) {}
            feedObserver=null; feedObserverMargin=''; feedObserved=new Set();
            resetScanLocality();
            if(resetState) compactFeedState();
            if(hadTargets) feedStats.compactions++;
          };
          const ageFeedObserver=(cards,start,end,observer)=>{
            if(!observer || !cards) return 0;
            const budget=observerAgingBudget();
            const keepStart=Math.max(0,start-budget.before);
            const keepEnd=Math.min(cards.length,end+budget.after);
            const keep=new Set();
            for(let index=keepStart;index<keepEnd;index++) {
              const card=cards[index];
              if(card?.isConnected) keep.add(card);
            }
            let pruned=0;
            for(const card of Array.from(feedObserved)) {
              if(card?.isConnected && keep.has(card)) continue;
              try { observer.unobserve(card); } catch (_) {}
              feedObserved.delete(card); pruned++;
            }
            // A hard cap is a final guard against unusual SPA ordering/recycling. Prefer dropping
            // oldest retained targets; a later idle scan will re-observe any nearby survivor.
            if(feedObserved.size>budget.hardCap) {
              for(const card of Array.from(feedObserved)) {
                if(feedObserved.size<=budget.hardCap) break;
                try { observer.unobserve(card); } catch (_) {}
                feedObserved.delete(card); pruned++;
              }
            }
            feedStats.agingPasses++;
            if(pruned>0) {
              feedStats.observerPrunes+=pruned; prunedSinceStateReset+=pruned;
              if(prunedSinceStateReset>=budget.stateReset) compactFeedState();
            }
            feedStats.observedPeak=Math.max(feedStats.observedPeak,feedObserved.size);
            return pruned;
          };
          const ensureFeedObserver=()=>{
            // Keep one broad observation envelope. Actual prefetch/decode work is narrowed by
            // velocityBudget(), so changing scroll speed does not rebuild observers every frame.
            const margin=constrained()?'560px 0px':'920px 0px';
            if(feedObserver && feedObserverMargin===margin) return feedObserver;
            compactFeedObserver();
            feedObserverMargin=margin;
            feedObserver=new IntersectionObserver(entries=>{
              const budget=velocityBudget();
              let remaining=budget.cards;
              let remainingImages=budget.images;
              const ordered=Array.from(entries).sort((a,b)=>{
                try {
                  const viewport=Math.max(1,window.innerHeight||720);
                  const da=scrollDirection<0?Math.abs(viewport-a.boundingClientRect.bottom):Math.abs(a.boundingClientRect.top);
                  const db=scrollDirection<0?Math.abs(viewport-b.boundingClientRect.bottom):Math.abs(b.boundingClientRect.top);
                  return da-db;
                } catch (_) { return 0; }
              });
              for(const entry of ordered) {
                feedStats.layoutReadsSaved++;
                const near=!!entry.isIntersecting && remaining>0 && cardNearRect(entry.boundingClientRect);
                let imageAllowance=0;
                if(near) {
                  remaining--;
                  imageAllowance=Math.min(remainingImages,velocityBand==='slow'?3:2);
                  remainingImages-=imageAllowance;
                }
                tuneCard(entry.target,near,imageAllowance);
              }
            },{root:null,rootMargin:margin,threshold:0.01});
            return feedObserver;
          };
          const windowStart=(total,count)=>{
            if(total<=count) return 0;
            const scroller=document.scrollingElement||document.documentElement;
            const maxScroll=Math.max(1,(scroller?.scrollHeight||0)-(window.innerHeight||0));
            const ratio=Math.max(0,Math.min(1,(window.scrollY||0)/maxScroll));
            const center=Math.round(ratio*(total-1));
            return Math.max(0,Math.min(total-count,center-Math.floor(count*0.36)));
          };
          const scanFeed=()=>{
            if(feedSuspended || !isFeed() || document.visibilityState==='hidden') return false;
            const cards=document.querySelectorAll(feedSelector);
            const aging=observerAgingBudget();
            const max=aging.scan;
            const observer=ensureFeedObserver();
            const budget=velocityBudget();
            let remainingImages=budget.images;
            const total=cards.length;
            const start=windowStart(total,Math.min(max,total));
            const end=Math.min(total,start+max);
            const middle=start<end?start+Math.floor((end-start-1)/2):-1;
            const firstNode=start<end?cards[start]:null;
            const middleNode=middle>=0?cards[middle]:null;
            const lastNode=start<end?cards[end-1]:null;
            ageFeedObserver(cards,start,end,observer);
            if(total===lastScanTotal && start===lastScanStart && end===lastScanEnd &&
                firstNode===lastScanFirst && middleNode===lastScanMiddle && lastNode===lastScanLast) {
              feedStats.localityHits++; feedStats.scans++; return true;
            }
            lastScanTotal=total; lastScanStart=start; lastScanEnd=end;
            lastScanFirst=firstNode; lastScanMiddle=middleNode; lastScanLast=lastNode;
            for(let index=start;index<end;index++) {
              const card=cards[index];
              if(!card || !card.isConnected || feedObserved.has(card)) continue;
              feedObserved.add(card); observer.observe(card); feedStats.observed++; feedStats.observedPeak=Math.max(feedStats.observedPeak,feedObserved.size);
              const near=cardNearByVelocity(card);
              const imageAllowance=near?Math.min(remainingImages,velocityBand==='slow'?3:2):0;
              remainingImages-=imageAllowance;
              tuneCard(card,near,imageAllowance);
            }
            feedStats.scans++;
            return true;
          };
          const scheduleFeedScan=()=>{
            if(feedSuspended || !isFeed() || document.visibilityState==='hidden') return false;
            if(interactionBusy()) { feedStats.interactionDeferrals++; return false; }
            if(feedIdleToken) { feedStats.coalesced++; return false; }
            const run=()=>{ feedIdleToken=0; scanFeed(); };
            try {
              if(typeof requestIdleCallback==='function') feedIdleToken=requestIdleCallback(run,{timeout:constrained()?1400:800});
              else feedIdleToken=setTimeout(run,constrained()?240:140);
            } catch (_) { feedIdleToken=setTimeout(run,180); }
            return true;
          };
          const stopFeed=(remember=true)=>{
            if(remember) storeScroll(true);
            feedSuspended=true; restoreGeneration++;
            if(feedIdleToken) {
              try { if(typeof cancelIdleCallback==='function') cancelIdleCallback(feedIdleToken); } catch (_) {}
              try { clearTimeout(feedIdleToken); } catch (_) {}
              feedIdleToken=0;
            }
            if(interactionQuietToken) { try { clearTimeout(interactionQuietToken); } catch (_) {} interactionQuietToken=0; }
            compactFeedObserver(true);
          };
          const resumeFeed=(restore=false)=>{
            feedSuspended=false;
            lastFeedScrollY=window.scrollY||0;
            scheduleFeedScan();
            if(restore) restoreScroll(activeFeedKey);
          };
          const trim=(aggressive=false)=>{
            if(isShorts()) return {media:0,paused:0,aggressive:false};
            let paused=0;
            const media=Array.from(document.querySelectorAll('video,audio')).slice(0,48);
            for(const node of media) {
              try {
                if(!node.paused && !node.ended) { node.pause(); paused++; }
                // Do not remove src / call load(): browse previews may be MediaSource-backed and
                // Chromium/YouTube own those source lifetimes. Downgrading preload is enough to
                // make hidden previews cheap without corrupting a future SPA reuse.
                if(node.tagName==='VIDEO') {
                  const next=aggressive?'none':'metadata';
                  if(sanitizePreload(node.preload)!==next) node.preload=next;
                  if(node.getAttribute('preload')!==next) node.setAttribute('preload',next);
                }
              } catch (_) {}
            }
            return {media:media.length,paused,aggressive:!!aggressive};
          };
          const transition=()=>{
            storeScroll(true);
            if(!feedSuspended) scheduleFeedScan();
            if(transitionFrame) return;
            const run=()=>{ transitionFrame=0; trim(false); };
            try { transitionFrame=requestAnimationFrame(run); }
            catch (_) { transitionFrame=setTimeout(run,24); }
          };
          const suspend=()=>{ storeScroll(true); stopFeed(false); return trim(true); };
          const resume=()=>{
            if(isShorts()) return false;
            // Browsing resumes from a quiet surface. Restore only the preload hint; playback
            // remains user/YouTube initiated so opening/closing mini-player cannot start previews.
            for(const node of Array.from(document.querySelectorAll('video')).slice(0,48)) {
              try {
                if(node.preload==='none' || node.getAttribute('preload')==='none') {
                  node.preload='metadata'; node.setAttribute('preload','metadata');
                }
              } catch (_) {}
            }
            activeFeedKey=feedKey()||activeFeedKey;
            resumeFeed(false);
            return true;
          };
          const release=()=>{
            storeScroll(true); stopFeed(false);
            if(transitionFrame) {
              try { cancelAnimationFrame(transitionFrame); } catch (_) {}
              try { clearTimeout(transitionFrame); } catch (_) {}
              transitionFrame=0;
            }
            return trim(true);
          };
          const onScroll=()=>{
            if(feedSuspended || !isFeed()) return;
            sampleScrollVelocity();
            markInteraction();
            storeScroll(false);
            const y=window.scrollY||0;
            const threshold=Math.max(640,Math.round((window.innerHeight||720)*0.85));
            if(Math.abs(y-lastFeedScrollY)<threshold) return;
            lastFeedScrollY=y; scheduleFeedScan();
          };
          const onRoute=()=>{
            const nextKey=feedKey();
            if(!nextKey) { stopFeed(false); return; }
            const changed=!!activeFeedKey && activeFeedKey!==nextKey;
            if(changed) compactFeedObserver(true);
            activeFeedKey=nextKey;
            lastStoredScrollY=-1; lastStoredScrollAt=0;
            resumeFeed(changed || (window.scrollY||0)<=8);
          };
          const onVisibility=()=>{
            if(document.visibilityState==='hidden') { storeScroll(true); stopFeed(false); }
            else onRoute();
          };
          // Capture feed position before YouTube handles a navigation click. This avoids relying
          // on yt-navigate-finish, which may already have reset scrollTop for the destination.
          document.addEventListener('click',event=>{
            if(!feedSuspended && activeFeedKey && event.target.closest?.('a[href]')) storeScroll(true);
          },true);
          window.addEventListener('touchstart',markInteraction,{passive:true});
          window.addEventListener('pointerdown',markInteraction,{passive:true});
          window.addEventListener('scroll',onScroll,{passive:true});
          window.addEventListener('pagehide',()=>storeScroll(true),{passive:true});
          window.addEventListener('popstate',onRoute,{passive:true});
          document.addEventListener('yt-navigate-finish',onRoute,true);
          document.addEventListener('visibilitychange',onVisibility);
          window.__votuibeTrimBrowseMedia=trim;
          window.__votuibeBrowseRouteTransition=transition;
          window.__votuibeSuspendBrowseMedia=suspend;
          window.__votuibeResumeBrowseMedia=resume;
          window.__votuibeReleaseBrowseMedia=release;
          window.__votuibeScheduleFeedScan=scheduleFeedScan;
          window.__votuibeRememberFeedScroll=()=>storeScroll(true);
          window.__votuibeSetFeedMemoryPressure=setMemoryPressure;
          window.__votuibeFeedDiagnostics=()=>({...feedStats,pending:!!feedIdleToken,interacting:interactionBusy(),suspended:feedSuspended,key:activeFeedKey,velocityPxPerSec:Math.round(scrollVelocity),velocityBand,scrollDirection,memoryPressure,prefetch:velocityBudget(),observerBudget:observerAgingBudget(),observedTargets:feedObserved.size,scrollSnapshotLimit:scrollSnapshotLimit()});
          onRoute();
        })();
    """.trimIndent()

    fun transition(): String = "window.__votuibeBrowseRouteTransition?.();"
    fun suspend(): String = "window.__votuibeSuspendBrowseMedia?.();"
    fun resume(): String = "window.__votuibeResumeBrowseMedia?.();"
    fun release(): String = "window.__votuibeReleaseBrowseMedia?.();"
    fun rememberScroll(): String = "window.__votuibeRememberFeedScroll?.();"
    fun memoryPressure(tier: MemoryPressureTier): String =
        "window.__votuibeSetFeedMemoryPressure?.('${tier.name.lowercase()}');"
}
