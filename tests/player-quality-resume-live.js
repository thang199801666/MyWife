(async()=>{
  const p=document.querySelector('.html5-video-player');
  const before=p?.getPlayerState?.();p?.playVideo?.();
  await new Promise(resolve=>setTimeout(resolve,7000));
  const v=document.querySelector('video');
  return {before,state:p?.getPlayerState?.(),quality:p?.getPlaybackQuality?.(),preferred:p?.getPreferredQuality?.(),
    width:v?.videoWidth,height:v?.videoHeight,ready:v?.readyState,time:v?.currentTime,paused:v?.paused};
})()
