(async () => {
  const video = document.querySelector('video');
  if (!video || video.readyState < 1 || window.__videoShieldDiagnostics?.().ad) throw new Error('Loaded content media required');
  window.__youTooBeeSeekProbeBefore = {position:video.currentTime, paused:video.paused};
  window.__videoShieldControl('pause');
  window.__videoShieldSetPosition(30);
  await new Promise(resolve => setTimeout(resolve, 2200));
  return {url:location.href, position:video.currentTime, paused:video.paused, ready:video.readyState, diagnostics:window.__videoShieldDiagnostics?.()};
})()
