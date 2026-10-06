(() => {
  const v=document.querySelector('video'),p=document.querySelector('.html5-video-player');
  const rect=n=>n?{width:n.getBoundingClientRect().width,height:n.getBoundingClientRect().height,
    left:n.getBoundingClientRect().left,top:n.getBoundingClientRect().top}:null;
  return {mode:document.documentElement.getAttribute('data-votuibe-surface'),viewport:[innerWidth,innerHeight],
    video:rect(v),player:rect(p),container:rect(document.querySelector('.html5-video-container')),
    paused:v?.paused,time:v?.currentTime};
})()
