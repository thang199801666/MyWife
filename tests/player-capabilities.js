(() => {
  const p = document.querySelector('.html5-video-player');
  return { methods: Object.keys(p || {}).filter(k => /quality|rate/i.test(k)),
    quality: p?.getPlaybackQuality?.(), preferred: p?.getPreferredQuality?.(), available: p?.getAvailableQualityLevels?.(),
    media: (() => { const v = document.querySelector('video'); return v ? {time:v.currentTime, duration:v.duration, paused:v.paused, loop:v.loop, ready:v.readyState, width:v.videoWidth, height:v.videoHeight} : null; })(),
    rate: document.querySelector('video')?.playbackRate,
    config: window.__videoShieldCfg };
})()
