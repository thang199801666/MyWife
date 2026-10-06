
        (() => {
          try {
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
              button.setAttribute('aria-label','Download');
              button.style.cssText = 'display:inline-flex;gap:6px;align-items:center;justify-content:center;min-height:40px;padding:0 12px;border:0;border-radius:24px;background:#272727;color:#fff;font:500 14px Roboto,Arial,sans-serif;cursor:pointer';
              const icon = document.createElementNS('http://www.w3.org/2000/svg','svg');
              for (const [name,value] of Object.entries({viewBox:'0 0 24 24',width:'24',height:'24',fill:'currentColor','aria-hidden':'true'})) icon.setAttribute(name,value);
              const path = document.createElementNS('http://www.w3.org/2000/svg','path');
              path.setAttribute('d','M11 3h2v10.17l3.59-3.58L18 11l-6 6-6-6 1.41-1.41L11 13.17V3ZM5 19h14v2H5z');
              icon.appendChild(path);
              const label = document.createElement('span'); label.textContent = 'Download';
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
              .ytp-unmute, .ytp-muted-autoplay-overlay, .ytp-mute-button, .ytp-volume-panel,
              .ytp-volume-area { display: none !important; }
              ytm-mobile-topbar-renderer,
              ytm-pivot-bar-renderer,
              ytm-app-promo-renderer,
              ytm-mealbar-promo-renderer,
              ytm-survey-trigger-renderer {
                display: none !important;
              }
              html, body, ytm-app { background: #000 !important; }
              ytm-app { padding-top: 0 !important; }
            `;
          } catch (_) {}
        })();
    
