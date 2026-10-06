(async () => {
  const saved = window.__youTooBeeSeekProbeBefore;
  if (!saved) throw new Error('No matching seek probe to restore');
  window.__videoShieldControl('pause');
  window.__videoShieldSetPosition(saved.position);
  await new Promise(resolve => setTimeout(resolve, 1500));
  delete window.__youTooBeeSeekProbeBefore;
  const video = document.querySelector('video');
  return {position:video?.currentTime, paused:video?.paused, restoredFrom:saved.position};
})()
