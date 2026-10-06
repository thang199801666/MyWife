(async () => {
  const p=document.querySelector('.html5-video-player'),v=document.querySelector('video');
  if(!p||!v) throw new Error('No video');
  const snapshot=()=>({quality:p.getPlaybackQuality?.(),preferred:p.getPreferredQuality?.(),
    width:v.videoWidth,height:v.videoHeight,time:v.currentTime,paused:v.paused,
    controller:window.__videoShieldQualityState?.()});
  const before=snapshot();
  window.__videoShieldCfg.preferredQuality='hd720';
  window.__videoShieldPreferencesChanged?.();window.__videoShieldSweep?.();
  v.play().catch(()=>{});
  await new Promise(resolve=>setTimeout(resolve,7000));
  return {before,after:snapshot()};
})()
