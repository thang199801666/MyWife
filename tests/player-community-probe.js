(async () => {
  const video = document.querySelector('video');
  if (location.href.indexOf('aqz-KE-bpKQ') < 0 || !video || video.readyState < 1) throw new Error('Loaded Big Buck Bunny test video required');
  if (!window.__videoShieldCfg?.skipIntrosOutros) throw new Error('Enable community intro/outro skipping in native settings first');
  // Current public API marks an outro at 494.6179..618.71356. Do not inject segments here:
  // this probe relies on the app's actual native fetch, parser and bridge delivery.
  window.__videoShieldControl('pause');
  video.currentTime = 494.8;
  const before = {time:video.currentTime, preference:window.__videoShieldCfg.skipIntrosOutros};
  await video.play();
  await new Promise(resolve => setTimeout(resolve, 5000));
  const after = {time:video.currentTime, paused:video.paused, diagnostics:window.__videoShieldDiagnostics?.()};
  window.__videoShieldControl('pause');
  return {before, after, skipped: after.time >= 618.7};
})()
