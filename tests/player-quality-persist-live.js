(async()=>{
  const p=document.querySelector('.html5-video-player');
  p.setPlaybackQuality('hd720');p.setPlaybackQualityRange('hd720','hd720');
  await new Promise(resolve=>setTimeout(resolve,5000));
  const v=document.querySelector('video');
  return {config:window.__videoShieldCfg.preferredQuality,controller:window.__videoShieldQualityState?.(),
    preferred:p.getPreferredQuality?.(),width:v.videoWidth,height:v.videoHeight};
})()
