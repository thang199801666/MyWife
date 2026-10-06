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
            let hiddenSeen = new WeakSet();
            let lastBridgeUpdate = 0;
            let internalErrors = 0;
            let compatibilityTimer = null;
            let lastCompatibilityUrl = "";
            let qualityAppliedVideoId = "";
            let qualitySession = null;
            let lastNativeAutonavAt = 0;
            let nativeAutonavVideoId = '';
            let segmentVideoId = "";
            let communitySegments = [];
            let skippedSegmentKeys = new Set();
            let lastSegmentPosition = 0;
            let scanningSweep = false;
            let mobileSkipCache;

            const EMPTY = '__VS_EMPTY__';

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

            function applyPlaybackEnhancements(video) {
              if (!video) return;
              const c = window.__videoShieldCfg || {};
              try { video.loop = !!c.autoRepeat; } catch (_) { internalErrors++; }
              const configuredRate = Number(c.playbackSpeed);
              if (video.readyState > 2 && Number.isFinite(configuredRate) && configuredRate > 0) {
                const rate = Math.max(0.25, Math.min(4, configuredRate));
                try {
                  if (video.defaultPlaybackRate !== rate) video.defaultPlaybackRate = rate;
                  if (video.playbackRate !== rate) video.playbackRate = rate;
                } catch (_) { internalErrors++; }
              }

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
              const video = document.querySelector('video');
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
              const video = player ? player.querySelector('video') : document.querySelector('video');
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
              const video = document.querySelector('video');
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
                try { video.currentTime = 0; video.play(); } catch (_) { internalErrors++; }
                lastEndedVideoId = "";
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
                const videoFound = !!document.querySelector('video');
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

            function sweep() {
              try {
                scanningSweep = true;
                mobileSkipCache = undefined;
                hidePageAds();
                hideAnnoyances();
                applyAmoledTheme();
                handlePlayerAd();
                const video = document.querySelector('video');
                if (!isPlayerAd()) {
                  useSystemAudio(video);
                  applyPlaybackEnhancements(video);
                  handleCommunitySegments(video);
                }
                updatePlaybackBridge();
                if (window.__voTuibeInstallDownload) window.__voTuibeInstallDownload();
              } catch (_) { internalErrors++; }
              finally { scanningSweep = false; mobileSkipCache = undefined; }
            }

            window.__videoShieldSweep = sweep;
            window.__videoShieldDiagnostics = () => ({ errors: internalErrors, playing: lastPlaying,
              videoId: lastVideoId, ad: isPlayerAd(), bridge: typeof window.VideoShieldBridge });
            window.__videoShieldScheduleCompatibility = scheduleCompatibility;
            window.__videoShieldControl = (cmd) => {
              if(cmd==='pause' || cmd==='toggle') window.__videoShieldExpandPlaybackWanted=false;
              if (cmd === 'pause' || cmd === 'toggle') window.__videoShieldPipResumePending = false;
              if (cmd === 'pause') window.__videoShieldPipPlaybackWanted = false;
              if (cmd === 'pause') window.__videoShieldMiniPlaybackWanted = false;
              const video = document.querySelector('video');
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
              const video = document.querySelector('video');
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
              if (window.__videoShieldCfg) window.__videoShieldCfg.playbackSpeed = target;
              const video = document.querySelector('video');
              if (!video) return false;
              try {
                video.defaultPlaybackRate = target;
                if (adState && adState.video === video) adState.playbackRate = target;
                else video.playbackRate = target;
                return true;
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

            window.__videoShieldPreferencesChanged = () => {
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
                if (window.__videoShieldPending) return;
                window.__videoShieldPending = true;
                // YouTube mutates progress/control styles every frame. Batch those changes
                // so filtering does not repeatedly scan the entire document at display FPS.
                // Keep a fixed deadline (not a debounce) so continuous mutations cannot
                // postpone an ad transition indefinitely.
                setTimeout(() => {
                  window.__videoShieldPending = false;
                  sweep();
                }, 200);
              });
              observer.observe(document.documentElement, {
                subtree: true, childList: true, attributes: true,
                attributeFilter: ['class', 'style', 'hidden']
              });
              window.__videoShieldObserver = observer;
            }

            // The mobile player pauses on small viewports. In native PiP only, preserve the
            // explicit playback intent; native Pause clears it before calling video.pause().
            if (typeof HTMLMediaElement !== 'undefined') {
              const nativePause = HTMLMediaElement.prototype.pause;
              HTMLMediaElement.prototype.pause = function() {
                const playerVideo = document.querySelector('.html5-video-player video') || document.querySelector('video');
                const keepPlaying = (window.__videoShieldPipPlaybackWanted && document.getElementById('youtoobee-pip-style')) ||
                    (window.__videoShieldMiniPlaybackWanted && document.getElementById('youtoobee-mini-style')) ||
                    (window.__videoShieldExpandPlaybackWanted && document.documentElement.getAttribute('data-votuibe-surface')==='expanded');
                if (this === playerVideo && keepPlaying && !this.ended && !isPlayerAd()) return;
                return nativePause.call(this);
              };
            }

            document.addEventListener('play', updatePlaybackBridge, true);
            for(const eventName of ['pointerdown','touchstart']) document.addEventListener(eventName,()=>{
              window.__videoShieldExpandPlaybackWanted=false;
            },true);
            for (const eventName of ['loadedmetadata', 'play', 'volumechange']) {
              document.addEventListener(eventName, event => {
                if (event.target === document.querySelector('video')) useSystemAudio(event.target);
              }, true);
            }
            document.addEventListener('play', event => {
              if (event.target === document.querySelector('video') && document.getElementById('youtoobee-mini-style'))
                window.__videoShieldMiniPlaybackWanted = true;
            }, true);
            document.addEventListener('pause', updatePlaybackBridge, true);
            document.addEventListener('yt-navigate-finish', () => {
              lastCompatibilityUrl = '';
              sweep();
              scheduleCompatibility();
            }, true);
            sweep();
            startObserver();
            scheduleCompatibility();
            setInterval(sweep, 800);
          } catch (_) {}
        })();
        """.trimIndent()
    }
}
