(async () => {
  const saved = window.__youTooBeeBackgroundProbeBefore;
  if (!saved || saved.url !== location.href) throw new Error('Probe document changed; cannot restore another video');
  window.__videoShieldControl('pause');
  for (let i = 0; i < 16 && (document.querySelector('video')?.readyState || 0) < 3; i++) {
    await new Promise(resolve => setTimeout(resolve, 400));
  }
  window.__videoShieldSetPosition(saved.position);
  await new Promise(resolve => setTimeout(resolve, 1500));
  const video = document.querySelector('video');
  if (!video?.paused || Math.abs(video.currentTime - saved.position) > 0.5) throw new Error('Seek restoration not confirmed; saved probe retained');
  delete window.__youTooBeeBackgroundProbeBefore;
  return {position:video?.currentTime, paused:video?.paused, restoredFrom:saved.position};
})()
