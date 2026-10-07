package com.example.videoshield

/**
 * Keeps the long-running Shorts browse surface cheap without rebuilding the feed.
 *
 * The feed keeps exactly one warm neighbour on either side of the active Short. Distant
 * inactive media are paused and downgraded to preload=none on every Short transition.
 * Decoder/buffer reset is intentionally reserved for real memory pressure, a bloated retained
 * video set, or the periodic hard pass so normal swiping stays smooth.
 */
object ShortsResourceGuardScript {
    fun install(): String = """
        (() => {
          if (window.__votuibeShortsResourceGuardInstalled) return;
          window.__votuibeShortsResourceGuardInstalled=true;
          const isShorts=()=>location.pathname.startsWith('/shorts/');
          let activeVideo=null;
          let resumeAfterSuspend=false;
          let guardActive=false;
          let transitionFrame=0;
          let deferredHardTrim=0;
          const cancelIdle=(handle)=>{
            if(!handle) return;
            try { if(typeof cancelIdleCallback==='function') cancelIdleCallback(handle); } catch (_) {}
            try { clearTimeout(handle); } catch (_) {}
          };
          const installVisualPolish=()=>{
            try {
              const id='votuibe-shorts-polish-style';
              if(document.getElementById(id)) return;
              const style=document.createElement('style');
              style.id=id;
              style.textContent=`
                /* Keep YouTube's Shorts structure/gestures, but normalize the chrome to the
                   same compact visual language as the native app shell. Unknown selectors
                   intentionally no-op across server-side markup experiments. */
                ytm-reel-player-overlay-renderer {
                  --votuibe-shorts-control:48px;
                  --votuibe-shorts-control-radius:24px;
                }
                ytm-reel-player-overlay-renderer button,
                ytm-reel-player-overlay-renderer [role=button] {
                  -webkit-tap-highlight-color:transparent !important;
                }
                ytm-reel-player-overlay-renderer ytm-like-button-renderer button,
                ytm-reel-player-overlay-renderer ytm-dislike-button-renderer button,
                ytm-reel-player-overlay-renderer ytm-share-button-renderer button,
                ytm-reel-player-overlay-renderer ytm-button-renderer.reel-action-button button,
                ytm-reel-player-overlay-renderer .reel-player-overlay-actions button {
                  width:var(--votuibe-shorts-control) !important;
                  min-width:var(--votuibe-shorts-control) !important;
                  height:var(--votuibe-shorts-control) !important;
                  min-height:var(--votuibe-shorts-control) !important;
                  padding:0 !important;
                  border-radius:var(--votuibe-shorts-control-radius) !important;
                  background:rgba(0,0,0,.30) !important;
                  backdrop-filter:none !important;
                  transition:transform 80ms ease-out,background-color 80ms linear !important;
                }
                ytm-reel-player-overlay-renderer .reel-player-overlay-actions button:active,
                ytm-reel-player-overlay-renderer ytm-like-button-renderer button:active,
                ytm-reel-player-overlay-renderer ytm-share-button-renderer button:active {
                  transform:scale(.94) !important;
                  background:rgba(0,0,0,.42) !important;
                }
                ytm-reel-player-overlay-renderer ytm-subscribe-button-renderer button,
                ytm-reel-player-overlay-renderer .subscribe-button button {
                  min-height:36px !important;
                  padding-inline:14px !important;
                  border-radius:18px !important;
                  font-weight:600 !important;
                }
                ytm-reel-player-overlay-renderer .reel-player-header,
                ytm-reel-player-overlay-renderer .metadata-container,
                ytm-reel-player-overlay-renderer .reel-player-overlay-metadata {
                  text-shadow:0 1px 2px rgba(0,0,0,.75) !important;
                }
                ytm-reel-player-overlay-renderer .reel-player-overlay-metadata,
                ytm-reel-player-overlay-renderer .metadata-container {
                  padding-bottom:max(12px,env(safe-area-inset-bottom)) !important;
                }
                html[data-votuibe-shorts-chrome-hidden="true"] ytm-reel-player-overlay-renderer .reel-player-overlay-actions,
                html[data-votuibe-shorts-chrome-hidden="true"] ytm-reel-player-overlay-renderer .reel-player-overlay-metadata,
                html[data-votuibe-shorts-chrome-hidden="true"] ytm-reel-player-overlay-renderer .metadata-container,
                html[data-votuibe-shorts-chrome-hidden="true"] ytm-reel-player-overlay-renderer .reel-player-header,
                html[data-votuibe-shorts-chrome-hidden="true"] ytm-reel-player-overlay-renderer ytm-like-button-renderer,
                html[data-votuibe-shorts-chrome-hidden="true"] ytm-reel-player-overlay-renderer ytm-dislike-button-renderer,
                html[data-votuibe-shorts-chrome-hidden="true"] ytm-reel-player-overlay-renderer ytm-share-button-renderer,
                html[data-votuibe-shorts-chrome-hidden="true"] ytm-reel-player-overlay-renderer ytm-subscribe-button-renderer,
                html[data-votuibe-shorts-chrome-hidden="true"] ytm-reel-player-overlay-renderer ytm-button-renderer.reel-action-button {
                  opacity:0 !important;
                  pointer-events:none !important;
                  transform:translateY(4px) !important;
                  transition:opacity 140ms linear,transform 160ms ease-out !important;
                }
              `;
              (document.head||document.documentElement).appendChild(style);
            } catch (_) {}
          };
          installVisualPolish();

          // YouTube now provides an explicit Clear Screen action server-side. Do not replace
          // or intercept that menu. This optional local policy only auto-hides known overlay
          // chrome after an idle period and is disabled by default. It is one-shot/event-driven:
          // there is no repeating timer or DOM observer.
          let autoHideChrome=false,chromeHideTimer=0,chromeHidden=false;
          const clearChromeHideTimer=()=>{ if(chromeHideTimer){ clearTimeout(chromeHideTimer); chromeHideTimer=0; } };
          const setChromeHidden=hidden=>{
            hidden=!!hidden && isShorts();
            if(chromeHidden===hidden) return;
            chromeHidden=hidden;
            try {
              if(hidden) document.documentElement.setAttribute('data-votuibe-shorts-chrome-hidden','true');
              else document.documentElement.removeAttribute('data-votuibe-shorts-chrome-hidden');
            } catch (_) {}
            try { window.VoTuibeNavigation?.shortsChromeHidden?.(hidden); } catch (_) {}
          };
          const currentActiveVideo=()=>{
            // play capture keeps activeVideo hot on the normal Shorts path. Avoid a full DOM
            // video scan every time the one-shot idle timer expires; geometry/DOM lookup stays
            // a fallback only for initial/buffering cases or after YouTube recycles the node.
            if(activeVideo && activeVideo.isConnected!==false) return activeVideo;
            return resolveActive(Array.from(document.querySelectorAll('video')));
          };
          const scheduleChromeHide=()=>{
            clearChromeHideTimer();
            if(!autoHideChrome || !isShorts() || document.visibilityState==='hidden') return;
            chromeHideTimer=setTimeout(()=>{
              chromeHideTimer=0;
              if(!autoHideChrome || !isShorts() || document.visibilityState==='hidden') return;
              const current=currentActiveVideo();
              if(current && !current.paused && !current.ended) setChromeHidden(true);
            },2600);
          };
          const revealChrome=(reschedule=false)=>{
            clearChromeHideTimer();
            setChromeHidden(false);
            if(reschedule) scheduleChromeHide();
          };

          // YouTube's 2026 Shorts player supports temporary 2x playback while holding an
          // edge of the video. Provide the same interaction as a lightweight fallback for
          // mobile-web builds that have not received that server-side feature yet. Never
          // Do not cancel default pointer behavior: vertical swipe navigation stays YouTube-owned.
          let holdTimer=0,holdPointer=-1,holdStartX=0,holdStartY=0,holdVideo=null,holdBaseRate=1,hold2x=false,holdLocked=false;
          let lockedVideo=null,lockedBaseRate=1,lockMoveAttached=false;
          let holdFeedback=null;
          const interactiveTarget=target=>{
            try { return !!target?.closest?.('button,a,input,textarea,select,[role=button],[role=slider]'); }
            catch (_) { return false; }
          };
          const feedback2x=(show,label='2\u00d7')=>{
            try {
              if(!holdFeedback) {
                holdFeedback=document.createElement('div');
                Object.assign(holdFeedback.style,{
                  position:'fixed',left:'50%',top:'14%',transform:'translate(-50%,-50%) scale(.92)',
                  zIndex:'2147483646',padding:'7px 13px',borderRadius:'18px',background:'rgba(0,0,0,.58)',
                  color:'#fff',font:'600 14px/20px Roboto,Arial,sans-serif',pointerEvents:'none',
                  opacity:'0',transition:'opacity 100ms linear,transform 100ms ease-out'
                });
                (document.body||document.documentElement).appendChild(holdFeedback);
              }
              holdFeedback.textContent=label;
              holdFeedback.style.opacity=show?'1':'0';
              holdFeedback.style.transform=show?'translate(-50%,-50%) scale(1)':'translate(-50%,-50%) scale(.92)';
            } catch (_) {}
          };
          const releaseLockedSpeed=()=>{
            if(lockedVideo) {
              try { lockedVideo.playbackRate=Math.max(.25,Math.min(4,Number(lockedBaseRate)||1)); } catch (_) {}
            }
            lockedVideo=null; lockedBaseRate=1;
          };
          const onLockMove=event=>{
            if(event.pointerId!==holdPointer || !hold2x) return;
            const dx=(Number(event.clientX)||0)-holdStartX,dy=(Number(event.clientY)||0)-holdStartY;
            if(holdLocked || (dy>52 && Math.abs(dy)>Math.abs(dx)*1.15)) {
              // This listener exists only after the user has deliberately held long enough for
              // temporary 2x. Prevent scrolling only for the lock gesture so normal Shorts
              // swipes stay on the passive hot path.
              try { event.preventDefault(); } catch (_) {}
              if(!holdLocked) { holdLocked=true; feedback2x(true,'2\u00d7 locked'); }
            }
          };
          const attachLockMove=()=>{
            if(lockMoveAttached) return;
            lockMoveAttached=true;
            window.addEventListener('pointermove',onLockMove,{capture:true,passive:false});
          };
          const detachLockMove=()=>{
            if(!lockMoveAttached) return;
            lockMoveAttached=false;
            try { window.removeEventListener('pointermove',onLockMove,true); } catch (_) {}
          };
          const clearHold=restore=>{
            if(holdTimer) { clearTimeout(holdTimer); holdTimer=0; }
            detachLockMove();
            if(restore && hold2x && holdVideo && !holdLocked) {
              try { holdVideo.playbackRate=Math.max(.25,Math.min(4,Number(holdBaseRate)||1)); } catch (_) {}
            }
            if(holdLocked && holdVideo) { lockedVideo=holdVideo; lockedBaseRate=holdBaseRate; }
            hold2x=false; holdLocked=false; holdPointer=-1; holdVideo=null;
            feedback2x(false);
          };
          const onHoldDown=event=>{
            if(!isShorts() || event.isPrimary===false) return;
            // Any deliberate interaction should immediately reveal controls if auto-hide is on.
            revealChrome(false);
            if(interactiveTarget(event.target)) return;
            const width=Math.max(1,innerWidth||document.documentElement?.clientWidth||1);
            const x=Number(event.clientX)||0;
            if(x>width*.34 && x<width*.66) return;
            clearHold(true);
            holdPointer=event.pointerId; holdStartX=x; holdStartY=Number(event.clientY)||0;
            holdTimer=setTimeout(()=>{
              holdTimer=0;
              if(holdPointer!==event.pointerId || !isShorts()) return;
              const videos=Array.from(document.querySelectorAll('video'));
              const current=resolveActive(videos);
              if(!current) return;
              if(lockedVideo===current) { feedback2x(true,'2\u00d7'); return; }
              holdVideo=current; holdBaseRate=Number(current.playbackRate)||1;
              try {
                current.playbackRate=2; hold2x=true; feedback2x(true,'2\u00d7'); attachLockMove();
              } catch (_) { clearHold(false); }
            },340);
          };
          const onHoldMove=event=>{
            if(event.pointerId!==holdPointer || hold2x) return;
            const dx=(Number(event.clientX)||0)-holdStartX,dy=(Number(event.clientY)||0)-holdStartY;
            // Before 2x activates, any real movement belongs entirely to YouTube's normal
            // vertical feed gesture. The non-passive lock listener is attached only later.
            if(dx*dx+dy*dy>18*18) clearHold(false);
          };
          const onHoldEnd=event=>{
            if(event.pointerId===holdPointer) clearHold(true);
            if(isShorts()) scheduleChromeHide();
          };
          window.addEventListener('pointerdown',onHoldDown,{capture:true,passive:true});
          window.addEventListener('pointermove',onHoldMove,{capture:true,passive:true});
          window.addEventListener('pointerup',onHoldEnd,{capture:true,passive:true});
          window.addEventListener('pointercancel',onHoldEnd,{capture:true,passive:true});

          let profile={
            previousPreload:'metadata',
            nextPreload:'auto',
            hardReleaseDistant:false,
            trimImages:false,
            retainedVideoPressure:10,
            autoHideChrome:false
          };

          const sanitizePreload=value=>value==='auto'||value==='metadata'||value==='none'?value:'metadata';
          const configure=next=>{
            if(!next||typeof next!=='object') return profile;
            autoHideChrome=next.autoHideChrome===true;
            profile={
              previousPreload:sanitizePreload(next.previousPreload),
              nextPreload:sanitizePreload(next.nextPreload),
              hardReleaseDistant:next.hardReleaseDistant===true,
              trimImages:next.trimImages===true,
              retainedVideoPressure:Math.max(4,Math.min(16,Number(next.retainedVideoPressure)||10)),
              autoHideChrome
            };
            if(autoHideChrome) scheduleChromeHide(); else revealChrome(false);
            return profile;
          };

          // Capturing play gives us the active recycler item without layout reads on the hot path.
          document.addEventListener('play',event=>{
            const video=event.target;
            if(isShorts() && video?.tagName==='VIDEO') {
              if(lockedVideo && lockedVideo!==video) releaseLockedSpeed();
              activeVideo=video; guardActive=true; scheduleChromeHide();
            }
          },true);
          document.addEventListener('pause',event=>{
            if(isShorts() && event.target===activeVideo) revealChrome(false);
          },true);
          document.addEventListener('visibilitychange',()=>{
            if(document.visibilityState==='hidden') { clearChromeHideTimer(); setChromeHidden(false); }
            else if(isShorts()) scheduleChromeHide();
          },true);

          const resolveActive=videos=>{
            if(activeVideo && activeVideo.isConnected===false) activeVideo=null;
            if(activeVideo && videos.includes(activeVideo)) return activeVideo;
            const playing=videos.find(video=>!video.paused && !video.ended && video.readyState>=1);
            if(playing) { activeVideo=playing; return playing; }

            // Geometry is only a fallback for an initially paused/buffering Short.
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

          const restoreDetachedSource=video=>{
            const source=video?.dataset?.votuibeDetachedSrc;
            if(!source) return;
            try {
              video.setAttribute('src',source);
              delete video.dataset.votuibeDetachedSrc;
              video.load();
            } catch (_) {}
          };

          const warm= (video, mode, pause=true)=>{
            if(!video) return;
            try {
              restoreDetachedSource(video);
              if(pause && !video.paused) video.pause();
              video.preload=sanitizePreload(mode);
            } catch (_) {}
          };

          const releaseDistant=(video,hard)=>{
            if(!video) return false;
            try {
              if(!video.paused) video.pause();
              video.preload='none';
              if(!hard) return true;

              // HTTP media can be detached and restored later. Blob/MediaSource URLs are owned
              // by YouTube; for those we only reset the media element so Chromium may discard
              // decoder buffers without invalidating YouTube's source bookkeeping.
              const attr=video.getAttribute('src')||'';
              const detachable=attr && !attr.startsWith('blob:') && !attr.startsWith('mediasource:') && !video.srcObject;
              if(detachable) {
                if(!video.dataset.votuibeDetachedSrc) video.dataset.votuibeDetachedSrc=attr;
                video.removeAttribute('src');
                video.load();
              } else if(video.readyState>1) {
                video.load();
              }
              return true;
            } catch (_) { return false; }
          };

          const trim=(aggressive=false,trimImages=profile.trimImages)=>{
            if (!isShorts()) {
              activeVideo=null; resumeAfterSuspend=false; guardActive=false;
              return {videos:0,trimmed:0,aggressive:false};
            }
            guardActive=true;
            if (document.visibilityState==='hidden') return {videos:0,trimmed:0,aggressive:false};
            const videos=Array.from(document.querySelectorAll('video'));
            if(!videos.length) return {videos:0,trimmed:0,aggressive:false};

            const current=resolveActive(videos);
            const activeIndex=current ? videos.indexOf(current) : -1;
            const hard=aggressive || profile.hardReleaseDistant || videos.length>=profile.retainedVideoPressure;
            let trimmed=0;

            for(let index=0; index<videos.length; index++) {
              const video=videos[index];
              if(index===activeIndex || video===current) {
                warm(video,'auto',false);
                continue;
              }
              if(activeIndex>=0 && Math.abs(index-activeIndex)===1) {
                // Exactly previous/current/next form the warm window. Only the next Short is
                // allowed full preload on healthy unmetered devices.
                warm(video,index>activeIndex?profile.nextPreload:profile.previousPreload,true);
                continue;
              }
              if(releaseDistant(video,hard)) trimmed++;
            }

            if(hard && trimImages) {
              const images=document.querySelectorAll('img');
              const start=Math.max(0,images.length-120);
              for(let i=start;i<images.length;i++) {
                const image=images[i];
                try {
                  const r=image.getBoundingClientRect();
                  if(r.bottom < -innerHeight*2 || r.top > innerHeight*3) {
                    image.loading='lazy';
                    image.decoding='async';
                  }
                } catch (_) {}
              }
            }
            return {videos:videos.length,trimmed,aggressive:hard,activeIndex};
          };

          const scheduleTransitionTrim=()=>{
            releaseLockedSpeed();
            clearHold(false);
            revealChrome(false);
            if(transitionFrame) return;
            const run=()=>{
              transitionFrame=0;
              if(!isShorts() || document.visibilityState==='hidden') return;
              trim(false,false);
              // transition() deliberately reveals the overlay so the viewer sees the new Short's
              // metadata. Re-arm the one-shot timer afterwards; otherwise a late route callback
              // can cancel the timer previously scheduled by the new video's play event.
              scheduleChromeHide();
            };
            try {
              transitionFrame=requestAnimationFrame(()=>{ transitionFrame=requestAnimationFrame(run); });
            } catch (_) {
              transitionFrame=setTimeout(run,32);
            }
          };

          const scheduleHardTrim=(trimImages=false)=>{
            cancelIdle(deferredHardTrim);
            const run=()=>{
              deferredHardTrim=0;
              if(!isShorts() || document.visibilityState==='hidden') return;
              trim(true,trimImages);
            };
            try {
              deferredHardTrim=typeof requestIdleCallback==='function'
                ? requestIdleCallback(run,{timeout:900})
                : setTimeout(run,220);
            } catch (_) { deferredHardTrim=setTimeout(run,220); }
          };

          const suspend=()=>{
            clearHold(true);
            releaseLockedSpeed();
            revealChrome(false);
            if(!isShorts()) { activeVideo=null; resumeAfterSuspend=false; return {videos:0,trimmed:0}; }
            const videos=Array.from(document.querySelectorAll('video'));
            const current=resolveActive(videos);
            resumeAfterSuspend=!!(current && !current.paused && !current.ended);
            for(const video of videos) { try { if(!video.paused) video.pause(); } catch (_) {} }
            return trim(true,false);
          };

          const resume=()=>{
            if(!isShorts() || !resumeAfterSuspend || document.visibilityState==='hidden') {
              resumeAfterSuspend=false;
              return false;
            }
            resumeAfterSuspend=false;
            const videos=Array.from(document.querySelectorAll('video'));
            const current=resolveActive(videos);
            if(!current) return false;
            try {
              warm(current,'auto',false);
              const promise=current.play();
              if(promise && typeof promise.catch==='function') promise.catch(()=>{});
              return true;
            } catch (_) { return false; }
          };

          const release=()=>{
            clearHold(true);
            releaseLockedSpeed();
            revealChrome(false);
            if(transitionFrame) {
              try { cancelAnimationFrame(transitionFrame); } catch (_) {}
              try { clearTimeout(transitionFrame); } catch (_) {}
              transitionFrame=0;
            }
            cancelIdle(deferredHardTrim);
            deferredHardTrim=0;
            // Leaving Shorts: there is no reason to retain decoders from the old recycler.
            if(guardActive) {
              const videos=Array.from(document.querySelectorAll('video'));
              for(const video of videos) releaseDistant(video,true);
            }
            activeVideo=null;
            resumeAfterSuspend=false;
            guardActive=false;
            return true;
          };

          // Native route handling invokes transition() when the active Short id changes.
          // No MutationObserver, setInterval, or navigation polling is needed.
          window.__votuibeConfigureShortsGuard=configure;
          window.__votuibeTrimShorts=trim;
          window.__votuibeShortsTransition=scheduleTransitionTrim;
          window.__votuibeHardTrimShorts=scheduleHardTrim;
          window.__votuibeSuspendShorts=suspend;
          window.__votuibeResumeShorts=resume;
          window.__votuibeReleaseShorts=release;
        })();
    """.trimIndent()

