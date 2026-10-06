(async () => {
  const video = document.querySelector('video');
  if (!video) throw new Error('No video');
  const r = video.getBoundingClientRect();
  return {url: location.href, paused: video.paused, position: video.currentTime,
    duration: video.duration, rect: {left: r.left, top: r.top, right: r.right, bottom: r.bottom},
    viewport: {width: innerWidth, height: innerHeight},
    sameVideo: window.__minimizeProbeVideo === video,
    miniStyle: !!document.getElementById('youtoobee-mini-style'),
    miniWanted: !!window.__videoShieldMiniPlaybackWanted};
})();
