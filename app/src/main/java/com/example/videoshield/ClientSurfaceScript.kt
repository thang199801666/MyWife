package com.example.videoshield

/**
 * Small presentation-only layer that removes duplicate website chrome when YouTube is hosted
 * inside YouTooBee's native browse/player shell. It intentionally avoids media URL extraction
 * and player transport internals; playback commands remain behind PlaybackBackend.
 */
object ClientSurfaceScript {
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
            }));
            return;
          }
          window.__videoShieldExpandPlaybackWanted=false;
          window.__videoShieldExpandPlaybackUntil=0;
          if (!style) { style=document.createElement('style'); style.id=id; (document.head||document.documentElement).appendChild(style); }
          style.textContent=`
            html,body { overflow:hidden !important; background:#000 !important; }
            video { position:fixed !important; top:0 !important; left:0 !important;
              width:100vw !important; height:calc(100vh - 56px) !important;
              object-fit:contain !important; background:#000 !important; z-index:2147483646 !important; }
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

    fun player(downloadLabel: String = "Download", lightTheme: Boolean = false): String {
        val pageBackground = if (lightTheme) "#ffffff" else "#000000"
        val actionBackground = if (lightTheme) "#f1f3f6" else "#272727"
        val actionColor = if (lightTheme) "#111318" else "#ffffff"
        val colorScheme = if (lightTheme) "light" else "dark"
        return """
        (() => {
          try {
            if (!document.documentElement.hasAttribute('data-votuibe-surface'))
              document.documentElement.setAttribute('data-votuibe-surface','expanded');
            window.__voTuibeInstallDownload = () => {
              if (typeof window.VideoShieldBridge?.requestDownload !== 'function') return;
              const share = Array.from(document.querySelectorAll('button,[role="button"]')).find(n =>
                /^(share|chia sẻ)$/i.test((n.getAttribute('aria-label') || n.textContent || '').trim()));
              if (!share) return;
              const anchor = share.closest('button-view-model,ytm-button-renderer') || share;
              let wrapper = document.getElementById('votuibe-download-action');
              if (wrapper && wrapper.previousElementSibling === anchor) return;
              if (wrapper) wrapper.remove();
              wrapper = document.createElement('span');
              wrapper.id = 'votuibe-download-action';
              wrapper.style.cssText = 'display:inline-flex;align-items:center;flex-shrink:0;margin:0 4px';
              const button = document.createElement('button');
              button.type = 'button';
              button.setAttribute('aria-label',${org.json.JSONObject.quote(downloadLabel)});
              button.style.cssText = 'display:inline-flex;gap:6px;align-items:center;justify-content:center;min-height:40px;padding:0 12px;border:0;border-radius:24px;background:${actionBackground};color:${actionColor};font:500 14px Roboto,Arial,sans-serif;cursor:pointer';
              const icon = document.createElementNS('http://www.w3.org/2000/svg','svg');
              for (const [name,value] of Object.entries({viewBox:'0 0 24 24',width:'24',height:'24',fill:'currentColor','aria-hidden':'true'})) icon.setAttribute(name,value);
              const path = document.createElementNS('http://www.w3.org/2000/svg','path');
              path.setAttribute('d','M11 3h2v10.17l3.59-3.58L18 11l-6 6-6-6 1.41-1.41L11 13.17V3ZM5 19h14v2H5z');
              icon.appendChild(path);
              const label = document.createElement('span'); label.textContent = ${org.json.JSONObject.quote(downloadLabel)};
              button.append(icon,label);
              button.addEventListener('click', event => {
                event.preventDefault(); event.stopPropagation();
                window.VideoShieldBridge.requestDownload();
              });
              wrapper.appendChild(button);
              anchor.after(wrapper);
            };
            window.__voTuibeInstallDownload();
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
              html[data-votuibe-surface="expanded"] #player-container-id.sticky-player {
                top:0 !important;
              }
              html[data-votuibe-surface="expanded"] .html5-video-player { width:100% !important; }
              html[data-votuibe-surface="expanded"] .html5-video-container {
                width:100% !important; height:100% !important; max-width:none !important;
              }
              html[data-votuibe-surface="expanded"] .html5-video-player video {
                position:absolute !important; top:0 !important; left:0 !important;
                width:100% !important; height:100% !important;
                max-width:none !important; max-height:none !important; transform:none !important;
                object-fit:contain !important;
              }
              .ytp-unmute, .ytp-muted-autoplay-overlay, .ytp-mute-button, .ytp-volume-panel,
              .ytp-volume-area { display: none !important; }
              ytm-mobile-topbar-renderer,
              ytm-pivot-bar-renderer,
              ytm-app-promo-renderer,
              ytm-mealbar-promo-renderer,
              ytm-survey-trigger-renderer {
                display: none !important;
              }
              html { color-scheme: ${colorScheme}; }
              html, body, ytm-app { background: ${pageBackground} !important; }
              ytm-app { padding-top: 0 !important; }
            `;
          } catch (_) {}
        })();
    """.trimIndent()
    }

    fun browse(lightTheme: Boolean = false): String {
        val pageBackground = if (lightTheme) "#ffffff" else "#000000"
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
              html, body, ytm-app { background: ${pageBackground} !important; }
              ytm-app { padding-top: 0 !important; padding-bottom: 0 !important; }
            `;
          } catch (_) {}
        })();
    """.trimIndent()
    }
}
