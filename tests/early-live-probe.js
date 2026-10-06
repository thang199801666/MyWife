(() => {
  if(window.__videoShieldControl) window.__videoShieldControl('pause');
  const video=document.querySelector('video');
  const response=window.ytInitialPlayerResponse;
  return {url:location.href,earlyInstalled:!!window.__voTuibeEarlyInstalled,
    removedFields:window.__voTuibeEarlyAdFieldsRemoved,initialAdFields:response?Object.keys(response).filter(k=>['playerAds','adPlacements','adSlots'].includes(k)):null,
    paused:video?.paused,position:video?.currentTime,duration:video?.duration,
    nativeBridge:!!window.VideoShieldBridge,legacyAd:!!document.querySelector('.ad-showing,.ad-interrupting')};
})();
