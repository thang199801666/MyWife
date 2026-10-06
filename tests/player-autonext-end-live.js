(async()=>{
  const p=document.querySelector('.html5-video-player'),v=document.querySelector('video');
  if(!v||!Number.isFinite(v.duration))throw Error('No finite video');
  const before=location.href;v.currentTime=v.duration-0.5;p?.playVideo?.();
  await new Promise(resolve=>setTimeout(resolve,8000));
  return {before,after:location.href,ended:document.querySelector('video')?.ended};
})()
