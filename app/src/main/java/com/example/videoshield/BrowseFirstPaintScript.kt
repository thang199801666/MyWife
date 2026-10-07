package com.example.videoshield

/**
 * Tiny document-start helper that gives the first browse viewport a deterministic request budget.
 *
 * The full feed virtualization policy is injected later. This early layer deliberately does only
 * one bounded scan per document/SPA navigation: up to two near-viewport thumbnails are promoted,
 * while farther feed images are marked lazy/low-priority before they can compete with first paint.
 * It uses no polling loop, MutationObserver or timer cadence.
 */
object BrowseFirstPaintScript {
    fun install(): String = """
        (() => {
          if (window.__votuibeFirstPaintPriorityInstalled) return;
          window.__votuibeFirstPaintPriorityInstalled = true;
          let frame = 0;
          const stats = { runs: 0, promoted: 0, deferred: 0 };
          const cardSelector = [
            'ytm-rich-item-renderer',
            'ytm-video-with-context-renderer',
            'ytm-compact-video-renderer',
            'ytm-playlist-renderer',
            'ytm-shorts-lockup-view-model'
          ].join(',');
          const tune = () => {
            frame = 0;
            if (document.visibilityState === 'hidden') return;
            const cards = Array.from(document.querySelectorAll(cardSelector)).slice(0, 36);
            const viewport = Math.max(1, window.innerHeight || 720);
            let promoted = 0;
            for (const card of cards) {
              if (!card || !card.isConnected) continue;
              let near = false;
              try {
                const rect = card.getBoundingClientRect();
                near = rect.bottom >= -64 && rect.top <= viewport * 1.15;
              } catch (_) {}
              const images = Array.from(card.querySelectorAll?.('img') || []).slice(0, 3);
              for (const image of images) {
                try {
                  image.decoding = 'async';
                  if (near && promoted < 2) {
                    image.loading = 'eager';
                    image.fetchPriority = 'high';
                    promoted++;
                    stats.promoted++;
                  } else {
                    image.loading = 'lazy';
                    image.fetchPriority = near ? 'auto' : 'low';
                    if (!near) stats.deferred++;
                  }
                } catch (_) {}
              }
            }
            stats.runs++;
          };
          const schedule = () => {
            if (frame || document.visibilityState === 'hidden') return;
            try { frame = requestAnimationFrame(tune); }
            catch (_) { tune(); }
          };
          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', schedule, { once: true });
          } else {
            schedule();
          }
          document.addEventListener('yt-navigate-finish', schedule, true);
          window.addEventListener('pageshow', schedule, { passive: true });
          window.__votuibeFirstPaintDiagnostics = () => ({ ...stats, pending: !!frame });
        })();
    """.trimIndent()
}
