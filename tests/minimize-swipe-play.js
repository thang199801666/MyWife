(async () => {
  const video = document.querySelector('video');
  if (!video || !Number.isFinite(video.duration) || video.duration <= 0) throw new Error('Content not ready');
  await video.play();
  window.__minimizeProbeVideo = video;
  return {position: video.currentTime, duration: video.duration, paused: video.paused};
})();
