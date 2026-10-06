(() => {
  const p=document.querySelector('.html5-video-player'),v=document.querySelector('video');
  return {url:location.href,videoId:p?.getVideoData?.().video_id,title:p?.getVideoData?.().title,
    quality:p?.getPlaybackQuality?.(),preferred:p?.getPreferredQuality?.(),
    controller:window.__videoShieldQualityState?.(),
    video:v?{width:v.videoWidth,height:v.videoHeight,time:v.currentTime,ready:v.readyState,
      paused:v.paused,error:v.error?.code,networkState:v.networkState,
      buffered:Array.from({length:v.buffered.length},(_,i)=>[v.buffered.start(i),v.buffered.end(i)])}:null};
})()
