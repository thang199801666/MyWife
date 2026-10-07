package com.example.videoshield

/**
 * Small presentation-only layer that removes duplicate website chrome when YouTube is hosted
 * inside YouTooBee's native browse/player shell. It intentionally avoids media URL extraction
 * and player transport internals; playback commands remain behind PlaybackBackend.
 */
object ClientSurfaceScript {
    private const val BOTTOM_NAV_OVERLAY_INSET_PX = 64

    fun mini(enabled: Boolean, resumePlaying: Boolean = false): String = """
        (() => {
          const id='youtoobee-mini-style';
          const wasPlaying=!!window.__videoShieldMiniPlaybackWanted;
          window.__videoShieldMiniPlaybackWanted=$enabled && $resumePlaying;
          document.documentElement.setAttribute('data-votuibe-surface',
            document.getElementById('youtoobee-pip-style') ? 'pip' : $enabled ? 'mini' : 'expanded');
          let style=document.getElementById(id);
          if (!$enabled) {
            style?.remove();
            window.__videoShieldExpandPlaybackWanted=wasPlaying || $resumePlaying;
            const resumeUntil=Date.now()+1200;
            window.__videoShieldExpandPlaybackUntil=resumeUntil;
            // requestAnimationFrame may be suspended while the renderer is hidden.
            // Expire independently so a delayed resize cannot lock website Pause.
            setTimeout(()=>{
              if(window.__videoShieldExpandPlaybackUntil===resumeUntil)
                window.__videoShieldExpandPlaybackWanted=false;
            },1200);
            requestAnimationFrame(() => requestAnimationFrame(() => {
              window.dispatchEvent(new Event('resize'));
              const video=document.querySelector('.html5-video-player video.html5-main-video') || document.querySelector('video.html5-main-video') || document.querySelector('video');
              if(window.__videoShieldExpandPlaybackWanted && Date.now()<resumeUntil && video && !video.ended &&
                  document.documentElement.getAttribute('data-votuibe-surface')==='expanded') {
                const player=document.querySelector('.html5-video-player');
                if(typeof player?.playVideo==='function') player.playVideo();
                else if(video.paused) video.play().catch(()=>{});
              }
              window.__videoShieldSyncPlayerState?.();
              // YouTube can publish its resize Pause after the first expanded
              // frame. Reconcile a few bounded checkpoints; the helper never
              // resumes paused media or touches a later mini/PiP surface.
              for(const delay of [250,600,1200]) setTimeout(()=>{
                if(document.documentElement.getAttribute('data-votuibe-surface')==='expanded')
                  window.__videoShieldSyncPlayerState?.();
              },delay);
            }));
            return;
          }
          window.__videoShieldExpandPlaybackWanted=false;
          window.__videoShieldExpandPlaybackUntil=0;
          if (!style) { style=document.createElement('style'); style.id=id; (document.head||document.documentElement).appendChild(style); }
          style.textContent=`
            html,body { overflow:hidden !important; background:#000 !important; }
            /* Reset the whole composited player, not just video. YouTube keeps
               offsets/transforms on its containers after the watch viewport shrinks. */
            #player, #player-container-id, .html5-video-player, .html5-video-container, video {
              position:fixed !important; top:0 !important; left:0 !important;
              width:100vw !important; height:100vh !important;
              min-width:0 !important; max-width:none !important; max-height:none !important;
              margin:0 !important; padding:0 !important; transform:none !important;
              object-fit:contain !important; background:#000 !important; z-index:2147483646 !important;
            }
            #player, #player-container-id { overflow:visible !important; }
            .ytp-chrome-top,.ytp-chrome-bottom,.ytp-gradient-top,.ytp-gradient-bottom,
            .ytp-pause-overlay,.ytp-endscreen-content { display:none !important; }
            button,[role="button"] { visibility:hidden !important; }
          `;
        })();
    """.trimIndent()

