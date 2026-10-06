(async () => {
  const p=document.querySelector('.html5-video-player'),v=document.querySelector('video');
  if(!p||!v) throw new Error('No video');
  window.__videoShieldCfg.preferredQuality='hd1080';
  window.__videoShieldPreferencesChanged?.();window.__videoShieldSweep?.();
  await new Promise(resolve=>setTimeout(resolve,7000));
  return {quality:p.getPlaybackQuality?.(),preferred:p.getPreferredQuality?.(),
    width:v.videoWidth,height:v.videoHeight,time:v.currentTime,paused:v.paused,
    controller:window.__videoShieldQualityState?.()};
})()
