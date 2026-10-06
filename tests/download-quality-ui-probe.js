(() => {
 const w=document.getElementById('votuibe-download-action');
 const p=document.querySelector('.html5-video-player');
 const previous=w?.previousElementSibling;
 const share=previous?.querySelector('button,[role="button"]')||previous;
 const v=document.querySelector('video');
 return {download:{exists:!!w,count:document.querySelectorAll('#votuibe-download-action').length,
   previousLabel:share?.getAttribute('aria-label')||share?.textContent,rect:w?.getBoundingClientRect().toJSON(),text:w?.textContent},
   quality:{requested:window.__videoShieldCfg?.preferredQuality,available:p?.getAvailableQualityLevels?.(),current:p?.getPlaybackQuality?.()},
   media:v?{width:v.videoWidth,height:v.videoHeight,playing:!v.paused,muted:v.muted}:null};
})()