    fun pip(enabled: Boolean, resumePlaying: Boolean = false): String = """
        (() => {
          const id = 'youtoobee-pip-style';
          let style = document.getElementById(id);
          window.__videoShieldPipResumePending = $enabled && $resumePlaying;
          window.__videoShieldPipPlaybackWanted = $enabled && $resumePlaying;
          document.documentElement.setAttribute('data-votuibe-surface', $enabled ? 'pip' :
            document.getElementById('youtoobee-mini-style') ? 'mini' : 'expanded');
          if (!$enabled) { if (style) style.remove(); return; }
          if (!style) {
            style = document.createElement('style');
            style.id = id;
            (document.head || document.documentElement).appendChild(style);
          }
          style.textContent = `
            html, body { overflow: hidden !important; margin: 0 !important; padding: 0 !important; }
            #player, #player-container-id, .html5-video-player, .html5-video-container, video {
              position: fixed !important; top: 0 !important; left: 0 !important;
              width: 100vw !important; height: 100vh !important;
              min-width: 0 !important; max-width: none !important; max-height: none !important;
              margin: 0 !important; padding: 0 !important; transform: none !important;
              object-fit: contain !important; background: #000 !important;
              z-index: 2147483647 !important;
            }
            #player, #player-container-id { overflow: visible !important; }
            .ytp-chrome-top, .ytp-chrome-bottom, .ytp-gradient-top, .ytp-gradient-bottom,
            .ytp-pause-overlay, .ytp-endscreen-content { display: none !important; }
          `;
          // The mobile player can pause during the final viewport resize of the PiP animation.
          setTimeout(() => {
            if (!window.__videoShieldPipResumePending || !document.getElementById(id)) return;
            window.__videoShieldPipResumePending = false;
            const video = document.querySelector('.html5-video-player video.html5-main-video') || document.querySelector('video.html5-main-video') || document.querySelector('video');
            if (video && video.paused && !video.ended) video.play().catch(error => {
              window.__videoShieldPipResumeError = String(error && error.name || 'play failed');
            });
          }, 1200);
        })();
    """.trimIndent()

