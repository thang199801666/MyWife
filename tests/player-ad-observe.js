(async () => {
  const samples = [];
  for (let i=0;i<20;i++) {
    const p=document.querySelector('.html5-video-player'),v=document.querySelector('video');
    samples.push({ad:!!p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting')),
      time:v?.currentTime,ready:v?.readyState,paused:v?.paused,
      skipButtons:Array.from(document.querySelectorAll('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button')).filter(n=>n.getClientRects().length).length});
    await new Promise(resolve=>setTimeout(resolve,500));
  }
  return {url:location.href,enabled:window.__videoShieldCfg?.enabled,adSeen:samples.some(s=>s.ad),samples};
})()
