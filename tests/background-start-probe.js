(async () => {
  const video = document.querySelector('video');
  if (!video || video.readyState < 3 || !video.seekable.length || window.__videoShieldDiagnostics?.().ad) throw new Error('Loaded seekable content required');
  window.__youTooBeeBackgroundProbeBefore = {position:video.currentTime, url:location.href};
  window.__videoShieldSetPosition(30);
  window.__videoShieldControl('play');
  await new Promise(resolve => setTimeout(resolve, 1500));
  return {position:video.currentTime, paused:video.paused, ready:video.readyState, diagnostics:window.__videoShieldDiagnostics?.()};
})()
