package com.example.videoshield

import org.json.JSONObject

object AdBlockScript {
    fun build(
        preferences: ShieldPreferences,
        rules: RulePack,
        pageWhitelisted: Boolean,
        preferredQualityOverride: String? = null
    ): String {
        val cfg = JSONObject().apply {
            put("enabled", preferences.shieldEnabled)
            put("safeMode", preferences.safeMode)
            put("bypassAds", pageWhitelisted)
            put("shorts", preferences.blockShorts)
            put("recommendations", preferences.blockRecommendations)
            put("comments", preferences.blockComments)
            put("endScreen", preferences.blockEndScreen)
            put("openInApp", preferences.blockOpenInApp)
            put("amoled", preferences.amoledTheme)
            put("autoRepeat", preferences.autoRepeat)
            put("playbackSpeed", preferences.playbackSpeed)
            put("backgroundPlayback", preferences.backgroundControls && preferences.screenOffPlayback)
            put("preferredQuality", preferredQualityOverride ?: preferences.preferredQuality)
            put("communitySponsorSkip", preferences.communitySponsorSkip)
            put("skipIntrosOutros", preferences.skipIntrosOutros)
        }.toString()
        val ruleJson = rules.domRulesJson()

        return """
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

            let adState = null;
            let lastSkipButton = null;
            let lastSkipAt = 0;
            let lastPlaying = null;
            let lastTitle = "";
            let lastChannel = "";
            let lastVideoId = "";
            let lastPlaybackReportAt = 0;
            let lastReportedPosition = 0;
            let lastEndedVideoId = "";
            let repeatBoundVideo = null;
            let repeatEndedHandler = null;
            let repeatRestartAt = 0;
            let repeatRestartVideoId = "";
            let rateBoundVideo = null;
            let rateChangeHandler = null;
            let rateRetryTimer = null;
            let rateRetryGeneration = 0;
            let ratePlayerApiVideo = null;
            let ratePlayerApiValue = NaN;
            let lastRequestedRate = 1;
            let hiddenSeen = new WeakSet();
            let lastBridgeUpdate = 0;
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
            let mobileSkipCache;
            let observedSweepTimer = null;
            let fallbackSweepTimer = null;
            let lastSweepAt = 0;
            let lastFullDomSweepAt = 0;
            let preloadBoundVideo = null;
            let preloadAttributeObserver = null;

            const EMPTY = '__VS_EMPTY__';

            function getPlayerVideo() {
              try {
                // YouTube can keep preload/ad/preview <video> elements in the same DOM.
                // Always prefer the actual html5-main-video used by the active player.
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

            function applyPreloadPolicy(video) {
              if (!video || video !== getPlayerVideo()) return;
              try {
                // YouTube normally feeds this element through MediaSource. `load()` must not be
                // called here: it tears down that MediaSource and throws away already-buffered
                // ranges. We only prevent the host page from downgrading the browser hint to
                // metadata/none, leaving segment scheduling to YouTube/Chromium.
                if (video.preload !== 'auto') video.preload = 'auto';
                if (video.getAttribute('preload') !== 'auto') video.setAttribute('preload', 'auto');
                if (!video.hasAttribute('autobuffer')) video.setAttribute('autobuffer', '');

                if (preloadBoundVideo === video) return;
                preloadBoundVideo = video;
                try { preloadAttributeObserver?.disconnect?.(); } catch (_) {}
                preloadAttributeObserver = new MutationObserver(() => {
                  if (video !== getPlayerVideo()) return;
                  try {
                    if (video.getAttribute('preload') !== 'auto') video.setAttribute('preload', 'auto');
                  } catch (_) { internalErrors++; }
                });
                preloadAttributeObserver.observe(video, {attributes:true, attributeFilter:['preload']});
              } catch (_) { internalErrors++; }
            }

            function configuredPlaybackRate() {
              const raw = Number((window.__videoShieldCfg || {}).playbackSpeed);
              return Number.isFinite(raw) && raw > 0 ? Math.max(0.25, Math.min(4, raw)) : 1;
            }

            function playerForVideo(video) {
              try {
                return video?.closest?.('.html5-video-player') || document.querySelector('.html5-video-player');
              } catch (_) {
                internalErrors++;
                return document.querySelector('.html5-video-player');
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
                const target = configuredPlaybackRate();
                if (Math.abs((Number(video.playbackRate) || 1) - target) > 0.01) {
                  ratePlayerApiValue = NaN;
                  schedulePlaybackRateRestore(video, 40);
                }
              };
              try { video.addEventListener('ratechange', rateChangeHandler, true); } catch (_) { internalErrors++; }
            }

            function applyPlaybackRatePolicy(video) {
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
                const player = playerForVideo(video);
                const mediaRate = Number(video.playbackRate) || 1;
                let playerRate = NaN;
                if (player && typeof player.getPlaybackRate === 'function') {
                  try { playerRate = Number(player.getPlaybackRate()); } catch (_) {}
                }
                // Do not trust only our cached API value: YouTube can silently reset its
                // internal player rate after a rendition/SPA transition while reusing the
                // same <video>. Re-issue the player command whenever either side differs.
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
                if (Math.abs((Number(video.defaultPlaybackRate) || 1) - target) > 0.01) {
                  video.defaultPlaybackRate = target;
                  applied = true;
                }
                if (Math.abs((Number(video.playbackRate) || 1) - target) > 0.01) {
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
                // Restart just before YouTube's own end/autonav handler can replace the
                // current watch route. The threshold is intentionally tiny so the final
                // frame is not perceptibly clipped.
                if (video.ended || (Number.isFinite(duration) && duration > 0.5 &&
                    Number.isFinite(position) && !video.seeking && position >= duration - 0.08)) {
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

            function applyRepeatPolicy(video) {
              bindRepeatVideo(video);
              const enabled = !!(window.__videoShieldCfg || {}).autoRepeat;
              if (!video) return;
              try {
                // During an ad, keep the ad itself non-looping while updating the content
                // state that will be restored after the ad ends.
                if (adState && adState.video === video) adState.loop = enabled;
                video.loop = enabled && !isPlayerAd();
                if (enabled) {
                  const player = playerForVideo(video);
                  // Native queue/repeat owns end-of-video navigation. Disable the site's
                  // autonav countdown where this internal API is available.
                  if (player && typeof player.setAutonavState === 'function') {
                    try { player.setAutonavState(1); } catch (_) {}
                  }
                }
              } catch (_) { internalErrors++; }
              if (!enabled) {
                repeatRestartAt = 0;
                repeatRestartVideoId = '';
              }
            }

            // Keep the trusted player page active when background playback is enabled.
            // YouTube otherwise unloads its media on visibility changes, not just pause().
            if (typeof window.VideoShieldBridge === 'object') {
              for (const key of ['hidden', 'visibilityState', 'webkitHidden', 'webkitVisibilityState']) {
                let owner = document;
                let descriptor;
                while (owner && !(descriptor = Object.getOwnPropertyDescriptor(owner, key))) owner = Object.getPrototypeOf(owner);
                if (!descriptor || typeof descriptor.get !== 'function') continue;
                const originalGet = descriptor.get;
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
                    (nativeAutonavVideoId!==videoId || Date.now()-lastNativeAutonavAt>=3000)) {
                  player.setAutonavState(1); lastNativeAutonavAt=Date.now(); nativeAutonavVideoId=videoId;
                }
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
              if (!video || !communitySkippingEnabled()) return;
              const videoId = getVideoId();
              if (!videoId || videoId !== segmentVideoId || !Array.isArray(communitySegments)) return;
              const now = Number(video.currentTime) || 0;
              // A repeated video or a user rewind must make skipped segments eligible again.
              if (now < lastSegmentPosition - 1) skippedSegmentKeys.clear();
              lastSegmentPosition = now;
              for (let i = 0; i < communitySegments.length; i++) {
                const segment = communitySegments[i] || {};
                const start = Number(segment.start);
                const end = Number(segment.end);
                if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) continue;
                const key = start.toFixed(3) + ':' + end.toFixed(3) + ':' + String(segment.category || '');
                if (skippedSegmentKeys.has(key)) continue;
                if (now >= Math.max(0, start - 0.08) && now < end - 0.03) {
                  try {
                    const duration = Number(video.duration);
                    video.currentTime = Number.isFinite(duration) && duration > 0
                      ? Math.min(end + 0.03, Math.max(0, duration - 0.05))
                      : end + 0.03;
                    skippedSegmentKeys.add(key);
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
              const candidates = hasLegacyAdSignal() ? selectorsFor('skip').map(selector => {
                try { return document.querySelector(selector); } catch (_) { return null; }
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
              const player = document.querySelector('.html5-video-player');
              return !!(player && player.classList &&
                (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting')));
            }

            function mobileSkipButton() {
              if (scanningSweep && mobileSkipCache !== undefined) return mobileSkipCache;
              const button = findMobileSkipButton();
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
              const player = document.querySelector('.html5-video-player');
              const video = player ? player.querySelector('video') : getPlayerVideo();
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

            function getVideoId() {
              try {
                const u = new URL(location.href);
                if (u.pathname.startsWith('/shorts/')) return u.pathname.split('/')[2] || '';
                return u.searchParams.get('v') || '';
              } catch (_) { return ''; }
            }

            function updatePlaybackBridge() {
              // Ad media time must never overwrite the content resume position or advance the queue.
              if (isPlayerAd()) return;
              const now = Date.now();
              if (now - lastBridgeUpdate < 450) return;
              lastBridgeUpdate = now;
              const video = getPlayerVideo();
              // An unloaded placeholder must not replace the saved content position with zero.
              if (!video || video.readyState < 1) return;
              if (!video.ended) lastEndedVideoId = '';
              const playing = !video.paused && !video.ended && video.readyState > 2;
              const title = (document.title || '').replace(/\s*-\s*YouTube\s*$/, '').trim();
              const info = getChannelInfo();
              const channel = info.name || '';
              const videoId = getVideoId();
              if (videoId !== lastVideoId) {
                lastEndedVideoId = "";
              }
              const periodic = playing && now - lastPlaybackReportAt >= 10000;
              const position = Number(video.currentTime);
              const pausedSeek = !playing && Number.isFinite(position) && Math.abs(position - lastReportedPosition) >= 0.25;
              if (playing !== lastPlaying || title !== lastTitle || channel !== lastChannel || videoId !== lastVideoId || periodic || pausedSeek) {
                lastPlaying = playing;
                lastTitle = title;
                lastChannel = channel;
                lastVideoId = videoId;
                lastPlaybackReportAt = now;
                lastReportedPosition = Number.isFinite(position) ? Math.max(0, position) : 0;
                const duration = Number(video.duration);
                const positionMs = Number.isFinite(position) ? Math.max(0, Math.round(position * 1000)) : 0;
                const durationMs = Number.isFinite(duration) ? Math.max(0, Math.round(duration * 1000)) : 0;
                try {
                  if (window.VideoShieldBridge) VideoShieldBridge.onPlaybackState(playing, title, channel, info.url || '', videoId, positionMs, durationMs);
                } catch (_) { internalErrors++; }
              }
              const player = document.querySelector('.html5-video-player');
              const showingAd = isPlayerAd();
              const c = window.__videoShieldCfg || {};
              if (video.ended && c.autoRepeat && videoId && !showingAd) {
                restartRepeatedVideo(video);
              } else if (video.ended && videoId && !showingAd && lastEndedVideoId !== videoId) {
                lastEndedVideoId = videoId;
                try { if (window.VideoShieldBridge) VideoShieldBridge.onPlaybackEnded(videoId); } catch (_) { internalErrors++; }
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
                const player = document.querySelector('.html5-video-player');
                if (player && typeof player.isMuted === 'function' && player.isMuted() && typeof player.unMute === 'function') player.unMute();
                if (video.muted) video.muted = false;
                if (video.volume !== 1) video.volume = 1;
              } catch (_) { internalErrors++; }
            }

            function scheduleFallbackSweep() {
              try {
                if (fallbackSweepTimer) clearTimeout(fallbackSweepTimer);
                const video = getPlayerVideo();
                const playing = !!(video && !video.paused && !video.ended);
                const hidden = document.visibilityState === 'hidden';
                const delay = playing ? (hidden ? 1800 : 1000) : (hidden ? 8000 : 2500);
                fallbackSweepTimer = setTimeout(() => {
                  fallbackSweepTimer = null;
                  const fullDom = Date.now() - lastFullDomSweepAt >= (hidden ? 10000 : 5000);
                  sweep(fullDom);
                }, delay);
              } catch (_) { internalErrors++; }
            }

            function scheduleObservedSweep() {
              if (observedSweepTimer) return;
              const elapsed = Date.now() - lastSweepAt;
              const delay = Math.max(0, 500 - elapsed);
              observedSweepTimer = setTimeout(() => {
                observedSweepTimer = null;
                sweep();
              }, delay);
            }

            function sweep(fullDom = true) {
              try {
                scanningSweep = true;
                mobileSkipCache = undefined;
                if (fullDom) {
                  hidePageAds();
                  hideAnnoyances();
                  applyAmoledTheme();
                  lastFullDomSweepAt = Date.now();
                }
                handlePlayerAd();
                const video = getPlayerVideo();
                if (!isPlayerAd()) {
                  useSystemAudio(video);
                  applyPlaybackEnhancements(video);
                  handleCommunitySegments(video);
                }
                updatePlaybackBridge();
                if (window.__voTuibeInstallDownload) window.__voTuibeInstallDownload();
              } catch (_) { internalErrors++; }
              finally {
                scanningSweep = false;
                mobileSkipCache = undefined;
                lastSweepAt = Date.now();
                scheduleFallbackSweep();
              }
            }

            window.__videoShieldSweep = sweep;
            window.__videoShieldDiagnostics = () => ({ errors: internalErrors, playing: lastPlaying,
              videoId: lastVideoId, ad: isPlayerAd(), bridge: typeof window.VideoShieldBridge,
              repeat: !!(window.__videoShieldCfg || {}).autoRepeat, mediaLoop: !!getPlayerVideo()?.loop,
              configuredRate: configuredPlaybackRate(), mediaRate: Number(getPlayerVideo()?.playbackRate || 1),
              requestedRate: lastRequestedRate });
            window.__videoShieldScheduleCompatibility = scheduleCompatibility;
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
                const video = getPlayerVideo();
                if (!video) return false;
                const applied = applyPlaybackRatePolicy(video);
                schedulePlaybackRateRestore(video);
                return applied;
              } catch (_) { internalErrors++; return false; }
            };
            window.__videoShieldSetSegments = (videoId, segments) => {
              try {
                segmentVideoId = String(videoId || '').slice(0, 64);
                communitySegments = Array.isArray(segments) ? segments.slice(0, 256) : [];
                skippedSegmentKeys = new Set();
                lastSegmentPosition = 0;
                return true;
              } catch (_) { internalErrors++; return false; }
            };
            window.__videoShieldSetRepeat = (enabled) => {
              try {
                if (!window.__videoShieldCfg) window.__videoShieldCfg = {};
                window.__videoShieldCfg.autoRepeat = !!enabled;
                applyRepeatPolicy(getPlayerVideo());
                if (enabled) lastEndedVideoId = '';
                return true;
              } catch (_) { internalErrors++; return false; }
            };

            window.__videoShieldPreferencesChanged = () => {
              const media = getPlayerVideo();
              applyPreloadPolicy(media);
              applyRepeatPolicy(media);
              applyPlaybackRatePolicy(media);
              if (media && !isPlayerAd()) schedulePlaybackRateRestore(media);
              // Unrelated settings must not reset a user's quality or adaptive downgrade.
              if (qualitySession && qualitySession.mode !== String((window.__videoShieldCfg || {}).preferredQuality || 'auto')) {
                qualityAppliedVideoId = ''; qualitySession = null;
              }
            };
            window.__videoShieldResetQuality = () => { qualityAppliedVideoId=''; qualitySession=null; sweep(); };
            window.__videoShieldQualityState = () => qualitySession ? {
              mode:qualitySession.mode,target:qualitySession.target,manual:qualitySession.manual
            } : null;

            function startObserver() {
              if (!document.documentElement || window.__videoShieldObserver) return;
              const observer = new MutationObserver(() => {
                // YouTube changes inline progress/control styles at display-frame cadence.
                // Watching style on the entire DOM made the full shield sweep effectively
                // continuous. Child/class/visibility mutations still catch ad transitions;
                // a lightweight fallback sweep covers experiments that only touch style.
                scheduleObservedSweep();
              });
              observer.observe(document.documentElement, {
                subtree: true, childList: true, attributes: true,
                attributeFilter: ['class', 'hidden', 'aria-hidden']
              });
              window.__videoShieldObserver = observer;
              document.addEventListener('visibilitychange', () => {
                scheduleObservedSweep();
                scheduleFallbackSweep();
              }, true);
            }

            // The mobile player pauses on small viewports. In native PiP only, preserve the
            // explicit playback intent; native Pause clears it before calling video.pause().
            if (typeof HTMLMediaElement !== 'undefined') {
              const nativePause = HTMLMediaElement.prototype.pause;
              HTMLMediaElement.prototype.pause = function() {
                const playerVideo = getPlayerVideo();
                const keepPlaying = (window.__videoShieldPipPlaybackWanted && document.getElementById('youtoobee-pip-style')) ||
                    (window.__videoShieldMiniPlaybackWanted && document.getElementById('youtoobee-mini-style')) ||
                    (window.__videoShieldExpandPlaybackWanted && document.documentElement.getAttribute('data-votuibe-surface')==='expanded');
                if (this === playerVideo && keepPlaying && !this.ended && !isPlayerAd()) return;
                return nativePause.call(this);
              };
            }

            document.addEventListener('loadstart', event => {
              if (event.target === getPlayerVideo()) applyPreloadPolicy(event.target);
            }, true);
            document.addEventListener('play', updatePlaybackBridge, true);
            for(const eventName of ['pointerdown','touchstart']) document.addEventListener(eventName,event=>{
              window.__videoShieldExpandPlaybackWanted=false;
              const menu = event.target?.closest?.('.ytp-settings-button,.ytp-menuitem,[role="menuitem"],[role="menuitemradio"]');
              if (menu) {
                if (!qualityPlaybackIntent || qualityPlaybackIntent.id !== getVideoId())
                  window.__videoShieldPrepareQualityChange();
              } else cancelQualityPlaybackRestore();
            },true);
            document.addEventListener('keydown', cancelQualityPlaybackRestore, true);
            for (const eventName of ['loadedmetadata', 'durationchange', 'canplay', 'playing', 'play', 'volumechange']) {
              document.addEventListener(eventName, event => {
                if (event.target === getPlayerVideo()) {
                  applyPreloadPolicy(event.target);
                  applyRepeatPolicy(event.target);
                  if (eventName !== 'volumechange') applyPlaybackRatePolicy(event.target);
                  useSystemAudio(event.target);
                }
              }, true);
            }
            document.addEventListener('play', event => {
              if (event.target === getPlayerVideo() && document.getElementById('youtoobee-mini-style'))
                window.__videoShieldMiniPlaybackWanted = true;
            }, true);
            document.addEventListener('pause', event => {
              updatePlaybackBridge(event);
              // During an app-initiated quality switch, YouTube may pause the new
              // rendition after it already resumed once. Re-assert play immediately.
              const transition = qualityTransition;
              if (transition && transition.wanted && event.target === getPlayerVideo()) {
                setTimeout(() => restoreQualityPlaybackNow(transition), 0);
              }
            }, true);
            document.addEventListener('loadedmetadata', event => {
              const transition = qualityTransition;
              if (transition && event.target === getPlayerVideo()) {
                setTimeout(() => restoreQualityPlaybackNow(transition), 0);
              }
            }, true);
            document.addEventListener('yt-navigate-finish', () => {
              lastCompatibilityUrl = '';
              sweep();
              scheduleCompatibility();
            }, true);
            sweep();
            startObserver();
            scheduleCompatibility();
          } catch (_) {}
        })();
        """.trimIndent()
    }
}
