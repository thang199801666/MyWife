(async () => {
  const v = document.querySelector('video');
  if (!v || v.readyState < 3 || !v.seekable.length) throw new Error('Seekable loaded video required');
  v.currentTime = 30;
  await v.play();
  await new Promise(resolve=>setTimeout(resolve,1500));
  return {time:v.currentTime,paused:v.paused,ready:v.readyState,diagnostics:window.__videoShieldDiagnostics?.()};
})()
