(() => {
  const v=document.querySelector('video');
  const p=document.querySelector('.html5-video-player');
  return {media:v?{muted:v.muted,volume:v.volume,paused:v.paused,currentTime:v.currentTime}:null,
    playerMuted:typeof p?.isMuted==='function'?p.isMuted():null,
    muteControls:Array.from(document.querySelectorAll('.ytp-unmute,.ytp-mute-button,.ytp-volume-panel,.ytp-volume-area')).map(n=>({className:n.className,display:getComputedStyle(n).display,visible:n.getClientRects().length>0})),
    diagnostics:window.__videoShieldDiagnostics?.()};
})()