    fun configure(policy: ShortsRuntimePolicy, autoHideChrome: Boolean = false): String = buildString {
        append("window.__votuibeConfigureShortsGuard?.({")
        append("previousPreload:'").append(policy.previousPreload.webValue).append("',")
        append("nextPreload:'").append(policy.nextPreload.webValue).append("',")
        append("hardReleaseDistant:").append(policy.hardReleaseDistant).append(',')
        append("trimImages:").append(policy.trimImages).append(',')
        append("retainedVideoPressure:").append(policy.retainedVideoPressure).append(',')
        append("autoHideChrome:").append(autoHideChrome)
        append("});")
    }

    fun transition(): String = "window.__votuibeShortsTransition?.();"

    fun hardTrimDeferred(trimImages: Boolean = false): String =
        "window.__votuibeHardTrimShorts?.(${if (trimImages) "true" else "false"});"

    fun trim(aggressive: Boolean, trimImages: Boolean = aggressive): String =
        "window.__votuibeTrimShorts?.(${if (aggressive) "true" else "false"},${if (trimImages) "true" else "false"});"

    fun suspend(): String = "window.__votuibeSuspendShorts?.();"

    fun resume(): String = "window.__votuibeResumeShorts?.();"

    fun release(): String = "window.__votuibeReleaseShorts?.();"
}