    fun player(
        downloadLabel: String = "Download",
        optionsLabel: String = "More",
        lightTheme: Boolean = false
    ): String {
        val pageBackground = if (lightTheme) "#ffffff" else "#0f0f0f"
        val actionBackground = if (lightTheme) "#f2f2f2" else "#272727"
        val actionPressed = if (lightTheme) "#e5e5e5" else "#3f3f3f"
        val actionColor = if (lightTheme) "#0f0f0f" else "#ffffff"
        val cardBackground = if (lightTheme) "#f2f2f2" else "#272727"
        val secondaryText = if (lightTheme) "#606060" else "#aaaaaa"
        val colorScheme = if (lightTheme) "light" else "dark"
        return """
        (() => {
          try {
            if (!document.documentElement.hasAttribute('data-votuibe-surface'))
              document.documentElement.setAttribute('data-votuibe-surface','expanded');

            const makeActionButton = (label, pathData, onClick, iconOnly = false) => {
              const button = document.createElement('button');
              button.type = 'button';
              button.className = 'votuibe-watch-action-button' + (iconOnly ? ' is-icon-only' : '');
              button.setAttribute('aria-label', label);
              button.title = label;
              const icon = document.createElementNS('http://www.w3.org/2000/svg','svg');
              for (const [name,value] of Object.entries({viewBox:'0 0 24 24',width:'24',height:'24',fill:'currentColor','aria-hidden':'true'})) icon.setAttribute(name,value);
              const path = document.createElementNS('http://www.w3.org/2000/svg','path');
              path.setAttribute('d',pathData);
              icon.appendChild(path);
              button.appendChild(icon);
              if (!iconOnly) {
                const text = document.createElement('span'); text.textContent = label;
                button.appendChild(text);
              }
              button.addEventListener('click', event => {
                event.preventDefault(); event.stopPropagation(); onClick();
              });
              return button;
            };

            const nativeActionUsesIconOnly = (button) => {
              try {
                const rect = button.getBoundingClientRect();
                if (rect.width > 0 && rect.width <= 54) return true;
                return !Array.from(button.querySelectorAll('span')).some(node => {
                  if (!(node.textContent || '').trim()) return false;
                  const style = getComputedStyle(node);
                  const r = node.getBoundingClientRect();
                  return style.display !== 'none' && style.visibility !== 'hidden' && r.width > 1 && r.height > 1;
                });
              } catch (_) { return false; }
            };

            window.__voTuibeInstallWatchActions = () => {
              const bridge = window.VideoShieldBridge;
              if (!bridge) return;
              const routeKey=location.pathname+location.search;
              let wrapper=document.getElementById('votuibe-watch-actions');
              // This installer is called by the bounded shield sweep. The fast path is O(1)
              // and avoids rescanning YouTube's action buttons during steady-state playback.
              if (wrapper && wrapper.dataset.routeKey===routeKey && wrapper.isConnected) return;
              if (wrapper) wrapper.remove();

              const share = Array.from(document.querySelectorAll('button,[role="button"]')).find(n => {
                const label=(n.getAttribute('aria-label') || n.textContent || '').trim();
                return /^(share|chia sẻ)$/i.test(label) || /share|chia sẻ/i.test(n.getAttribute('aria-label') || '');
              });
              if (!share) return;
              const anchor = share.closest('button-view-model,ytm-button-renderer') || share;
              const host = anchor.parentElement;
              if (!host) return;
              // YouTube is experimenting with icon-only watch actions. Match whichever
              // presentation the current document actually uses instead of forcing one mode.
              const iconOnly = nativeActionUsesIconOnly(share);

              wrapper=document.createElement('span');
              wrapper.id='votuibe-watch-actions';
              wrapper.dataset.routeKey=routeKey;
              wrapper.dataset.iconOnly=iconOnly ? '1' : '0';
              wrapper.className='votuibe-watch-actions';
              if (typeof bridge.requestDownload === 'function') {
                wrapper.appendChild(makeActionButton(
                  ${org.json.JSONObject.quote(downloadLabel)},
                  'M11 3h2v10.17l3.59-3.58L18 11l-6 6-6-6 1.41-1.41L11 13.17V3ZM5 19h14v2H5z',
                  () => bridge.requestDownload(),
                  iconOnly
                ));
              }
              if (typeof bridge.requestPlayerOptions === 'function') {
                wrapper.appendChild(makeActionButton(
                  ${org.json.JSONObject.quote(optionsLabel)},
                  'M12 8a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm0 2a2 2 0 1 0 0 4 2 2 0 0 0 0-4Zm0 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4Z',
                  () => bridge.requestPlayerOptions(),
                  iconOnly
                ));
              }
              anchor.after(wrapper);
            };
            // Compatibility alias for older sweep code and mixed cached pages.
            window.__voTuibeInstallDownload = window.__voTuibeInstallWatchActions;
            window.__voTuibeInstallWatchActions();

            const id = 'youtoobee-player-surface-style';
            let style = document.getElementById(id);
            if (!style) {
              style = document.createElement('style');
              style.id = id;
              (document.head || document.documentElement).appendChild(style);
            }
            style.textContent = `
              /* The native shell replaces the mobile topbar. Its sticky player must also
                 lose the topbar offset, otherwise it covers the title below the placeholder. */
              html[data-votuibe-surface="expanded"] #player-container-id.sticky-player { top:0 !important; }
              html[data-votuibe-surface="expanded"] .html5-video-player { width:100% !important; background:#000 !important; }
              html[data-votuibe-surface="expanded"] .html5-video-container {
                width:100% !important; height:100% !important; max-width:none !important; background:#000 !important;
              }
              html[data-votuibe-surface="expanded"] .html5-video-player video {
                position:absolute !important; top:0 !important; left:0 !important;
                width:100% !important; height:100% !important;
                max-width:none !important; max-height:none !important; transform:none !important;
                object-fit:contain !important; background:#000 !important;
              }
              .ytp-unmute, .ytp-muted-autoplay-overlay, .ytp-mute-button, .ytp-volume-panel,
              .ytp-volume-area { display: none !important; }
              ytm-mobile-topbar-renderer,
              ytm-pivot-bar-renderer,
              ytm-app-promo-renderer,
              ytm-mealbar-promo-renderer,
              ytm-survey-trigger-renderer { display: none !important; }

              html { color-scheme: ${colorScheme}; }
              html, body, ytm-app { background:${pageBackground} !important; }
              body { margin:0 !important; -webkit-tap-highlight-color:transparent !important; }
              ytm-app {
                padding-top:0 !important;
                padding-bottom:${BOTTOM_NAV_OVERLAY_INSET_PX}px !important;
                scroll-padding-bottom:${BOTTOM_NAV_OVERLAY_INSET_PX}px !important;
              }

              /* Watch metadata hierarchy: compact title, owner row and pill actions. The
                 selectors are additive so a YouTube markup refresh simply falls back to its
                 native layout instead of hiding content. */
              ytm-slim-video-metadata-section-renderer,
              ytm-watch-metadata,
              ytm-video-description-header-renderer {
                box-sizing:border-box !important;
              }
              ytm-slim-video-metadata-section-renderer h1,
              ytm-watch-metadata h1,
              .slim-video-metadata-title {
                font-size:18px !important;
                line-height:24px !important;
                font-weight:600 !important;
                letter-spacing:0 !important;
              }
              ytm-slim-owner-renderer {
                margin:4px 12px 8px !important;
                min-height:52px !important;
              }
              ytm-slim-owner-renderer button,
              ytm-subscribe-button-renderer button,
              subscribe-button-view-model button {
                min-height:36px !important;
                border-radius:18px !important;
                padding-inline:14px !important;
                font-weight:600 !important;
              }
              ytm-slim-video-action-bar-renderer,
              ytm-video-action-bar-renderer,
              .slim-video-action-bar-actions {
                overflow-x:auto !important;
                overscroll-behavior-inline:contain !important;
                scrollbar-width:none !important;
                -webkit-overflow-scrolling:touch !important;
              }
              ytm-slim-video-action-bar-renderer::-webkit-scrollbar,
              ytm-video-action-bar-renderer::-webkit-scrollbar,
              .slim-video-action-bar-actions::-webkit-scrollbar { display:none !important; }
              ytm-slim-video-action-bar-renderer button,
              ytm-video-action-bar-renderer button,
              button-view-model button {
                min-height:36px !important;
                border-radius:18px !important;
              }
              .votuibe-watch-actions {
                display:inline-flex !important;
                align-items:center !important;
                gap:8px !important;
                flex:0 0 auto !important;
                margin-inline:4px !important;
                vertical-align:middle !important;
              }
              .votuibe-watch-action-button {
                display:inline-flex !important;
                gap:7px !important;
                align-items:center !important;
                justify-content:center !important;
                min-width:max-content !important;
                min-height:36px !important;
                padding:0 13px !important;
                border:0 !important;
                border-radius:18px !important;
                background:${actionBackground} !important;
                color:${actionColor} !important;
                font:500 14px/20px Roboto,Arial,sans-serif !important;
                white-space:nowrap !important;
                box-shadow:none !important;
                transform:scale(1) !important;
                transition:transform 90ms ease-out,background-color 90ms linear !important;
                -webkit-tap-highlight-color:transparent !important;
              }
              .votuibe-watch-action-button:active {
                background:${actionPressed} !important;
                transform:scale(.95) !important;
              }
              .votuibe-watch-action-button.is-icon-only {
                width:40px !important;
                min-width:40px !important;
                height:40px !important;
                min-height:40px !important;
                padding:0 !important;
                border-radius:20px !important;
                gap:0 !important;
              }
              .votuibe-watch-action-button svg { flex:0 0 24px !important; width:24px !important; height:24px !important; }

              /* YouTube's current watch surface treats description/comments as compact cards.
                 Keep these rules tolerant: unknown renderers are never hidden. */
              ytm-expandable-video-description-body-renderer,
              ytm-comments-entry-point-header-renderer,
              ytm-comment-section-renderer {
                margin-inline:12px !important;
                border-radius:12px !important;
                background:${cardBackground} !important;
                overflow:hidden !important;
              }
              ytm-expandable-video-description-body-renderer,
              ytm-comments-entry-point-header-renderer { padding:10px 12px !important; }
              ytm-slim-video-metadata-section-renderer .metadata-info,
              ytm-video-description-header-renderer .secondary-text { color:${secondaryText} !important; }

              /* Long recommendation lists are the expensive part of a watch session. Let
                 Chromium skip layout/paint work for cards far outside the viewport. */
              ytm-item-section-renderer ytm-video-with-context-renderer,
              ytm-item-section-renderer ytm-compact-video-renderer,
              ytm-video-with-context-renderer.compact-media-item {
                content-visibility:auto !important;
                contain-intrinsic-size:300px !important;
                contain:layout paint style !important;
              }
              ytm-video-with-context-renderer ytm-thumbnail-cover,
              ytm-compact-video-renderer ytm-thumbnail-cover,
              .media-item-thumbnail-container {
                border-radius:12px !important;
                overflow:hidden !important;
              }
            `;
          } catch (_) {}
        })();
    """.trimIndent()
    }

