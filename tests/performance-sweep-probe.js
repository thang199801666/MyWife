(() => {
  const samples=[];
  for(let i=0;i<10;i++){const start=performance.now();window.__videoShieldSweep?.();samples.push(performance.now()-start);}
  const video=document.querySelector('video');
  return {url:location.href,nodes:document.querySelectorAll('*').length,sweepMs:samples,averageSweepMs:samples.reduce((a,b)=>a+b,0)/samples.length,diagnostics:window.__videoShieldDiagnostics?.(),media:video?{time:video.currentTime,ready:video.readyState,paused:video.paused}:null};
})()
