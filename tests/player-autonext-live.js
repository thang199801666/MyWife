(() => {
  const p=document.querySelector('.html5-video-player');
  if(!p?.setAutonavState)throw Error('No autonav API');
  const read=()=>p.getVideoData?.().autonavState;
  const original=read();p.setAutonavState(1);const disabled=read();
  p.setAutonavState(2);const enabled=read();
  if(original===1||original===2)p.setAutonavState(original);
  return {original,disabled,enabled};
})()
