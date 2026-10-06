(async () => {
  const video = document.querySelector('video');
  if (!video || !Number.isFinite(video.duration) || video.duration <= 0) throw new Error('Content video not ready');
  video.pause();
  const button = document.createElement('button');
  button.textContent = 'QA fullscreen';
  button.style.cssText = 'position:fixed;top:20px;left:20px;width:180px;height:60px;z-index:2147483647';
  button.onclick = () => { video.requestFullscreen(); button.remove(); };
  document.body.appendChild(button);
  return {position: video.currentTime, duration: video.duration, paused: video.paused};
})();
