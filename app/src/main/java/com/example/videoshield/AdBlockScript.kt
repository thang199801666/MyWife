package com.example.videoshield

import org.json.JSONObject

object AdBlockScript {
    private data class ScriptKey(
        val enabled: Boolean,
        val safeMode: Boolean,
        val bypassAds: Boolean,
        val shorts: Boolean,
        val recommendations: Boolean,
        val comments: Boolean,
        val endScreen: Boolean,
        val openInApp: Boolean,
        val amoled: Boolean,
        val autoRepeat: Boolean,
        val playbackSpeed: Float,
        val backgroundPlayback: Boolean,
        val preferredQuality: String,
        val communitySponsorSkip: Boolean,
        val skipIntrosOutros: Boolean,
        val ruleVersion: Int,
        val ruleHash: Int
    )

    private val scriptCache = object : LinkedHashMap<ScriptKey, String>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ScriptKey, String>?): Boolean = size > 4
    }


    /** Policy source strings are reproducible. Drop them before media/browser state when Android
     * reports memory pressure; the active document already owns its evaluated JavaScript. */
    fun trimCache() {
        synchronized(scriptCache) { scriptCache.clear() }
    }

    fun setPowerConstrained(constrained: Boolean): String =
        // This snippet is followed by IIFEs in the combined page policy. A newline
        // alone would call its return value and abort the native-chrome CSS.
        "window.__videoShieldSetPowerConstrained && window.__videoShieldSetPowerConstrained(${if (constrained) "true" else "false"});"

    fun setPreferredQuality(quality: String): String {
        val safe = quality.takeIf { it in ShieldPreferences.SUPPORTED_QUALITY_VALUES } ?: "adaptive"
        return "window.__videoShieldSetPreferredQuality && window.__videoShieldSetPreferredQuality(${JSONObject.quote(safe)})"
    }

    fun build(
        preferences: ShieldPreferences,
        rules: RulePack,
        pageWhitelisted: Boolean,
        preferredQualityOverride: String? = null
    ): String {
        val key = ScriptKey(
            enabled = preferences.shieldEnabled,
            safeMode = preferences.safeMode,
            bypassAds = pageWhitelisted,
            shorts = preferences.blockShorts,
            recommendations = preferences.blockRecommendations,
            comments = preferences.blockComments,
            endScreen = preferences.blockEndScreen,
            openInApp = preferences.blockOpenInApp,
            amoled = preferences.amoledTheme,
            autoRepeat = preferences.autoRepeat,
            playbackSpeed = preferences.playbackSpeed,
            backgroundPlayback = preferences.backgroundControls && preferences.screenOffPlayback,
            preferredQuality = preferredQualityOverride ?: preferences.preferredQuality,
            communitySponsorSkip = preferences.communitySponsorSkip,
            skipIntrosOutros = preferences.skipIntrosOutros,
            ruleVersion = rules.ruleVersion,
            ruleHash = rules.rawJson.hashCode()
        )
        synchronized(scriptCache) { scriptCache[key]?.let { return it } }

        val cfg = JSONObject().apply {
            put("enabled", key.enabled)
            put("safeMode", key.safeMode)
            put("bypassAds", key.bypassAds)
            put("shorts", key.shorts)
            put("recommendations", key.recommendations)
            put("comments", key.comments)
            put("endScreen", key.endScreen)
            put("openInApp", key.openInApp)
            put("amoled", key.amoled)
            put("autoRepeat", key.autoRepeat)
            put("playbackSpeed", key.playbackSpeed)
            put("backgroundPlayback", key.backgroundPlayback)
            put("preferredQuality", key.preferredQuality)
            put("communitySponsorSkip", key.communitySponsorSkip)
            put("skipIntrosOutros", key.skipIntrosOutros)
        }.toString()
        val ruleJson = rules.domRulesJson()

        val script = """
        (() => {
          try {
            window.__videoShieldCfg = $cfg;
            window.__videoShieldRules = $ruleJson;

            if (window.__videoShieldInstalled) {
              if (window.__videoShieldPreferencesChanged) window.__videoShieldPreferencesChanged();
              if (window.__videoShieldSweep) window.__videoShieldSweep();
              if (window.__videoShieldScheduleCompatibility) window.__videoShieldScheduleCompatibility();
              return;
            }
            window.__videoShieldInstalled = true;
            if (typeof window.__videoShieldPowerConstrained !== 'boolean') window.__videoShieldPowerConstrained = false;

            let adState = null;
            let lastSkipButton = null;
            let lastSkipAt = 0;
            let lastPlaying = null;
            let lastBuffering = false;
            let lastTitle = "";
            let lastChannel = "";
            let lastVideoId = "";
            let channelInfoVideoId = "";
            let cachedChannelInfo = { name: '', url: '' };
            let channelInfoAt = 0;
            let lastPlaybackReportAt = 0;
            let lastReportedPosition = 0;
            let lastEndedVideoId = "";
            let repeatBoundVideo = null;
            let repeatEndedHandler = null;
            let repeatRestartAt = 0;
            let repeatRestartVideoId = "";
            let repeatUiInteractionUntil = 0;
            let repeatUiPending = false;
            let repeatLastReportedState = null;
            let rateBoundVideo = null;
            let rateChangeHandler = null;
            let rateRetryTimer = null;
            let rateRetryGeneration = 0;
            let ratePlayerApiVideo = null;
            let ratePlayerApiValue = NaN;
            let lastRequestedRate = 1;
            let rateUiInteractionUntil = 0;
            let rateLastReportedValue = NaN;
            let hiddenSeen = new WeakSet();
            let lastBridgeUpdate = 0;
            let lastBridgeHref = "";
            let lastMetadataRetryAt = 0;
            let playbackHeartbeatTimer = null;
            let metadataBridgeRetryTimer = null;
            let metadataBridgeRetryVideoId = "";
            let metadataBridgeRetryCount = 0;
            let playbackBridgeReports = 0;
            let playbackHeartbeatFires = 0;
            let lastRateApiVerifyAt = 0;
            let repeatAutonavVideoId = "";
            let repeatAutonavAt = 0;
            let lastQualityPolicyAt = 0;
            let lastQualityPolicyVideoId = "";
            let lastQualityPolicyMode = "";
            let segmentCursor = 0;
            let audioBoundVideo = null;
            let originalHiddenGetter = null;
            let originalVisibilityStateGetter = null;
            let internalErrors = 0;
            let compatibilityTimer = null;
            let lastCompatibilityUrl = "";
            let qualityAppliedVideoId = "";
            let qualitySession = null;
            let qualityPlaybackIntent = null;
            let qualityTransition = null;
            let qualityResumeTimer = null;
            let qualitySource = null;
            let lastNativeAutonavAt = 0;
            let nativeAutonavVideoId = '';
            let segmentVideoId = "";
            let communitySegments = [];
            let skippedSegmentKeys = new Set();
            let lastSegmentPosition = 0;
            let scanningSweep = false;
            let sweepVideoCache;
            let mobileSkipCache;
            let observedSweepTimer = null;
            let fallbackSweepTimer = null;
            let fullDomIdleHandle = null;
            let fullDomFallbackTimer = null;
            let fullDomMaintenancePending = false;
            let fullDomMaintenanceRuns = 0;
            let fullDomMaintenanceCoalesced = 0;
            let lastSweepAt = 0;
            let lastFullDomSweepAt = 0;
            let preloadBoundVideo = null;
            let preloadAttributeObserver = null;
            let bufferingVideo = null;
            let bufferingSince = 0;
            let bufferingTimer = null;
            let bufferNudgeCount = 0;
            let lastBufferNudgeAt = 0;
            let shortsActiveVideo = null;
            let lastMobileSkipScanAt = 0;
            let lastMobileSkipResult = null;
            let sessionCompactions = 0;

            const EMPTY = '__VS_EMPTY__';

            function isShortsRoute() {
              try { return /^\/shorts(?:\/|$)/.test(new URL(location.href).pathname); }
              catch (_) { return false; }
            }

            function isBrowseShortsSurface() {
              // Shorts need to stay owned by YouTube's scroll/feed renderer. The browse
              // WebView intentionally has no VideoShieldBridge; the dedicated player does.
              return isShortsRoute() && typeof window.VideoShieldBridge !== 'object';
            }

            function resolvePlayerVideo() {
              try {
                // Shorts keep several adjacent media elements mounted so the next clip can
                // start immediately. querySelector() returns the first item, not necessarily
                // the one centered on screen. Pick the visible/playing Shorts video first.
                if (isShortsRoute()) {
                  // The capturing `play` listener below tells us which recycler item became
                  // active. Reusing that element avoids an O(N) geometry walk on every shield
                  // sweep after a long Shorts session. Only fall back to a DOM scan if YouTube
                  // replaced the media element without emitting a usable play event.
                  const cached = shortsActiveVideo;
                  if (cached && cached.isConnected !== false) {
                    try {
                      if (!cached.paused && !cached.ended && cached.readyState >= 1) return cached;
                      const r = cached.getBoundingClientRect();
                      const vh = Math.max(1, window.innerHeight || document.documentElement?.clientHeight || 1);
                      const vw = Math.max(1, window.innerWidth || document.documentElement?.clientWidth || 1);
                      const visibleWidth = Math.max(0, Math.min(r.right, vw) - Math.max(r.left, 0));
                      const visibleHeight = Math.max(0, Math.min(r.bottom, vh) - Math.max(r.top, 0));
                      if (r.width > 1 && r.height > 1 && visibleWidth * visibleHeight >= r.width * r.height * 0.35 &&
                          Math.abs((r.top + r.bottom) * 0.5 - vh * 0.5) <= vh * 0.7) return cached;
                    } catch (_) {}
                  }

                  const shortsVideos = Array.from(document.querySelectorAll(
                    'video.video-stream.html5-main-video, video.html5-main-video, video'
                  )).filter(video => video && video.isConnected !== false);
                  const playing = shortsVideos.find(video => !video.paused && !video.ended && video.readyState >= 1);
                  if (playing) { shortsActiveVideo = playing; return playing; }

                  let best = null;
                  let bestScore = -Infinity;
                  const viewportHeight = Math.max(1, window.innerHeight || document.documentElement?.clientHeight || 1);
                  const viewportWidth = Math.max(1, window.innerWidth || document.documentElement?.clientWidth || 1);
                  for (const video of shortsVideos) {
                    try {
                      const rect = video.getBoundingClientRect();
                      if (rect.width <= 1 || rect.height <= 1) continue;
                      const visibleWidth = Math.max(0, Math.min(rect.right, viewportWidth) - Math.max(rect.left, 0));
                      const visibleHeight = Math.max(0, Math.min(rect.bottom, viewportHeight) - Math.max(rect.top, 0));
                      const area = Math.max(1, rect.width * rect.height);
                      const visibleRatio = (visibleWidth * visibleHeight) / area;
                      if (visibleRatio <= 0.01) continue;
                      const center = (rect.top + rect.bottom) * 0.5;
                      const centerDistance = Math.abs(center - viewportHeight * 0.5) / viewportHeight;
                      const score = visibleRatio * 100 + (video.readyState >= 2 ? 8 : 0) - centerDistance * 20;
                      if (score > bestScore) { best = video; bestScore = score; }
                    } catch (_) {}
                  }
                  if (best) { shortsActiveVideo = best; return best; }
                }

                // Normal watch pages have one authoritative html5-main-video.
                const main = document.querySelector('.html5-video-player video.html5-main-video') ||
                  document.querySelector('video.video-stream.html5-main-video') ||
                  document.querySelector('video.html5-main-video');
                if (main && main.isConnected !== false) return main;

                const player = document.querySelector('.html5-video-player');
                const candidates = Array.from(player?.querySelectorAll('video') || document.querySelectorAll('video'))
                  .filter(video => video && video.isConnected !== false);
                if (!candidates.length) return null;
                const playing = candidates.find(video => !video.paused && !video.ended && video.readyState >= 2);
                if (playing) return playing;
                const visible = candidates.find(video => {
                  try {
                    const rect = video.getBoundingClientRect();
                    return rect.width > 1 && rect.height > 1 && video.readyState >= 1;
                  } catch (_) { return false; }
                });
                return visible || candidates.find(video => video.readyState >= 1) || candidates[0] || null;
              } catch (_) {
                internalErrors++;
                return null;
              }
            }

            function getPlayerVideo() {
              if (scanningSweep && sweepVideoCache !== undefined) return sweepVideoCache;
              const video = resolvePlayerVideo();
              if (scanningSweep) sweepVideoCache = video;
              return video;
            }

            function releasePreloadPolicy() {
              try { preloadAttributeObserver?.disconnect?.(); } catch (_) {}
              preloadAttributeObserver = null;
              preloadBoundVideo = null;
            }

            function bufferedAheadSeconds(video) {
              try {
                if (!video) return 0;
                const position = Number(video.currentTime) || 0;
                let ahead = 0;
                for (let i = 0; video.buffered && i < video.buffered.length; i++) {
                  if (video.buffered.start(i) <= position && video.buffered.end(i) >= position) {
                    ahead = Math.max(ahead, video.buffered.end(i) - position);
                  }
                }
                return Math.max(0, ahead);
              } catch (_) { internalErrors++; return 0; }
            }

            function clearBufferRecovery(video = null) {
              if (video && bufferingVideo && bufferingVideo !== video) return;
              try { if (bufferingTimer) clearTimeout(bufferingTimer); } catch (_) {}
              bufferingTimer = null;
              bufferingVideo = null;
              bufferingSince = 0;
              bufferNudgeCount = 0;
            }

            function scheduleBufferRecovery(video) {
              if (isBrowseShortsSurface() || !video || video !== getPlayerVideo() || isPlayerAd() || video.paused || video.ended) {
                clearBufferRecovery(video);
                return;
              }
              if (bufferingVideo !== video) {
                clearBufferRecovery();
                bufferingVideo = video;
                bufferingSince = Date.now();
              }
              if (bufferingTimer || bufferNudgeCount >= 2) return;

              const constrained = !!window.__videoShieldPowerConstrained;
              const delay = bufferNudgeCount === 0 ? (constrained ? 1800 : 1200) : (constrained ? 3600 : 2800);
              bufferingTimer = setTimeout(() => {
                bufferingTimer = null;
                if (video !== getPlayerVideo() || video.paused || video.ended || isPlayerAd()) {
                  clearBufferRecovery(video);
                  return;
                }
                const rate = Math.max(0.25, Number(video.playbackRate) || 1);
                const ahead = bufferedAheadSeconds(video);
                if (video.readyState >= 3 || ahead >= Math.max(1.5, rate * 1.5)) {
                  clearBufferRecovery(video);
                  updatePlaybackBridge(true);
                  return;
                }

                // Never call load(): YouTube's MediaSource owns the buffered ranges. A stall
                // nudge only restores preload intent and schedules one lightweight media sweep.
                applyPreloadPolicy(video);
                scheduleObservedSweep();
                const now = Date.now();
                if (bufferNudgeCount > 0 && !video.seeking && !qualityTransition && now - lastBufferNudgeAt >= 2500) {
                  try {
                    const player = playerForVideo(video);
                    if (typeof player?.getPlayerState === 'function' && player.getPlayerState() === 3 &&
                        typeof player.playVideo === 'function') {
                      player.playVideo();
                      lastBufferNudgeAt = now;
                    }
                  } catch (_) { internalErrors++; }
                }
                bufferNudgeCount++;
                if (bufferNudgeCount < 2) scheduleBufferRecovery(video);
              }, delay);
            }

            function mediaRetentionMode() {
              const value = String(window.__videoShieldMediaRetentionMode || 'active').toLowerCase();
              return value === 'cold' || value === 'lean' || value === 'warm' ? value : 'active';
            }

            function desiredMediaPreload(video) {
              // Never reduce preload underneath active playback, buffering, PiP or a seek. The
              // retention hint is only for a genuinely paused Watch session.
              if (!video || !video.paused || video.ended || video.seeking ||
                  document.getElementById('youtoobee-pip-style')) return 'auto';
              const mode = mediaRetentionMode();
              if (mode === 'cold') return 'none';
              if (mode === 'lean') return 'metadata';
              return 'auto';
            }

            function applyPreloadPolicy(video) {
              if (isBrowseShortsSurface()) { releasePreloadPolicy(); return; }
              if (!video || video !== getPlayerVideo()) return;
              try {
                // YouTube normally feeds this element through MediaSource. `load()` must not be
                // called here: it tears down that MediaSource and throws away already-buffered
                // ranges. For paused/background sessions we only lower the browser's future
                // preload hint; Chromium still owns all already-buffered MediaSource ranges.
                const desired = desiredMediaPreload(video);
                if (video.preload !== desired) video.preload = desired;
                if (video.getAttribute('preload') !== desired) video.setAttribute('preload', desired);
                if (desired === 'auto') {
                  if (!video.hasAttribute('autobuffer')) video.setAttribute('autobuffer', '');
                } else if (video.hasAttribute('autobuffer')) {
                  video.removeAttribute('autobuffer');
                }

                if (preloadBoundVideo === video) return;
                preloadBoundVideo = video;
                try { preloadAttributeObserver?.disconnect?.(); } catch (_) {}
                preloadAttributeObserver = new MutationObserver(() => {
                  if (video !== getPlayerVideo()) return;
                  try {
                    const target = desiredMediaPreload(video);
                    if (video.getAttribute('preload') !== target) video.setAttribute('preload', target);
                    if (target === 'auto') {
                      if (!video.hasAttribute('autobuffer')) video.setAttribute('autobuffer', '');
                    } else if (video.hasAttribute('autobuffer')) {
                      video.removeAttribute('autobuffer');
                    }
                  } catch (_) { internalErrors++; }
                });
                preloadAttributeObserver.observe(video, {attributes:true, attributeFilter:['preload','autobuffer']});
              } catch (_) { internalErrors++; }
            }

            function configuredPlaybackRate() {
              const raw = Number((window.__videoShieldCfg || {}).playbackSpeed);
              return Number.isFinite(raw) && raw > 0 ? Math.max(0.25, Math.min(4, raw)) : 1;
            }

            function normalizePlaybackRate(value) {
              const raw = Number(value);
              if (!Number.isFinite(raw) || raw <= 0) return null;
              return Math.max(0.25, Math.min(4, raw));
            }

            function reportPlaybackRateSelection(rate) {
              const target = normalizePlaybackRate(rate);
              if (target === null) return;
              if (Number.isFinite(rateLastReportedValue) && Math.abs(rateLastReportedValue - target) <= 0.001) return;
              rateLastReportedValue = target;
              try {
                if (typeof window.VideoShieldBridge?.onPlaybackRateSelected === 'function') {
                  window.VideoShieldBridge.onPlaybackRateSelected(target);
                }
              } catch (_) { internalErrors++; }
            }

            function adoptWebsitePlaybackRate(video, rate) {
              const target = normalizePlaybackRate(rate);
              if (target === null || !video || isPlayerAd()) return false;
              try {
                if (!window.__videoShieldCfg) window.__videoShieldCfg = {};
                window.__videoShieldCfg.playbackSpeed = target;
                lastRequestedRate = target;
                ratePlayerApiVideo = video;
                ratePlayerApiValue = target;
                try { video.defaultPlaybackRate = target; } catch (_) {}
                reportPlaybackRateSelection(target);
                return true;
              } catch (_) { internalErrors++; return false; }
            }

            function playbackRateMenuItem(node) {
              try {
                let item = node?.nodeType === 1 ? node : node?.parentElement;
                for (let depth = 0; item && depth < 9; depth++, item = item.parentElement) {
                  const candidate = item.matches?.(
                    '[role="menuitem"],[role="menuitemradio"],[role="menuitemcheckbox"],ytm-menu-item,ytm-menu-service-item-renderer,tp-yt-paper-item,button'
                  );
                  if (!candidate) continue;
                  const label = String(item.getAttribute?.('aria-label') || item.innerText || item.textContent || '')
                    .replace(/\s+/g, ' ').trim().toLocaleLowerCase();
                  if (/(playback\s*speed|(^|\s)speed(\s|$)|tốc\s*độ|toc\s*do)/i.test(label)) return item;
                }
              } catch (_) {}
              return null;
            }

            function playbackRateOptionValue(node) {
              try {
                let item = node?.nodeType === 1 ? node : node?.parentElement;
                for (let depth = 0; item && depth < 7; depth++, item = item.parentElement) {
                  if (!item.matches?.('[role="menuitem"],[role="menuitemradio"],[role="option"],button,tp-yt-paper-item,ytm-menu-item')) continue;
                  const label = String(item.getAttribute?.('aria-label') || item.innerText || item.textContent || '')
                    .replace(/\s+/g, ' ').trim().toLocaleLowerCase();
                  if (/^(normal|bình thường|binh thuong)$/i.test(label)) return 1;
                  const match = label.match(/^([0-4](?:[.,][0-9]{1,2})?)\s*(?:x|×)$/i);
                  if (match) return normalizePlaybackRate(match[1].replace(',', '.'));
                }
              } catch (_) {}
              return null;
            }

            function settleWebsitePlaybackRate(preferredRate = null) {
              const video = getPlayerVideo();
              if (!video || isPlayerAd() || isBrowseShortsSurface()) return false;
              let target = normalizePlaybackRate(preferredRate);
              try {
                const player = playerForVideo(video);
                const playerRate = player && typeof player.getPlaybackRate === 'function'
                  ? normalizePlaybackRate(player.getPlaybackRate()) : null;
                const mediaRate = normalizePlaybackRate(video.playbackRate);
                if (target === null) {
                  const configured = configuredPlaybackRate();
                  const mediaChanged = mediaRate !== null && Math.abs(mediaRate - configured) > 0.01;
                  const playerChanged = playerRate !== null && Math.abs(playerRate - configured) > 0.01;
                  // The HTMLMediaElement rate is the final playback cadence. Prefer a
                  // changed media value over a stale player API value during menu updates.
                  if (mediaChanged) target = mediaRate;
                  else if (playerChanged) target = playerRate;
                  else target = mediaRate !== null ? mediaRate : playerRate;
                }
                if (target === null) return false;
                adoptWebsitePlaybackRate(video, target);
                // Apply after YouTube's click handler. This is intentionally delayed so
                // the app never races the settings sheet while it changes renderer state.
                applyPlaybackRatePolicy(video);
                schedulePlaybackRateRestore(video, 140);
                return true;
              } catch (_) { internalErrors++; return false; }
            }

            function beginWebsitePlaybackRateInteraction(node) {
              const optionRate = playbackRateOptionValue(node);
              rateUiInteractionUntil = Date.now() + 5000;
              cancelPlaybackRateRestore();
              if (optionRate !== null) {
                // Update the app-side target during capture, before YouTube's own click
                // handler emits ratechange. Otherwise the old sticky target can instantly
                // pull the new website selection back to 1x.
                const video = getPlayerVideo();
                if (video) adoptWebsitePlaybackRate(video, optionRate);
              }
              const schedule = (delay) => setTimeout(() => settleWebsitePlaybackRate(optionRate), delay);
              schedule(optionRate !== null ? 45 : 120);
              schedule(optionRate !== null ? 220 : 420);
              schedule(optionRate !== null ? 700 : 900);
            }

            function playerForVideo(video) {
              try {
                const closest = video?.closest?.('.html5-video-player');
                if (closest) return closest;
                // Never fall back to the first Shorts player: adjacent Shorts are kept
                // mounted and the first one may be several swipes away from the viewport.
                if (isShortsRoute()) return null;
                return document.querySelector('.html5-video-player');
              } catch (_) {
                internalErrors++;
                return isShortsRoute() ? null : document.querySelector('.html5-video-player');
              }
            }

            function bindPlaybackRateVideo(video) {
              if (rateBoundVideo === video) return;
              try {
                if (rateBoundVideo && rateChangeHandler) {
                  rateBoundVideo.removeEventListener('ratechange', rateChangeHandler, true);
                }
              } catch (_) { internalErrors++; }
              rateBoundVideo = video || null;
              rateChangeHandler = null;
              if (!video) return;
              rateChangeHandler = () => {
                if (video !== getPlayerVideo() || isPlayerAd()) return;
                const actual = normalizePlaybackRate(video.playbackRate);
                const target = configuredPlaybackRate();
                // A website speed-menu selection is a user preference, not a player
                // reset. Adopt it before the sticky policy has a chance to undo it.
                if (Date.now() < rateUiInteractionUntil && actual !== null &&
                    Math.abs(actual - target) > 0.01) {
                  adoptWebsitePlaybackRate(video, actual);
                  schedulePlaybackRateRestore(video, 180);
                  return;
                }
                if (actual !== null && Math.abs(actual - target) > 0.01) {
                  ratePlayerApiValue = NaN;
                  schedulePlaybackRateRestore(video, 40);
                }
              };
              try { video.addEventListener('ratechange', rateChangeHandler, true); } catch (_) { internalErrors++; }
            }

            function applyPlaybackRatePolicy(video) {
              if (isBrowseShortsSurface()) { bindPlaybackRateVideo(null); return false; }
              if (!video) return false;
              bindPlaybackRateVideo(video);
              const target = configuredPlaybackRate();
              lastRequestedRate = target;

              // Keep ads under the ad skipper's own temporary speed, but update the
              // content rate that will be restored immediately after the ad.
              if (isPlayerAd()) {
                if (adState && adState.video === video) adState.playbackRate = target;
                return false;
              }

              let applied = false;
              try {
                const mediaRate = Number(video.playbackRate) || 1;
                const defaultRate = Number(video.defaultPlaybackRate) || 1;
                const now = Date.now();
                const mediaStable = Math.abs(mediaRate - target) <= 0.01 &&
                  Math.abs(defaultRate - target) <= 0.01;
                const cachedApiStable = ratePlayerApiVideo === video &&
                  Number.isFinite(ratePlayerApiValue) &&
                  Math.abs(ratePlayerApiValue - target) <= 0.01;

                // The HTMLMediaElement is authoritative for cadence. Avoid querying the
                // heavier YouTube player API on every shield sweep when both media rates
                // and our last API write are already correct. Re-verify periodically so a
                // silent internal YouTube reset is still repaired.
                if (mediaStable && cachedApiStable && now - lastRateApiVerifyAt < 8000) {
                  return true;
                }

                const player = playerForVideo(video);
                let playerRate = NaN;
                if (player && typeof player.getPlaybackRate === 'function') {
                  try { playerRate = Number(player.getPlaybackRate()); } catch (_) {}
                }
                lastRateApiVerifyAt = now;

                if (player && typeof player.setPlaybackRate === 'function' &&
                    (ratePlayerApiVideo !== video || !Number.isFinite(ratePlayerApiValue) ||
                     Math.abs(ratePlayerApiValue - target) > 0.01 ||
                     (Number.isFinite(playerRate) && Math.abs(playerRate - target) > 0.01) ||
                     Math.abs(mediaRate - target) > 0.01)) {
                  try {
                    player.setPlaybackRate(target);
                    ratePlayerApiVideo = video;
                    ratePlayerApiValue = target;
                    applied = true;
                  } catch (_) {
                    ratePlayerApiValue = NaN;
                  }
                }
                if (Math.abs(defaultRate - target) > 0.01) {
                  video.defaultPlaybackRate = target;
                  applied = true;
                }
                if (Math.abs(mediaRate - target) > 0.01) {
                  video.playbackRate = target;
                  applied = true;
                }
                return applied || Math.abs((Number(video.playbackRate) || 1) - target) <= 0.01;
              } catch (_) {
                internalErrors++;
                return false;
              }
            }

            function cancelPlaybackRateRestore() {
              rateRetryGeneration++;
              if (rateRetryTimer) clearTimeout(rateRetryTimer);
              rateRetryTimer = null;
            }

            function schedulePlaybackRateRestore(video, firstDelay = 60) {
              cancelPlaybackRateRestore();
              const generation = rateRetryGeneration;
              const delays = [firstDelay, 180, 450, 900, 1600];
              let attempt = 0;
              const restore = () => {
                rateRetryTimer = null;
                if (generation !== rateRetryGeneration) return;
                const media = getPlayerVideo();
                if (!media || media !== video || isPlayerAd()) return;
                applyPlaybackRatePolicy(media);
                const target = configuredPlaybackRate();
                if (Math.abs((Number(media.playbackRate) || 1) - target) <= 0.01) return;
                attempt++;
                if (attempt < delays.length) rateRetryTimer = setTimeout(restore, delays[attempt]);
              };
              rateRetryTimer = setTimeout(restore, delays[0]);
            }

            function repeatMenuItem(node) {
              try {
                let item = node?.nodeType === 1 ? node : node?.parentElement;
                // The visible switch/thumb is often a child button whose own label is
                // only "On/Off". Walk up to the containing menu row instead of stopping
                // at the first button, otherwise taps directly on the switch are missed.
                for (let depth = 0; item && depth < 9; depth++, item = item.parentElement) {
                  const candidate = item.matches?.(
                    '[role="menuitem"],[role="menuitemcheckbox"],[role="switch"],ytm-menu-item,ytm-menu-service-item-renderer,ytm-toggle-item-renderer,tp-yt-paper-item,button'
                  );
                  if (!candidate) continue;
                  const label = String(item.getAttribute?.('aria-label') || item.innerText || item.textContent || '')
                    .replace(/\s+/g, ' ').trim().toLocaleLowerCase();
                  if (/(^|\s)(repeat|loop|lặp lại|lap lai)(\s|$)/i.test(label)) return item;
                }
              } catch (_) {}
              return null;
            }

            function repeatUiState(item, video) {
              try {
                const candidates = [
                  item,
                  item?.querySelector?.('[role="switch"][aria-checked]'),
                  item?.querySelector?.('[role="checkbox"][aria-checked]'),
                  item?.querySelector?.('[aria-checked]'),
                  item?.querySelector?.('input[type="checkbox"]'),
                  item?.querySelector?.('tp-yt-paper-toggle-button')
                ].filter(Boolean);
                for (const candidate of candidates) {
                  const aria = candidate.getAttribute?.('aria-checked');
                  if (aria === 'true') return true;
                  if (aria === 'false') return false;
                  if (typeof candidate.checked === 'boolean') return !!candidate.checked;
                  if (candidate.hasAttribute?.('checked')) return true;
                }
              } catch (_) { internalErrors++; }
              return video ? !!video.loop : null;
            }

            function reportRepeatSelection(enabled) {
              const value = !!enabled;
              if (repeatLastReportedState === value) return;
              repeatLastReportedState = value;
              try {
                if (typeof window.VideoShieldBridge?.onRepeatSelected === 'function') {
                  window.VideoShieldBridge.onRepeatSelected(value);
                }
              } catch (_) { internalErrors++; }
            }

            function commitWebsiteRepeatSelection(item, fallbackState) {
              const video = getPlayerVideo();
              if (!video || isPlayerAd()) return;
              let enabled = repeatUiState(item, video);
              // Some mobile YouTube builds keep the toggle state in an internal
              // renderer model and update video.loop a little later. A click on the
              // Repeat row still has unambiguous toggle semantics, so fall back to
              // the inverse of the state captured before the click.
              if (enabled === null || enabled === fallbackState) {
                const mediaState = !!video.loop;
                enabled = mediaState !== fallbackState ? mediaState : !fallbackState;
              }
              if (!window.__videoShieldCfg) window.__videoShieldCfg = {};
              window.__videoShieldCfg.autoRepeat = !!enabled;
              repeatUiInteractionUntil = Date.now() + 500;
              repeatUiPending = false;
              // Apply only the ON side immediately. On OFF, leaving video.loop alone
              // for this turn lets YouTube finish its own click handler without a race.
              if (enabled) {
                try { video.loop = true; } catch (_) { internalErrors++; }
                lastEndedVideoId = '';
              } else {
                repeatRestartAt = 0;
                repeatRestartVideoId = '';
              }
              reportRepeatSelection(enabled);
            }

            function beginWebsiteRepeatInteraction(item) {
              if (!item) return;
              repeatUiInteractionUntil = Date.now() + 1200;
              if (repeatUiPending) return;
              repeatUiPending = true;
              const before = !!(window.__videoShieldCfg || {}).autoRepeat;
              // Run after YouTube's own click/toggle handler. Multiple checkpoints
              // cover both synchronous renderers and Polymer updates deferred a frame.
              setTimeout(() => commitWebsiteRepeatSelection(item, before), 40);
              setTimeout(() => {
                if (repeatUiPending) commitWebsiteRepeatSelection(item, before);
              }, 180);
            }

            function bindRepeatVideo(video) {
              if (repeatBoundVideo === video) return;
              try {
                if (repeatBoundVideo && repeatEndedHandler) {
                  repeatBoundVideo.removeEventListener('ended', repeatEndedHandler, true);
                  repeatBoundVideo.removeEventListener('timeupdate', repeatEndedHandler, true);
                }
              } catch (_) { internalErrors++; }
              repeatBoundVideo = video || null;
              repeatEndedHandler = null;
              if (!video) return;
              repeatEndedHandler = () => {
                const c = window.__videoShieldCfg || {};
                if (!c.autoRepeat || video !== getPlayerVideo() || isPlayerAd()) return;
                const duration = Number(video.duration);
                const position = Number(video.currentTime);
                // When loop=true the HTML media element loops at the exact end and the
                // ended event is suppressed. Only use an early restart as a fallback if
                // YouTube has unexpectedly cleared loop while repeat is still enabled.
                if (video.ended || (!video.loop && Number.isFinite(duration) && duration > 0.5 &&
                    Number.isFinite(position) && !video.seeking && position >= duration - 0.18)) {
                  restartRepeatedVideo(video);
                }
              };
              try {
                video.addEventListener('ended', repeatEndedHandler, true);
                video.addEventListener('timeupdate', repeatEndedHandler, true);
              } catch (_) { internalErrors++; }
            }

            function restartRepeatedVideo(video) {
              const c = window.__videoShieldCfg || {};
              if (!c.autoRepeat || !video || video !== getPlayerVideo() || isPlayerAd()) return false;
              const videoId = getVideoId();
              if (!videoId) return false;
              const now = Date.now();
              if (repeatRestartVideoId === videoId && now - repeatRestartAt < 350) return true;
              repeatRestartVideoId = videoId;
              repeatRestartAt = now;
              lastEndedVideoId = '';
              try {
                video.loop = true;
                const player = video.closest?.('.html5-video-player') || document.querySelector('.html5-video-player');
                if (player && typeof player.seekTo === 'function') player.seekTo(0, true);
                try { video.currentTime = 0; } catch (_) {}
                applyPlaybackRatePolicy(video);
                if (player && typeof player.playVideo === 'function') player.playVideo();
                const result = video.play();
                if (result && typeof result.catch === 'function') result.catch(() => {});
                return true;
              } catch (_) {
                internalErrors++;
                try {
                  video.currentTime = 0;
                  const result = video.play();
                  if (result && typeof result.catch === 'function') result.catch(() => {});
                  return true;
                } catch (_) { internalErrors++; return false; }
              }
            }

            function applyRepeatPolicy(video, explicit = false) {
              if (isBrowseShortsSurface()) {
                bindRepeatVideo(null);
                repeatRestartAt = 0; repeatRestartVideoId = '';
                return;
              }
              bindRepeatVideo(video);
              const enabled = !!(window.__videoShieldCfg || {}).autoRepeat;
              if (!video) return;
              try {
                const inWebsiteToggle = Date.now() < repeatUiInteractionUntil;
                // If the website turned loop on while the native preference was off,
                // adopt that state instead of fighting the user's own player menu.
                if (!explicit && !inWebsiteToggle && !enabled && !!video.loop && !isPlayerAd()) {
                  window.__videoShieldCfg.autoRepeat = true;
                  reportRepeatSelection(true);
                }
                const effective = !!(window.__videoShieldCfg || {}).autoRepeat;
                if (adState && adState.video === video && effective) adState.loop = true;
                if (!isPlayerAd() && !inWebsiteToggle) {
                  // Periodic policy only enforces ON. OFF is written only by an explicit
                  // native setting, so the website's Repeat switch can change state.
                  if (effective) video.loop = true;
                  else if (explicit) video.loop = false;
                }
                if (effective) {
                  const videoId = getVideoId();
                  const now = Date.now();
                  if (videoId !== repeatAutonavVideoId || now - repeatAutonavAt >= 10000) {
                    const player = playerForVideo(video);
                    if (player && typeof player.setAutonavState === 'function') {
                      try {
                        player.setAutonavState(1);
                        repeatAutonavVideoId = videoId;
                        repeatAutonavAt = now;
                      } catch (_) {}
                    }
                  }
                  lastEndedVideoId = '';
                }
              } catch (_) { internalErrors++; }
              if (!(window.__videoShieldCfg || {}).autoRepeat) {
                repeatRestartAt = 0;
                repeatRestartVideoId = '';
              }
            }

            function isActuallyHidden() {
              try {
                if (typeof originalHiddenGetter === 'function') return !!originalHiddenGetter.call(document);
                if (typeof originalVisibilityStateGetter === 'function')
                  return originalVisibilityStateGetter.call(document) === 'hidden';
              } catch (_) {}
              return document.visibilityState === 'hidden';
            }

            // Keep the trusted player page active when background playback is enabled.
            // YouTube otherwise unloads its media on visibility changes, not just pause().
            // Preserve the original getters separately so our own scheduler can still
            // recognize the real background state and reduce CPU usage.
            if (typeof window.VideoShieldBridge === 'object') {
              for (const key of ['hidden', 'visibilityState', 'webkitHidden', 'webkitVisibilityState']) {
                let owner = document;
                let descriptor;
                while (owner && !(descriptor = Object.getOwnPropertyDescriptor(owner, key))) owner = Object.getPrototypeOf(owner);
                if (!descriptor || typeof descriptor.get !== 'function') continue;
                const originalGet = descriptor.get;
                if (key === 'hidden' && !originalHiddenGetter) originalHiddenGetter = originalGet;
                if (key === 'visibilityState' && !originalVisibilityStateGetter) originalVisibilityStateGetter = originalGet;
                try {
                  Object.defineProperty(document, key, { configurable: true, get() {
                    if ((window.__videoShieldCfg || {}).backgroundPlayback) {
                      return key.toLowerCase().includes('hidden') ? false : 'visible';
                    }
                    return originalGet.call(document);
                  }});
                } catch (_) { internalErrors++; }
              }
            }

            function selectorsFor(key) {
              try {
                const r = window.__videoShieldRules || {};
                if (key === 'ads') return Array.isArray(r.adSelectors) ? r.adSelectors : [];
                if (key === 'skip') return Array.isArray(r.skipSelectors) ? r.skipSelectors : [];
                const a = r.annoyances || {};
                return Array.isArray(a[key]) ? a[key] : [];
              } catch (_) {
                internalErrors++;
                return [];
              }
            }

            function rememberStyle(node, reason) {
              if (!node || !node.style || node.hasAttribute('data-videoshield-hidden')) return;
              try {
                const d = node.style.getPropertyValue('display');
                const v = node.style.getPropertyValue('visibility');
                node.setAttribute('data-videoshield-hidden', '1');
                node.setAttribute('data-videoshield-reason', reason);
                node.setAttribute('data-videoshield-display', d || EMPTY);
                node.setAttribute('data-videoshield-display-priority', node.style.getPropertyPriority('display') || EMPTY);
                node.setAttribute('data-videoshield-visibility', v || EMPTY);
                node.setAttribute('data-videoshield-visibility-priority', node.style.getPropertyPriority('visibility') || EMPTY);
              } catch (_) { internalErrors++; }
            }

            function restoreNode(node) {
              if (!node || !node.style) return;
              try {
                const d = node.getAttribute('data-videoshield-display');
                const dp = node.getAttribute('data-videoshield-display-priority');
                const v = node.getAttribute('data-videoshield-visibility');
                const vp = node.getAttribute('data-videoshield-visibility-priority');
                if (d === EMPTY || d === null) node.style.removeProperty('display');
                else node.style.setProperty('display', d, dp === EMPTY || dp === null ? '' : dp);
                if (v === EMPTY || v === null) node.style.removeProperty('visibility');
                else node.style.setProperty('visibility', v, vp === EMPTY || vp === null ? '' : vp);
                ['data-videoshield-hidden','data-videoshield-reason','data-videoshield-display',
                 'data-videoshield-display-priority','data-videoshield-visibility',
                 'data-videoshield-visibility-priority'].forEach((a) => node.removeAttribute(a));
              } catch (_) { internalErrors++; }
            }

            function restoreReason(reason) {
              try {
                document.querySelectorAll('[data-videoshield-hidden="1"][data-videoshield-reason="' + reason + '"]')
                  .forEach(restoreNode);
              } catch (_) { internalErrors++; }
            }

            function hideNodes(selectors, countAds, reason) {
              let newlyHidden = 0;
              for (const selector of selectors) {
                let nodes = [];
                try { nodes = document.querySelectorAll(selector); } catch (_) { internalErrors++; continue; }
                nodes.forEach((node) => {
                  if (!node || !node.style) return;
                  // This container also owns Skip controls. Hiding it makes a valid Skip
                  // button invisible, so player ads must be handled by handlePlayerAd().
                  if (reason === 'ads' && typeof node.matches === 'function' &&
                      node.matches('.video-ads,.ytp-ad-module') && node.closest('.html5-video-player')) {
                    if (node.hasAttribute('data-videoshield-hidden')) restoreNode(node);
                    return;
                  }
                  rememberStyle(node, reason);
                  if (countAds && !hiddenSeen.has(node)) {
                    hiddenSeen.add(node);
                    newlyHidden++;
                  }
                  try {
                    if (node.style.getPropertyValue('display') !== 'none' || node.style.getPropertyPriority('display') !== 'important') {
                      node.style.setProperty('display', 'none', 'important');
                    }
                    if (node.style.getPropertyValue('visibility') !== 'hidden' || node.style.getPropertyPriority('visibility') !== 'important') {
                      node.style.setProperty('visibility', 'hidden', 'important');
                    }
                  } catch (_) { internalErrors++; }
                });
              }
              if (newlyHidden > 0 && window.VideoShieldBridge) {
                try { VideoShieldBridge.onPageAdsHidden(newlyHidden); } catch (_) { internalErrors++; }
              }
            }

            function hidePageAds() {
              const c = window.__videoShieldCfg || {};
              if (!c.enabled || c.bypassAds) {
                restoreReason('ads');
                return;
              }
              hideNodes(selectorsFor('ads'), true, 'ads');
            }

            function applyAnnoyance(key, enabled) {
              if (enabled) hideNodes(selectorsFor(key), false, key);
              else restoreReason(key);
            }

            function hideAnnoyances() {
              const c = window.__videoShieldCfg || {};
              applyAnnoyance('shorts', !!c.shorts);
              applyAnnoyance('recommendations', !!c.recommendations);
              applyAnnoyance('comments', !!c.comments);
              applyAnnoyance('endScreen', !!c.endScreen);
              applyAnnoyance('openInApp', !!c.openInApp);
            }

            function aggressiveBlockingAllowed() {
              const c = window.__videoShieldCfg || {};
              return !!c.enabled && !c.safeMode && !c.bypassAds;
            }

            function applyAmoledTheme() {
              const c = window.__videoShieldCfg || {};
              const id = 'videoshield-amoled-style';
              let style = document.getElementById(id);
              if (!c.amoled) {
                if (style && style.parentNode) style.parentNode.removeChild(style);
                return;
              }
              if (!style) {
                style = document.createElement('style');
                style.id = id;
                style.textContent = `
                  html, body, ytm-app, ytd-app, #content, #page-manager { background-color: #000 !important; }
                  .html5-video-player, .html5-video-container { background-color: #000 !important; }
                `;
                (document.head || document.documentElement).appendChild(style);
              }
            }

            function cancelQualityPlaybackRestore() {
              qualityPlaybackIntent = null;
              qualityTransition = null;
              if (qualityResumeTimer !== null) clearTimeout(qualityResumeTimer);
              qualityResumeTimer = null;
            }

            window.__videoShieldCancelQualityChange = cancelQualityPlaybackRestore;
            window.__videoShieldPrepareQualityChange = () => {
              if (isBrowseShortsSurface()) { cancelQualityPlaybackRestore(); return false; }
              const video = getPlayerVideo();
              if (!video || video.ended || isPlayerAd()) return false;
              const id = getVideoId();
              const previous = qualityTransition?.id === id ? qualityTransition : null;
              qualityPlaybackIntent = {
                id,
                wanted: previous ? previous.wanted : !video.paused,
                position: previous ? previous.position : (Number(video.currentTime) || 0),
                rate: configuredPlaybackRate(),
                loop: !!(window.__videoShieldCfg || {}).autoRepeat,
                capturedAt: Date.now()
              };
              return true;
            };

            function restoreQualityPlaybackNow(transition) {
              if (!transition || qualityTransition !== transition) return false;
              const media = getPlayerVideo();
              if (!media || getVideoId() !== transition.id ||
                  document.querySelector('.html5-video-player') !== transition.player ||
                  media.ended || isPlayerAd() || Date.now() >= transition.until) {
                cancelQualityPlaybackRestore();
                return false;
              }
              try {
                // A rendition switch can replace/reset the media element. Preserve the
                // user's playback position if the new source unexpectedly jumps backwards.
                const nowPosition = Number(media.currentTime) || 0;
                if (!media.seeking && transition.position > 3 && nowPosition < transition.position - 2.5) {
                  if (typeof transition.player.seekTo === 'function') transition.player.seekTo(transition.position, true);
                  else media.currentTime = transition.position;
                }

                // Quality transitions may also recreate/reset media properties.
                applyPlaybackRatePolicy(media);
                applyRepeatPolicy(media);

                if (transition.wanted && media.paused) {
                  if (typeof transition.player.playVideo === 'function') transition.player.playVideo();
                  else media.play()?.catch?.(()=>{});
                } else if (!transition.wanted && !media.paused) {
                  if (typeof transition.player.pauseVideo === 'function') transition.player.pauseVideo();
                  else media.pause();
                }
                return true;
              } catch (_) {
                internalErrors++;
                return false;
              }
            }

            function preserveQualityPlayback(player, video) {
              const id = getVideoId();
              const intent = qualityPlaybackIntent?.id === id ? qualityPlaybackIntent : null;
              const previous = qualityTransition?.id === id ? qualityTransition : null;
              const wanted = intent ? intent.wanted : previous ? previous.wanted : !video.paused;
              const position = intent ? intent.position : previous ? previous.position : (Number(video.currentTime) || 0);
              cancelQualityPlaybackRestore();
              if (video.ended || isPlayerAd()) return;
              const transition = {
                id, player, wanted, position,
                rate: intent ? intent.rate : configuredPlaybackRate(),
                loop: intent ? intent.loop : !!(window.__videoShieldCfg || {}).autoRepeat,
                startedAt: Date.now(), until: Date.now()+8000
              };
              qualityTransition = transition;

              // Apply once immediately, then keep enforcing the original play/pause
              // intent throughout the whole source/rendition transition. YouTube can
              // emit another pause well after its first successful play() callback.
              restoreQualityPlaybackNow(transition);
              const restore = () => {
                qualityResumeTimer = null;
                if (!restoreQualityPlaybackNow(transition)) return;
                qualityResumeTimer = setTimeout(restore, 250);
              };
              qualityResumeTimer = setTimeout(restore, 120);
            }

            window.__videoShieldCommitQualityChange = () => {
              if (isBrowseShortsSurface()) { cancelQualityPlaybackRestore(); return false; }
              const transition = qualityTransition;
              if (transition) return restoreQualityPlaybackNow(transition);
              const video = getPlayerVideo();
              if (!video || video.ended || isPlayerAd()) return false;
              const id = getVideoId();
              if (!qualityPlaybackIntent || qualityPlaybackIntent.id !== id) return false;
              const player = document.querySelector('.html5-video-player');
              if (!player) return false;
              preserveQualityPlayback(player, video);
              return true;
            };

            function applyPlaybackEnhancements(video) {
              if (!video) return;
              if (isBrowseShortsSurface()) {
                releasePreloadPolicy();
                bindPlaybackRateVideo(null);
                bindRepeatVideo(null);
                cancelQualityPlaybackRestore();
                return;
              }
              const c = window.__videoShieldCfg || {};
              applyPreloadPolicy(video);
              applyRepeatPolicy(video);
              applyPlaybackRatePolicy(video);

              const videoId = getVideoId();
              const quality = String(c.preferredQuality || 'auto');
              if (!videoId) return;
              const player = document.querySelector('.html5-video-player');
              if (!player) return;
              try {
                // Native Queue/related selection owns the next-video decision. Prevent
                // a second website countdown from racing it or ignoring the app toggle.
                if (video.readyState>=1 && typeof window.VideoShieldBridge?.onPlaybackEnded==='function' &&
                    typeof player.setAutonavState==='function' &&
                    (nativeAutonavVideoId!==videoId || Date.now()-lastNativeAutonavAt>=5000)) {
                  player.setAutonavState(1); lastNativeAutonavAt=Date.now(); nativeAutonavVideoId=videoId;
                }

                // Quality inspection is one of the most expensive steady-state tasks on
                // the watch page because it touches player APIs and buffered ranges.
                // Playback-rate/repeat/preload remain event-driven above; quality policy
                // only needs a slower maintenance cadence once the source is established.
                const qualityNow = Date.now();
                const qualityInterval = quality === 'adaptive' ? 2000 : 5000;
                const qualityChanged = lastQualityPolicyVideoId !== videoId || lastQualityPolicyMode !== quality;
                if (!qualityChanged && qualityNow - lastQualityPolicyAt < qualityInterval) return;
                lastQualityPolicyAt = qualityNow;
                lastQualityPolicyVideoId = videoId;
                lastQualityPolicyMode = quality;

                const rank = q => q === 'highres' ? 100000 : /^hd\d+$/.test(q) ? Number(q.slice(2)) :
                  ({large:480,medium:360,small:240,tiny:144})[q] || 0;
                const levels = typeof player.getAvailableQualityLevels === 'function' ? player.getAvailableQualityLevels() : [];
                const available = Array.isArray(levels) ? levels.filter(q => rank(q) > 0).sort((a,b) => rank(b)-rank(a)) : [];
                const setQuality = (target, adaptive) => {
                  // Initial metadata may arrive before the site's autoplay starts.
                  // Preserve a paused state only for an established source or explicit choice.
                  if (!video.paused || qualityPlaybackIntent?.id === videoId ||
                      (qualitySource?.id === videoId && qualitySource.player === player))
                    preserveQualityPlayback(player, video);
                  let applied = false;
                  if (typeof player.setPlaybackQualityRange === 'function') {
                    // Clear the old minimum first; otherwise a highest-quality floor can
                    // defeat a lower selection made by either the app or YouTube's menu.
                    player.setPlaybackQualityRange('auto', 'auto');
                    applied = true;
                  }
                  if (typeof player.setPlaybackQuality === 'function') {
                    player.setPlaybackQuality(target);
                    applied = true;
                  }
                  // Apply the final bounds after the preferred-quality setter, which
                  // may itself alter the range in some mobile player versions.
                  if (typeof player.setPlaybackQualityRange === 'function') {
                    player.setPlaybackQualityRange(adaptive ? (available.at(-1) || 'tiny') : target, target);
                  }
                  if (applied) qualitySource = {id:videoId, player};
                  return applied;
                };
                const now = Date.now();
                if (qualityAppliedVideoId === videoId && qualitySession && qualitySession.player === player && qualitySession.mode === quality) {
                  const session = qualitySession;
                  // A selection in the website's quality menu is also a user choice.
                  const preferred = typeof player.getPreferredQuality === 'function' ? player.getPreferredQuality() : null;
                  if (now-session.requestAt>=2000 && preferred && preferred !== session.target &&
                      (preferred === 'auto' || rank(preferred)>0)) {
                    const chosen = preferred === 'auto' ? 'adaptive' : preferred;
                    c.preferredQuality=chosen;
                    qualityAppliedVideoId=''; qualitySession=null;
                    if (typeof window.VideoShieldBridge?.onQualitySelected === 'function')
                      window.VideoShieldBridge.onQualitySelected(chosen);
                    return;
                  }
                  if (quality !== 'adaptive') return;
                  const position = Number(video.currentTime) || 0;
                  const gap = now-session.sampleAt;
                  const inactive = video.paused || video.ended || video.seeking || gap > 2500 || gap < 0;
                  const rate = Number(video.playbackRate)||1;
                  const advanced = position>session.position+0.05;
                  const jumped = Math.abs(position-session.position)>Math.max(2,gap/1000*rate+2);
                  let ahead = 0;
                  for (let i=0; video.buffered && i<video.buffered.length; i++) {
                    if (video.buffered.start(i)<=position && video.buffered.end(i)>=position) ahead=video.buffered.end(i)-position;
                  }
                  if (inactive || jumped || advanced) session.progressAt=now;
                  session.position=position; session.sampleAt=now;
                  if (inactive) { session.stableAt=0; return; }
                  const healthy = advanced && !jumped && video.readyState>=3 && ahead>=10*rate;
                  if (healthy) { if (!session.stableAt) session.stableAt=now; }
                  else session.stableAt=0;
                  if (video.readyState<=2 && ahead<1 && now-session.progressAt>=4000 && now-session.changeAt>=15000) {
                    const lower = available.find(q => rank(q)<rank(session.target));
                    if (lower && setQuality(lower,true)) { session.target=lower; session.changeAt=now; session.requestAt=now; session.progressAt=now; session.stableAt=0; }
                  } else if (session.stableAt && now-session.stableAt>=30000 && now-session.changeAt>=30000) {
                    const higher = available.filter(q => rank(q)>rank(session.target)).at(-1);
                    if (higher && setQuality(higher,true)) { session.target=higher; session.changeAt=now; session.requestAt=now; session.stableAt=0; }
                  }
                  return;
                }
                let applied = false;
                let targetQuality = quality;
                if (quality!=='auto' && typeof player.getAvailableQualityLevels==='function' && !available.length) return;
                if (quality === 'highres' || quality === 'adaptive') {
                  if (!available.length) return; // Wait until source qualities are ready.
                  targetQuality = available[0];
                } else if (quality !== 'auto' && available.length && !available.includes(quality)) {
                  targetQuality = available.find(q => rank(q)<=rank(quality)) || available.at(-1);
                }
                applied = setQuality(targetQuality, quality === 'adaptive');
                if (applied) {
                  qualityAppliedVideoId = videoId;
                  qualitySession = {player,mode:quality,target:targetQuality,manual:quality!=='adaptive'&&quality!=='auto',stableAt:0,
                    position:Number(video.currentTime)||0,sampleAt:now,progressAt:now,changeAt:now-15000,requestAt:now};
                }
              } catch (_) { internalErrors++; }
            }

            function communitySkippingEnabled() {
              const c = window.__videoShieldCfg || {};
              if (c.safeMode) return false;
              return !!c.communitySponsorSkip || !!c.skipIntrosOutros;
            }

            function handleCommunitySegments(video) {
              if (isBrowseShortsSurface() || !video || !communitySkippingEnabled()) return;
              const videoId = getVideoId();
              if (!videoId || videoId !== segmentVideoId || !Array.isArray(communitySegments) || !communitySegments.length) return;
              const now = Number(video.currentTime) || 0;

              // Segments arrive sorted by start time. Keep a rolling cursor instead of
              // scanning up to 256 entries on every playback sweep. A rewind resets the
              // cursor; normal forward playback touches only the next few candidates.
              if (now < lastSegmentPosition - 1) {
                skippedSegmentKeys.clear();
                segmentCursor = 0;
              }
              lastSegmentPosition = now;

              while (segmentCursor < communitySegments.length) {
                const candidate = communitySegments[segmentCursor] || {};
                const candidateEnd = Number(candidate.end);
                if (!Number.isFinite(candidateEnd) || candidateEnd <= now - 0.03) segmentCursor++;
                else break;
              }

              for (let i = segmentCursor; i < communitySegments.length; i++) {
                const segment = communitySegments[i] || {};
                const start = Number(segment.start);
                const end = Number(segment.end);
                if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) {
                  if (i === segmentCursor) segmentCursor++;
                  continue;
                }
                if (start > now + 0.08) break;
                const key = start.toFixed(3) + ':' + end.toFixed(3) + ':' + String(segment.category || '');
                if (skippedSegmentKeys.has(key)) continue;
                if (now >= Math.max(0, start - 0.08) && now < end - 0.03) {
                  try {
                    const duration = Number(video.duration);
                    video.currentTime = Number.isFinite(duration) && duration > 0
                      ? Math.min(end + 0.03, Math.max(0, duration - 0.05))
                      : end + 0.03;
                    skippedSegmentKeys.add(key);
                    segmentCursor = i + 1;
                    lastSegmentPosition = video.currentTime;
                    if (window.VideoShieldBridge) {
                      VideoShieldBridge.onSegmentSkipped(String(segment.category || '').slice(0, 40), Math.round((end - start) * 1000));
                    }
                  } catch (_) { internalErrors++; }
                  break;
                }
              }
            }

            function clickSkip() {
              if (!aggressiveBlockingAllowed() || !isPlayerAd()) return false;
              const activePlayer = playerForVideo(getPlayerVideo());
              const candidates = hasLegacyAdSignal() ? selectorsFor('skip').map(selector => {
                try { return activePlayer?.querySelector?.(selector) || null; } catch (_) { return null; }
              }) : [mobileSkipButton()];
              for (const button of candidates) {
                try {
                  if (button && !button.disabled && button.getAttribute('aria-disabled') !== 'true' &&
                      button.getClientRects().length > 0 && typeof button.click === 'function') {
                    const now = Date.now();
                    if (button === lastSkipButton && now - lastSkipAt < 1500) return false;
                    lastSkipButton = button;
                    lastSkipAt = now;
                    button.click();
                    sweepVideoCache = undefined;
                    mobileSkipCache = undefined; // The click may synchronously end the ad.
                    if (adState && !adState.counted) {
                      adState.counted = true;
                      try { if (window.VideoShieldBridge) VideoShieldBridge.onAdSkipped(); } catch (_) { internalErrors++; }
                    }
                    return true;
                  }
                } catch (_) { internalErrors++; }
              }
              return false;
            }

            function restorePlayerState(video) {
              if (!adState) return;
              try {
                const target = adState.video;
                target.playbackRate = adState.playbackRate || 1;
                target.muted = !!adState.muted;
                target.loop = !!adState.loop;
              } catch (_) { internalErrors++; }
              adState = null;
            }

            function hasLegacyAdSignal() {
              const player = playerForVideo(getPlayerVideo());
              return !!(player && player.classList &&
                (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting')));
            }

            function mobileSkipButton() {
              if (scanningSweep && mobileSkipCache !== undefined) return mobileSkipCache;
              const now = Date.now();
              const minInterval = isBrowseShortsSurface() ? 1200 : 650;
              if (now - lastMobileSkipScanAt < minInterval) {
                const cached = lastMobileSkipResult;
                return cached && cached.isConnected !== false ? cached : null;
              }
              const button = findMobileSkipButton();
              lastMobileSkipScanAt = now;
              lastMobileSkipResult = button;
              if (scanningSweep) mobileSkipCache = button;
              return button;
            }

            function findMobileSkipButton() {
              // Mobile experiments use overlay buttons without .ad-showing.
              // Accept only an explicit ad-skip label inside the visible video area.
              const video = getPlayerVideo();
              if (!video || typeof video.getBoundingClientRect !== 'function') return null;
              const videoRect = video.getBoundingClientRect();
              if (videoRect.width <= 0 || videoRect.height <= 0) return null;
              const buttons = document.querySelectorAll('button,[role="button"]');
              for (const button of Array.from(buttons).slice(0, 100)) {
                if (button.disabled || button.getAttribute('aria-disabled') === 'true' || !button.getClientRects().length) continue;
                const label = String(button.getAttribute('aria-label') || button.textContent || '').trim().replace(/\s+/g,' ');
                if (!/^(skip ads?|bỏ qua(?: quảng cáo)?)(?:\s*[›»▶⏭])?$/i.test(label)) continue;
                const r = button.getBoundingClientRect();
                const x=(r.left+r.right)/2, y=(r.top+r.bottom)/2;
                if (r.width>0 && r.height>0 && x>=videoRect.left && x<=videoRect.right && y>=videoRect.top && y<=videoRect.bottom) return button;
              }
              return null;
            }

            function isPlayerAd() {
              return hasLegacyAdSignal() || !!mobileSkipButton();
            }

            function handlePlayerAd() {
              const video = getPlayerVideo();
              const player = playerForVideo(video);
              if (!aggressiveBlockingAllowed()) {
                restorePlayerState(video);
                return;
              }
              const showingAd = isPlayerAd();

              if (showingAd && video) {
                if (adState && adState.video !== video) restorePlayerState(video);
                if (!adState) adState = { video: video, muted: video.muted, playbackRate: video.playbackRate, loop: video.loop, counted: false };
                clickSkip();
                // A click can switch back to content synchronously. Never seek that content to its end.
                if (!isPlayerAd()) { restorePlayerState(video); return; }
                // A localized mobile skip button is sufficient to click, but never to
                // seek the media: some mobile layouts share the content video element.
                if (!hasLegacyAdSignal()) {
                  const episode=adState;
                  restorePlayerState(video);
                  adState=episode;
                  return;
                }
                try {
                  video.muted = true;
                  video.loop = false;
                  video.playbackRate = 16;
                  const duration = Number(video.duration);
                  if (Number.isFinite(duration) && duration > 0.5 && video.currentTime < duration - 0.35) {
                    video.currentTime = Math.max(video.currentTime, duration - 0.25);
                  }
                } catch (_) { internalErrors++; }
              } else {
                restorePlayerState(video);
                lastSkipButton = null;
                lastSkipAt = 0;
              }
            }

            function getChannelInfo() {
              const candidates = [
                'ytm-slim-owner-renderer a',
                'ytd-channel-name a', '#owner #channel-name a'
              ];
              for (const s of candidates) {
                try {
                  const el = document.querySelector(s);
                  if (el && el.textContent) {
                    const href = el.href || el.getAttribute('href') || '';
                    return { name: el.textContent.trim(), url: href ? new URL(href, location.href).href : '' };
                  }
                } catch (_) { internalErrors++; }
              }
              try {
                const el = document.querySelector('ytm-slim-owner-renderer .slim-owner-channel-name');
                if (el && el.textContent) return { name: el.textContent.trim(), url: '' };
              } catch (_) { internalErrors++; }
              return { name: '', url: '' };
            }

            function getChannelInfoCached(videoId, force = false) {
              const now = Date.now();
              if (!force && videoId && videoId === channelInfoVideoId && now - channelInfoAt < 30000)
                return cachedChannelInfo;
              const info = getChannelInfo();
              if (videoId) channelInfoVideoId = videoId;
              channelInfoAt = now;
              // Keep known metadata through a transient YouTube re-render where the owner
              // element is briefly absent; refresh again on the next video/navigation.
              if (info.name || !cachedChannelInfo.name || force) cachedChannelInfo = info;
              return cachedChannelInfo;
            }

            function getVideoId() {
              try {
                const u = new URL(location.href);
                if (u.pathname.startsWith('/shorts/')) return u.pathname.split('/')[2] || '';
                return u.searchParams.get('v') || '';
              } catch (_) { return ''; }
            }

            function clearPlaybackHeartbeat() {
              try { if (playbackHeartbeatTimer) clearTimeout(playbackHeartbeatTimer); } catch (_) {}
              playbackHeartbeatTimer = null;
            }

            function schedulePlaybackHeartbeat(playing) {
              clearPlaybackHeartbeat();
              if (!playing || typeof window.VideoShieldBridge !== 'object' || isBrowseShortsSurface()) return;
              // PlaybackRecoveryController times out at 28 s. One event-driven heartbeat at
              // 18-20 s keeps a wide safety margin while avoiding bridge work on every shield sweep.
              const delay = window.__videoShieldPowerConstrained ? 20000 : 18000;
              playbackHeartbeatTimer = setTimeout(() => {
                playbackHeartbeatTimer = null;
                playbackHeartbeatFires++;
                updatePlaybackBridge(true);
              }, delay);
            }

            function clearMetadataBridgeRetry() {
              try { if (metadataBridgeRetryTimer) clearTimeout(metadataBridgeRetryTimer); } catch (_) {}
              metadataBridgeRetryTimer = null;
            }

            function scheduleMetadataBridgeRetry(videoId, complete) {
              const id = String(videoId || '');
              if (complete) {
                clearMetadataBridgeRetry();
                metadataBridgeRetryVideoId = id;
                metadataBridgeRetryCount = 0;
                return;
              }
              if (metadataBridgeRetryVideoId !== id) {
                clearMetadataBridgeRetry();
                metadataBridgeRetryVideoId = id;
                metadataBridgeRetryCount = 0;
              }
              if (metadataBridgeRetryTimer || metadataBridgeRetryCount >= 4) return;
              const delays = [2500, 3500, 5000, 7500];
              const delay = delays[Math.min(metadataBridgeRetryCount, delays.length - 1)];
              metadataBridgeRetryCount++;
              metadataBridgeRetryTimer = setTimeout(() => {
                metadataBridgeRetryTimer = null;
                updatePlaybackBridge(true);
              }, delay);
            }

            function updatePlaybackBridge(force = false) {
              // The browse Shorts feed owns its own lifecycle. Reporting/repeat/queue logic here
              // would mistake adjacent Shorts media for one dedicated playback session.
              if (isBrowseShortsSurface()) return;
              // Ad media time must never overwrite the content resume position or advance the queue.
              if (isPlayerAd()) return;
              const now = Date.now();
              if (!force && now - lastBridgeUpdate < 450) return;
              lastBridgeUpdate = now;
              const video = getPlayerVideo();
              // An unloaded placeholder must not replace the saved content position with zero.
              if (!video || video.readyState < 1) return;
              if (!video.ended) lastEndedVideoId = '';

              // Native playback state follows user/media intent, not decode readiness. During
              // a normal network stall readyState can temporarily fall below HAVE_FUTURE_DATA;
              // reporting that as paused made the mini player, MediaSession and wake policy
              // flap even though the media element was still actively trying to play.
              const playing = !video.paused && !video.ended;
              const buffering = playing && (video.readyState <= 2 || bufferingVideo === video);
              const position = Number(video.currentTime);
              const periodic = playing && now - lastPlaybackReportAt >= 18000;
              const pausedSeek = !playing && Number.isFinite(position) && Math.abs(position - lastReportedPosition) >= 0.25;
              const stateChanged = playing !== lastPlaying;
              const bufferingChanged = buffering !== lastBuffering;
              const href = String(location.href || '');
              const routeChanged = href !== lastBridgeHref;
              const metadataRetry = (!lastTitle || !lastChannel || !lastVideoId) && now - lastMetadataRetryAt >= 2500;

              // Most sweeps only need ad/media policy. Do not repeatedly query document
              // title, URL parsing and channel DOM while the playing state is unchanged.
              if (!stateChanged && !bufferingChanged && !periodic && !pausedSeek && !routeChanged && !metadataRetry && !video.ended) return;

              if (routeChanged) lastBridgeHref = href;
              if (metadataRetry) lastMetadataRetryAt = now;
              const videoId = (routeChanged || !lastVideoId) ? getVideoId() : lastVideoId;
              const videoChanged = videoId !== lastVideoId;
              const title = (document.title || '').replace(/\s*-\s*YouTube\s*$/, '').trim();
              const info = getChannelInfoCached(videoId, videoChanged || metadataRetry);
              const channel = info.name || '';
              if (videoChanged) lastEndedVideoId = "";

              if (stateChanged || bufferingChanged || title !== lastTitle || channel !== lastChannel || videoChanged || periodic || pausedSeek) {
                lastPlaying = playing;
                lastBuffering = buffering;
                lastTitle = title;
                lastChannel = channel;
                lastVideoId = videoId;
                lastPlaybackReportAt = now;
                lastReportedPosition = Number.isFinite(position) ? Math.max(0, position) : 0;
                const duration = Number(video.duration);
                const positionMs = Number.isFinite(position) ? Math.max(0, Math.round(position * 1000)) : 0;
                const durationMs = Number.isFinite(duration) ? Math.max(0, Math.round(duration * 1000)) : 0;
                try {
                  if (window.VideoShieldBridge) {
                    playbackBridgeReports++;
                    VideoShieldBridge.onPlaybackState(playing, buffering, title, channel, info.url || '', videoId, positionMs, durationMs);
                  }
                } catch (_) { internalErrors++; }
                schedulePlaybackHeartbeat(playing);
                scheduleMetadataBridgeRetry(videoId, !!(title && channel && videoId));
              }

              if (video.ended) {
                const showingAd = isPlayerAd();
                const c = window.__videoShieldCfg || {};
                if (c.autoRepeat && videoId && !showingAd) {
                  restartRepeatedVideo(video);
                } else if (videoId && !showingAd && lastEndedVideoId !== videoId) {
                  lastEndedVideoId = videoId;
                  try { if (window.VideoShieldBridge) VideoShieldBridge.onPlaybackEnded(videoId); } catch (_) { internalErrors++; }
                }
              }
            }

            function compatibilityReport() {
              try {
                const href = String(location.href || '');
                if (href === lastCompatibilityUrl) return;
                lastCompatibilityUrl = href;
                const playerFound = !!document.querySelector('.html5-video-player');
                const videoFound = !!getPlayerVideo();
                const version = Number((window.__videoShieldRules || {}).version || 0);
                if (window.VideoShieldBridge) {
                  VideoShieldBridge.onCompatibilityReport(playerFound, videoFound, internalErrors, version);
                }
              } catch (_) { internalErrors++; }
            }

            function scheduleCompatibility() {
              try {
                if (compatibilityTimer) clearTimeout(compatibilityTimer);
                compatibilityTimer = setTimeout(compatibilityReport, 7000);
              } catch (_) { internalErrors++; }
            }

            function useSystemAudio(video) {
              // Only the native playback WebView has this bridge. Browse previews stay quiet.
              if (!video || typeof window.VideoShieldBridge !== 'object' || isPlayerAd()) return;
              try {
                // The normal steady state is already unmuted at full media volume. Skip
                // querying YouTube's player object entirely in that hot path.
                if (audioBoundVideo === video && !video.muted && Math.abs((Number(video.volume) || 0) - 1) < 0.001) return;
                audioBoundVideo = video;
                const player = video.closest?.('.html5-video-player') || document.querySelector('.html5-video-player');
                if (player && typeof player.isMuted === 'function' && player.isMuted() && typeof player.unMute === 'function') player.unMute();
                if (video.muted) video.muted = false;
                if (Math.abs((Number(video.volume) || 0) - 1) >= 0.001) video.volume = 1;
              } catch (_) { internalErrors++; }
            }

            function cancelFullDomMaintenance() {
              try {
                if (fullDomIdleHandle !== null && typeof cancelIdleCallback === 'function') cancelIdleCallback(fullDomIdleHandle);
              } catch (_) {}
              try { if (fullDomFallbackTimer) clearTimeout(fullDomFallbackTimer); } catch (_) {}
              fullDomIdleHandle = null;
              fullDomFallbackTimer = null;
              fullDomMaintenancePending = false;
            }

            function runFullDomMaintenance() {
              fullDomIdleHandle = null;
              fullDomFallbackTimer = null;
              if (!fullDomMaintenancePending) return;
              fullDomMaintenancePending = false;
              try {
                hidePageAds();
                hideAnnoyances();
                applyAmoledTheme();
                lastFullDomSweepAt = Date.now();
                fullDomMaintenanceRuns++;
              } catch (_) { internalErrors++; }
            }

            function scheduleFullDomMaintenance(timeoutMs = 1200) {
              if (fullDomMaintenancePending) { fullDomMaintenanceCoalesced++; return; }
              fullDomMaintenancePending = true;
              const hidden = isActuallyHidden();
              const constrained = !!window.__videoShieldPowerConstrained;
              const timeout = Math.max(250, Number(timeoutMs) || 1200);
              try {
                if (typeof requestIdleCallback === 'function' && !hidden) {
                  fullDomIdleHandle = requestIdleCallback(() => runFullDomMaintenance(), {
                    timeout: constrained ? Math.max(timeout, 1800) : timeout
                  });
                  return;
                }
              } catch (_) {}
              // Hidden documents and old WebView builds do not reliably deliver idle callbacks.
              // One bounded fallback still keeps this work away from the media/event callback.
              const fallbackDelay = hidden ? (constrained ? 900 : 650) : (constrained ? 420 : 260);
              fullDomFallbackTimer = setTimeout(() => runFullDomMaintenance(), fallbackDelay);
            }

            function scheduleFallbackSweep() {
              try {
                if (fallbackSweepTimer) clearTimeout(fallbackSweepTimer);
                const video = getPlayerVideo();
                const playing = !!(video && !video.paused && !video.ended);
                const hidden = isActuallyHidden();
                const constrained = !!window.__videoShieldPowerConstrained;
                const nativePlayer = typeof window.VideoShieldBridge === 'object';
                const hasPrecisionSegments = nativePlayer && Array.isArray(communitySegments) && communitySegments.length > 0;
                // Critical media/ad state is observer/event driven. This fallback is only a
                // safety net, so quiet native playback can run at a very low wake-up rate.
                // Community segments keep the tighter cadence because their skip boundary is
                // time-sensitive.
                const delay = isBrowseShortsSurface()
                  ? (hidden ? (constrained ? 30000 : 22000) : (constrained ? 12000 : 8000))
                  : nativePlayer
                    ? (playing
                        ? (hasPrecisionSegments ? 1500 : (hidden ? (constrained ? 22000 : 16000) : (constrained ? 12000 : 9000)))
                        : (hidden ? (constrained ? 32000 : 24000) : (constrained ? 18000 : 12000)))
                    : (playing
                        ? (hidden ? (constrained ? 14000 : 9000) : (constrained ? 6500 : 4200))
                        : (hidden ? (constrained ? 32000 : 18000) : (constrained ? 12000 : 7000)));
                fallbackSweepTimer = setTimeout(() => {
                  fallbackSweepTimer = null;
                  const fullInterval = isBrowseShortsSurface() ? (hidden ? 60000 : 22000) :
                    nativePlayer ? (hidden ? 45000 : (constrained ? 26000 : 18000)) :
                    (hidden ? 30000 : (constrained ? 16000 : 10000));
                  const fullDom = Date.now() - lastFullDomSweepAt >= fullInterval;
                  sweep(fullDom);
                }, delay);
              } catch (_) { internalErrors++; }
            }

            function scheduleObservedSweep() {
              if (observedSweepTimer) return;
              const elapsed = Date.now() - lastSweepAt;
              const constrained = !!window.__videoShieldPowerConstrained;
              const nativePlayer = typeof window.VideoShieldBridge === 'object';
              const hasPrecisionSegments = nativePlayer && Array.isArray(communitySegments) && communitySegments.length > 0;
              const floor = isBrowseShortsSurface() ? (constrained ? 2600 : 1900) :
                (nativePlayer ? (hasPrecisionSegments ? 900 : (constrained ? 3200 : 2200)) : (constrained ? 1500 : 900));
              const delay = Math.max(0, floor - elapsed);
              observedSweepTimer = setTimeout(() => {
                observedSweepTimer = null;
                // DOM mutations are frequent, especially in Shorts. Keep this pass to
                // player/media state; the slower fallback owns whole-page ad/annoyance scans.
                const fullDomInterval = nativePlayer ? (constrained ? 26000 : 18000) : (constrained ? 7000 : 5000);
                const fullDom = !isBrowseShortsSurface() && Date.now() - lastFullDomSweepAt >= fullDomInterval;
                sweep(fullDom);
              }, delay);
            }

            function compactSessionReferences() {
              try {
                const video = resolvePlayerVideo();
                const player = video ? playerForVideo(video) : null;
                const videoId = getVideoId();

                // Event listeners/observers deliberately retain their bound element. Unbind only
                // when that element is no longer the current player; the next sweep rebinds once.
                if (preloadBoundVideo && preloadBoundVideo !== video) releasePreloadPolicy();
                if (rateBoundVideo && rateBoundVideo !== video) bindPlaybackRateVideo(null);
                if (repeatBoundVideo && repeatBoundVideo !== video) bindRepeatVideo(null);
                if (bufferingVideo && bufferingVideo !== video) clearBufferRecovery();

                if (audioBoundVideo && audioBoundVideo !== video) audioBoundVideo = null;
                if (ratePlayerApiVideo && ratePlayerApiVideo !== video) {
                  ratePlayerApiVideo = null;
                  ratePlayerApiValue = NaN;
                }
                if (adState?.video && adState.video !== video) adState = null;
                if (lastSkipButton && lastSkipButton.isConnected === false) lastSkipButton = null;
                if (lastMobileSkipResult && lastMobileSkipResult.isConnected === false) lastMobileSkipResult = null;

                // Quality state stores YouTube player DOM/API objects. A navigation must not keep
                // the previous player subtree reachable for the lifetime of a long watch session.
                if (qualitySource?.player && qualitySource.player !== player) qualitySource = null;
                if (qualitySession?.player && qualitySession.player !== player) {
                  qualitySession = null;
                  qualityAppliedVideoId = '';
                }
                if (qualityTransition?.player && qualityTransition.player !== player) cancelQualityPlaybackRestore();
                if (qualityPlaybackIntent?.id && qualityPlaybackIntent.id !== videoId) qualityPlaybackIntent = null;

                // Segment payloads are bounded, but keeping the previous video's array until the
                // async community lookup returns wastes memory and can inspect stale ranges.
                if (segmentVideoId && segmentVideoId !== videoId) {
                  segmentVideoId = '';
                  communitySegments = [];
                  skippedSegmentKeys = new Set();
                  segmentCursor = 0;
                  lastSegmentPosition = 0;
                }

                if (!isShortsRoute()) shortsActiveVideo = null;
                sweepVideoCache = undefined;
                mobileSkipCache = undefined;
                sessionCompactions++;
                return sessionCompactions;
              } catch (_) { internalErrors++; return sessionCompactions; }
            }

            function sweep(fullDom = true) {
              try {
                scanningSweep = true;
                sweepVideoCache = undefined;
                mobileSkipCache = undefined;
                // Whole-page cosmetic work is intentionally detached from the latency-sensitive
                // media pass. requestIdleCallback coalesces repeated mutation/fallback requests
                // and runs the DOM-heavy selectors when Chromium has frame budget available.
                if (fullDom) scheduleFullDomMaintenance(isActuallyHidden() ? 2400 : 1200);
                handlePlayerAd();
                const video = getPlayerVideo();
                if (!isPlayerAd()) {
                  useSystemAudio(video);
                  applyPlaybackEnhancements(video);
                  handleCommunitySegments(video);
                }
                // Playback reporting is event-driven. The native player only needs one
                // initial sample here; play/pause/buffer/seek/navigation events plus the
                // one-shot heartbeat own subsequent bridge traffic.
                if (typeof window.VideoShieldBridge === 'object' && lastPlaying === null) updatePlaybackBridge(true);
                if (window.__voTuibeInstallWatchActions) window.__voTuibeInstallWatchActions();
                else if (window.__voTuibeInstallDownload) window.__voTuibeInstallDownload();
              } catch (_) { internalErrors++; }
              finally {
                scanningSweep = false;
                sweepVideoCache = undefined;
                mobileSkipCache = undefined;
                lastSweepAt = Date.now();
                scheduleFallbackSweep();
              }
            }

            window.__videoShieldCompactSession = compactSessionReferences;
            window.__videoShieldSetMediaRetention = (mode, compact = false) => {
              const normalized = ['active','warm','lean','cold'].includes(String(mode || '').toLowerCase())
                ? String(mode).toLowerCase() : 'active';
              window.__videoShieldMediaRetentionMode = normalized;
              const video = getPlayerVideo();
              if (normalized !== 'active') clearBufferRecovery(video);
              if (compact) compactSessionReferences();
              if (video) applyPreloadPolicy(video);
              return {mode: normalized, paused: !!video?.paused, preload: video?.preload || ''};
            };
            window.__videoShieldSweep = sweep;
            window.__videoShieldDiagnostics = () => {
              const media = getPlayerVideo();
              return { errors: internalErrors, playing: lastPlaying,
                videoId: lastVideoId, ad: isPlayerAd(), bridge: typeof window.VideoShieldBridge,
                repeat: !!(window.__videoShieldCfg || {}).autoRepeat, mediaLoop: !!media?.loop,
                configuredRate: configuredPlaybackRate(), mediaRate: Number(media?.playbackRate || 1),
                requestedRate: lastRequestedRate, readyState: Number(media?.readyState || 0),
                mediaRetention: mediaRetentionMode(), mediaPreload: media?.preload || '',
                bufferAhead: bufferedAheadSeconds(media),
                bufferingMs: bufferingSince > 0 ? Math.max(0, Date.now() - bufferingSince) : 0,
                bridgeReports: playbackBridgeReports, heartbeatFires: playbackHeartbeatFires,
                fullDomRuns: fullDomMaintenanceRuns, fullDomCoalesced: fullDomMaintenanceCoalesced,
                fullDomPending: !!fullDomMaintenancePending, sessionCompactions };
            };
            window.__videoShieldScheduleCompatibility = scheduleCompatibility;
            window.__videoShieldSyncPlayerState = () => {
              const video = getPlayerVideo();
              if (!video || video.paused || video.ended || video.readyState < 3 || isPlayerAd()) return false;
              const player = playerForVideo(video);
              try {
                // A resize Pause can be blocked below while YouTube still changes
                // its own state to PAUSED. playVideo() then sees already-playing
                // media and may never emit the event needed to repair its controls.
                if (typeof player?.getPlayerState !== 'function' || player.getPlayerState() !== 2) return false;
                // Report the real running media state without pause/play, seeking,
                // load(), or rebuilding its buffered MediaSource.
                video.dispatchEvent(new Event('playing'));
                return true;
              } catch (_) { internalErrors++; return false; }
            };
            window.__videoShieldControl = (cmd) => {
              if (cmd === 'pause' || cmd === 'toggle' || cmd === 'play') cancelQualityPlaybackRestore();
              if(cmd==='pause' || cmd==='toggle') window.__videoShieldExpandPlaybackWanted=false;
              if (cmd === 'pause' || cmd === 'toggle') window.__videoShieldPipResumePending = false;
              if (cmd === 'pause') window.__videoShieldPipPlaybackWanted = false;
              if (cmd === 'pause') window.__videoShieldMiniPlaybackWanted = false;
              const video = getPlayerVideo();
              if (!video) return false;
              try {
                if (cmd === 'toggle') window.__videoShieldPipPlaybackWanted = !!video.paused;
                if (cmd === 'toggle') window.__videoShieldMiniPlaybackWanted = !!video.paused;
                if (cmd === 'play' && document.getElementById('youtoobee-mini-style')) window.__videoShieldMiniPlaybackWanted = true;
                if (cmd === 'play' && document.getElementById('youtoobee-pip-style')) window.__videoShieldPipPlaybackWanted = true;
                if (cmd === 'play') video.play();
                else if (cmd === 'pause') video.pause();
                else if (cmd === 'toggle') video.paused ? video.play() : video.pause();
                else if (cmd === 'seekBack') video.currentTime = Math.max(0, video.currentTime - 10);
                else if (cmd === 'seekForward') video.currentTime = Math.min(video.duration || video.currentTime + 10, video.currentTime + 10);
                return true;
              } catch (_) { internalErrors++; return false; }
            };
            window.__videoShieldSetPosition = (seconds) => {
              const video = getPlayerVideo();
              if (!video) return false;
              try {
                const target = Math.max(0, Number(seconds) || 0);
                const duration = Number(video.duration);
                video.currentTime = Number.isFinite(duration) && duration > 0 ? Math.min(target, Math.max(0, duration - 0.25)) : target;
                return true;
              } catch (_) { internalErrors++; return false; }
            };
            window.__videoShieldSetRate = (rate) => {
              const target = Math.min(4, Math.max(0.25, Number(rate) || 1));
              try {
                if (!window.__videoShieldCfg) window.__videoShieldCfg = {};
                window.__videoShieldCfg.playbackSpeed = target;
                lastRequestedRate = target;
                rateUiInteractionUntil = 0;
                const video = getPlayerVideo();
                if (!video) return false;
                const applied = applyPlaybackRatePolicy(video);
                schedulePlaybackRateRestore(video);
                // YouTube can replace the media source after the first successful
                // read-back without emitting a useful ratechange on the old element.
                // Re-check a few sparse checkpoints; these are one-shot, not polling.
                for (const delay of [240, 700, 1500]) {
                  setTimeout(() => {
                    const media = getPlayerVideo();
                    if (media && !isPlayerAd() && Math.abs(configuredPlaybackRate() - target) <= 0.01)
                      applyPlaybackRatePolicy(media);
                  }, delay);
                }
                return applied;
              } catch (_) { internalErrors++; return false; }
            };
            window.__videoShieldSetSegments = (videoId, segments) => {
              try {
                segmentVideoId = String(videoId || '').slice(0, 64);
                communitySegments = Array.isArray(segments)
                  ? segments.slice(0, 256).sort((a,b) => (Number(a?.start)||0) - (Number(b?.start)||0))
                  : [];
                skippedSegmentKeys = new Set();
                segmentCursor = 0;
                lastSegmentPosition = 0;
                return true;
              } catch (_) { internalErrors++; return false; }
            };
            window.__videoShieldSetRepeat = (enabled) => {
              try {
                if (!window.__videoShieldCfg) window.__videoShieldCfg = {};
                window.__videoShieldCfg.autoRepeat = !!enabled;
                repeatLastReportedState = !!enabled;
                applyRepeatPolicy(getPlayerVideo(), true);
                if (enabled) lastEndedVideoId = '';
                return true;
              } catch (_) { internalErrors++; return false; }
            };

            window.__videoShieldPreferencesChanged = () => {
              const media = getPlayerVideo();
              applyPreloadPolicy(media);
              applyRepeatPolicy(media, true);
              applyPlaybackRatePolicy(media);
              if (media && !isPlayerAd()) schedulePlaybackRateRestore(media);
              // Unrelated settings must not reset a user's quality or adaptive downgrade.
              if (qualitySession && qualitySession.mode !== String((window.__videoShieldCfg || {}).preferredQuality || 'auto')) {
                qualityAppliedVideoId = ''; qualitySession = null;
              }
            };
            window.__videoShieldSetPreferredQuality = (mode) => {
              try {
                const allowed=['adaptive','auto','highres','hd2160','hd1440','hd1080','hd720','large','medium','small','tiny'];
                const next=String(mode || 'adaptive');
                if(!allowed.includes(next)) return false;
                if(!window.__videoShieldCfg) window.__videoShieldCfg={};
                const changed=String(window.__videoShieldCfg.preferredQuality || 'auto') !== next;
                window.__videoShieldCfg.preferredQuality=next;
                if(changed) {
                  cancelQualityPlaybackRestore();
                  qualityAppliedVideoId=''; qualitySession=null; qualitySource=null;
                  lastQualityPolicyAt=0; lastQualityPolicyVideoId=''; lastQualityPolicyMode='';
                }
                // A lightweight media/player sweep is enough; no DOM-wide ad scan and no
                // reparsing of the full policy bundle is needed for a network profile flip.
                sweep(false);
                return true;
              } catch (_) { internalErrors++; return false; }
            };
            window.__videoShieldResetQuality = () => { qualityAppliedVideoId=''; qualitySession=null; sweep(); };
            window.__videoShieldQualityState = () => qualitySession ? {
              mode:qualitySession.mode,target:qualitySession.target,manual:qualitySession.manual
            } : null;

            window.__videoShieldSetPowerConstrained = (enabled) => {
              try {
                const next = !!enabled;
                if (window.__videoShieldPowerConstrained === next) return false;
                window.__videoShieldPowerConstrained = next;
                scheduleFallbackSweep();
                return true;
              } catch (_) { internalErrors++; return false; }
            };

            function startObserver() {
              if (!document.documentElement || window.__videoShieldObserver) return;
              let shortsMode = isBrowseShortsSurface();
              const observer = new MutationObserver(() => scheduleObservedSweep());
              const observe = () => {
                try {
                  observer.disconnect();
                  shortsMode = isBrowseShortsSurface();
                  // The Shorts recycler and the dedicated watch page both mutate class/aria
                  // state throughout large subtrees. Observe structural changes globally, but
                  // on the native player restrict attribute watching to the player element
                  // where ad state actually changes. Browse pages keep the richer filter.
                  const nativePlayer = typeof window.VideoShieldBridge === 'object';
                  if (shortsMode) {
                    observer.observe(document.documentElement, {subtree:true, childList:true});
                  } else if (nativePlayer) {
                    const player = document.querySelector('.html5-video-player');
                    if (player) {
                      // Watch pages mutate comments/related content continuously. Only the player
                      // subtree is latency-sensitive for ad/media policy; fallback sweeps still
                      // cover whole-page cosmetic rules at a much lower cadence.
                      observer.observe(player, {
                        subtree:true, childList:true, attributes:true,
                        attributeFilter:['class','hidden','aria-hidden']
                      });
                    } else {
                      // Before the player is mounted, watch structural DOM changes just long
                      // enough to discover it. yt-navigate-finish rebinds the observer as well.
                      observer.observe(document.documentElement, {subtree:true, childList:true});
                    }
                  } else {
                    observer.observe(document.documentElement, {
                      subtree:true, childList:true, attributes:true,
                      attributeFilter:['class','hidden','aria-hidden']
                    });
                  }
                } catch (_) { internalErrors++; }
              };
              const syncMode = () => {
                if (shortsMode !== isBrowseShortsSurface()) observe();
                // Visibility changes are a natural scheduling boundary. Drop an old pending
                // idle callback so a hidden page cannot wake later with stale full-DOM work.
                cancelFullDomMaintenance();
                scheduleObservedSweep();
                scheduleFallbackSweep();
                if (!isActuallyHidden()) scheduleFullDomMaintenance(900);
              };
              const syncNavigation = () => {
                // SPA navigation can replace the player node without changing route mode.
                // Rebind to the new subtree instead of leaving the observer on detached DOM.
                observe();
                scheduleObservedSweep();
                scheduleFallbackSweep();
                scheduleFullDomMaintenance(850);
              };
              observe();
              window.__videoShieldObserver = observer;
              document.addEventListener('visibilitychange', syncMode, true);
              document.addEventListener('yt-navigate-finish', syncNavigation, true);
              window.addEventListener('popstate', syncNavigation, {passive:true});
            }

            function releaseUserPlaybackProtection() {
              window.__videoShieldExpandPlaybackWanted = false;
              window.__videoShieldExpandPlaybackUntil = 0;
              window.__videoShieldPipResumePending = false;
              window.__videoShieldPipPlaybackWanted = false;
              window.__videoShieldMiniPlaybackWanted = false;
              cancelQualityPlaybackRestore();
            }

            function isWebsitePlaybackControl(target) {
              try {
                if (target?.closest?.('.ytp-play-button,.ytp-large-play-button,.player-control-play-pause-icon')) return true;
                const button = target?.closest?.('button,[role="button"]') || target;
                const label = String(button?.getAttribute?.('aria-label') || '').trim();
                return /^(play|pause|phát|tạm dừng|tiếp tục phát)(?:\s|$)/i.test(label);
              } catch (_) { return false; }
            }

            // The mobile player pauses on small viewports. In native PiP only, preserve the
            // explicit playback intent; native Pause clears it before calling video.pause().
            if (typeof HTMLMediaElement !== 'undefined') {
              const nativePause = HTMLMediaElement.prototype.pause;
              HTMLMediaElement.prototype.pause = function() {
                const playerVideo = getPlayerVideo();
                const keepPlaying = (window.__videoShieldPipPlaybackWanted && document.getElementById('youtoobee-pip-style')) ||
                    (window.__videoShieldMiniPlaybackWanted && document.getElementById('youtoobee-mini-style')) ||
                    (window.__videoShieldExpandPlaybackWanted &&
                      Date.now() < Number(window.__videoShieldExpandPlaybackUntil || 0) &&
                      document.documentElement.getAttribute('data-votuibe-surface')==='expanded');
                if (this === playerVideo && keepPlaying && !this.ended && !isPlayerAd()) return;
                return nativePause.call(this);
              };
            }

            document.addEventListener('loadstart', event => {
              if (event.target === getPlayerVideo()) applyPreloadPolicy(event.target);
            }, true);
            document.addEventListener('play', event => {
              if (isShortsRoute() && event.target instanceof HTMLVideoElement) shortsActiveVideo = event.target;
              if (event.target === getPlayerVideo()) updatePlaybackBridge(true);
            }, true);
            for (const eventName of ['seeked', 'seeking']) {
              document.addEventListener(eventName, event => {
                if (event.target === getPlayerVideo()) updatePlaybackBridge(true);
              }, true);
            }
            for(const eventName of ['pointerdown','touchstart']) document.addEventListener(eventName,event=>{
              window.__videoShieldExpandPlaybackWanted=false;
              window.__videoShieldExpandPlaybackUntil=0;
              if (isWebsitePlaybackControl(event.target)) releaseUserPlaybackProtection();
              const repeatItem = repeatMenuItem(event.target);
              if (repeatItem) repeatUiInteractionUntil = Date.now() + 1200;
              const rateItem = playbackRateMenuItem(event.target);
              const rateOption = playbackRateOptionValue(event.target);
              if (rateItem || rateOption !== null) {
                rateUiInteractionUntil = Date.now() + 5000;
                cancelPlaybackRateRestore();
              }
              const menu = event.target?.closest?.('.ytp-settings-button,.ytp-menuitem,[role="menuitem"],[role="menuitemradio"],[role="menuitemcheckbox"]');
              if (menu) {
                if (!qualityPlaybackIntent || qualityPlaybackIntent.id !== getVideoId())
                  window.__videoShieldPrepareQualityChange();
              } else cancelQualityPlaybackRestore();
            },true);
            document.addEventListener('click', event => {
              // Accessibility and some mobile controls emit click without pointerdown.
              if (isWebsitePlaybackControl(event.target)) releaseUserPlaybackProtection();
              const repeatItem = repeatMenuItem(event.target);
              if (repeatItem) beginWebsiteRepeatInteraction(repeatItem);

              const rateItem = playbackRateMenuItem(event.target);
              const rateOption = playbackRateOptionValue(event.target);
              if (rateItem || rateOption !== null) {
                beginWebsitePlaybackRateInteraction(event.target);
              } else if (Date.now() < rateUiInteractionUntil) {
                // Some YouTube builds render rate options as generic components with no
                // semantic role. Read back the actual player rate after the click.
                setTimeout(() => settleWebsitePlaybackRate(null), 80);
                setTimeout(() => settleWebsitePlaybackRate(null), 320);
              }
            }, true);
            document.addEventListener('keydown', event => {
              cancelQualityPlaybackRestore();
              const key = String(event.key || '').toLowerCase();
              if ((key === ' ' || key === 'k' || key === 'mediaplaypause' ||
                    (key === 'enter' && isWebsitePlaybackControl(event.target))) &&
                  !event.target?.closest?.('input,textarea,[contenteditable="true"]'))
                releaseUserPlaybackProtection();
            }, true);
            for (const eventName of ['loadedmetadata', 'durationchange', 'canplay', 'playing', 'play', 'volumechange']) {
              document.addEventListener(eventName, event => {
                if (event.target === getPlayerVideo()) {
                  applyPreloadPolicy(event.target);
                  applyRepeatPolicy(event.target);
                  if (eventName !== 'volumechange') applyPlaybackRatePolicy(event.target);
                  useSystemAudio(event.target);
                  if (eventName === 'canplay' || eventName === 'playing') clearBufferRecovery(event.target);
                  if (eventName === 'loadedmetadata' || eventName === 'durationchange' ||
                      eventName === 'canplay' || eventName === 'playing') {
                    updatePlaybackBridge(true);
                  }
                }
              }, true);
            }
            for (const eventName of ['waiting', 'stalled']) {
              document.addEventListener(eventName, event => {
                if (event.target === getPlayerVideo()) {
                  scheduleBufferRecovery(event.target);
                  updatePlaybackBridge(true);
                }
              }, true);
            }
            document.addEventListener('progress', event => {
              if (event.target !== getPlayerVideo() || bufferingVideo !== event.target) return;
              const rate = Math.max(0.25, Number(event.target.playbackRate) || 1);
              if (event.target.readyState >= 3 || bufferedAheadSeconds(event.target) >= Math.max(1.5, rate * 1.5)) {
                clearBufferRecovery(event.target);
                updatePlaybackBridge(true);
              }
            }, true);
            document.addEventListener('play', event => {
              if (event.target === getPlayerVideo() && document.getElementById('youtoobee-mini-style'))
                window.__videoShieldMiniPlaybackWanted = true;
            }, true);
            document.addEventListener('pause', event => {
              if (event.target === bufferingVideo) clearBufferRecovery(event.target);
              updatePlaybackBridge(true);
              // During an app-initiated quality switch, YouTube may pause the new
              // rendition after it already resumed once. Re-assert play immediately.
              const transition = qualityTransition;
              if (transition && transition.wanted && event.target === getPlayerVideo()) {
                setTimeout(() => restoreQualityPlaybackNow(transition), 0);
              }
            }, true);
            for (const eventName of ['ended', 'emptied']) {
              document.addEventListener(eventName, event => {
                if (event.target !== getPlayerVideo()) return;
                if (event.target === bufferingVideo) clearBufferRecovery(event.target);
                updatePlaybackBridge(true);
              }, true);
            }
            document.addEventListener('loadedmetadata', event => {
              const transition = qualityTransition;
              if (transition && event.target === getPlayerVideo()) {
                setTimeout(() => restoreQualityPlaybackNow(transition), 0);
              }
            }, true);
            document.addEventListener('yt-navigate-finish', () => {
              lastCompatibilityUrl = '';
              clearBufferRecovery();
              clearPlaybackHeartbeat();
              clearMetadataBridgeRetry();
              compactSessionReferences();
              sweep();
              updatePlaybackBridge(true);
              scheduleCompatibility();
            }, true);
            sweep();
            startObserver();
            scheduleCompatibility();
          } catch (_) {}
        })();
        """.trimIndent()
        synchronized(scriptCache) { scriptCache[key] = script }
        return script
    }
}