    fun browse(lightTheme: Boolean = false): String {
        val pageBackground = if (lightTheme) "#ffffff" else "#0f0f0f"
        val colorScheme = if (lightTheme) "light" else "dark"
        return """
        (() => {
          try {
            const id = 'youtoobee-browse-surface-style';
            let style = document.getElementById(id);
            if (!style) {
              style = document.createElement('style');
              style.id = id;
              (document.head || document.documentElement).appendChild(style);
            }
            style.textContent = `
              ytm-mobile-topbar-renderer,
              ytm-pivot-bar-renderer,
              ytm-app-promo-renderer,
              ytm-mealbar-promo-renderer,
              ytm-survey-trigger-renderer { display: none !important; }
              html { color-scheme: ${colorScheme}; }
              html, body, ytm-app {
                background: ${pageBackground} !important;
                font-family: Roboto, Arial, sans-serif !important;
              }
              body { margin: 0 !important; -webkit-tap-highlight-color: transparent !important; }
              ytm-app {
                padding-top: 0 !important;
                padding-bottom: ${BOTTOM_NAV_OVERLAY_INSET_PX}px !important;
                scroll-padding-bottom: ${BOTTOM_NAV_OVERLAY_INSET_PX}px !important;
              }

              /* Native app chrome owns top/bottom navigation. Match the denser, rounded
                 mobile visual language without replacing YouTube's own feed structure. */
              ytm-feed-filter-chip-bar-renderer,
              ytm-chip-cloud-renderer {
                background: ${pageBackground} !important;
                border: 0 !important;
              }
              ytm-chip-cloud-chip-renderer,
              ytm-filter-chip-bar-renderer button,
              ytm-chip-cloud-renderer button {
                min-height: 32px !important;
                border-radius: 10px !important;
                padding-inline: 12px !important;
                font-weight: 500 !important;
              }
              ytm-rich-item-renderer,
              ytm-video-with-context-renderer,
              ytm-compact-video-renderer {
                content-visibility: auto !important;
                contain-intrinsic-size: 330px !important;
                contain: layout paint style !important;
              }
              ytm-video-with-context-renderer ytm-thumbnail-cover,
              ytm-rich-item-renderer ytm-thumbnail-cover,
              ytm-compact-video-renderer ytm-thumbnail-cover,
              ytm-video-with-context-renderer .media-item-thumbnail-container,
              ytm-rich-item-renderer .media-item-thumbnail-container {
                border-radius: 12px !important;
                overflow: hidden !important;
              }
              /* Duration/progress overlays already belong to YouTube; normalize only their
                 geometry so native and local Home cards read as one visual system. */
              ytm-thumbnail-overlay-time-status-renderer,
              .badge-shape-wiz--thumbnail-default,
              .ytm-thumbnail-overlay-time-status-renderer {
                border-radius: 4px !important;
                overflow: hidden !important;
              }
              ytm-thumbnail-overlay-resume-playback-renderer,
              .ytm-thumbnail-overlay-resume-playback-renderer {
                height: 3px !important;
              }
              /* Keep the native Shorts shelf horizontal and cheap to paint. Each lockup can
                 be skipped by Chromium while it is outside the shelf viewport. */
              ytm-rich-section-renderer ytm-shorts-lockup-view-model,
              ytm-reel-shelf-renderer ytm-reel-item-renderer,
              ytm-shorts-lockup-view-model {
                content-visibility: auto !important;
                contain-intrinsic-size: 220px 390px !important;
                contain: layout paint style !important;
              }
              ytm-rich-section-renderer .horizontal-list,
              ytm-reel-shelf-renderer .reel-shelf-items,
              ytm-shorts-lockup-view-model {
                scrollbar-width: none !important;
              }
              ytm-rich-section-renderer .horizontal-list::-webkit-scrollbar,
              ytm-reel-shelf-renderer .reel-shelf-items::-webkit-scrollbar { display:none !important; }
              ytm-shorts-lockup-view-model,
              ytm-reel-item-renderer img {
                border-radius: 12px !important;
                overflow: hidden !important;
              }
              ytm-video-with-context-renderer h3,
              ytm-rich-item-renderer h3,
              .compact-media-item-headline {
                font-size: 16px !important;
                line-height: 21px !important;
                font-weight: 500 !important;
              }
              /* Subscriptions remains a first-class feed. Keep channel rows cheap to paint and
                 make avatar/card geometry match the native Home feed without assuming one exact
                 server-side experiment markup. Unknown selectors simply no-op. */
              ytm-subscription-list-item-renderer,
              ytm-channel-list-sub-menu-avatar-renderer,
              ytm-subscription-notification-toggle-button-renderer {
                content-visibility:auto !important;
                contain:layout paint style !important;
              }
              ytm-subscription-list-item-renderer img,
              ytm-channel-list-sub-menu-avatar-renderer img,
              ytm-subscription-list-item-renderer yt-img-shadow {
                border-radius:50% !important;
                overflow:hidden !important;
              }
              ytm-subscription-list-item-renderer button,
              ytm-subscription-notification-toggle-button-renderer button {
                min-height:36px !important;
                border-radius:18px !important;
              }
              ytm-rich-grid-renderer,
              ytm-section-list-renderer {
                overscroll-behavior-y:contain !important;
              }
              /* Keep tap targets comfortable while avoiding the oversized legacy button look. */
              ytm-button-renderer button,
              button-view-model button {
                border-radius: 20px !important;
              }
            `;
          } catch (_) {}
        })();
    """.trimIndent()
    }
}
