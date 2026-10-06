(async () => {
  const samples = [];
  for (let i = 0; i < 7; i++) {
    const video = document.querySelector('video');
    samples.push({position:video?.currentTime, paused:video?.paused, ready:video?.readyState,
      width:video?.videoWidth, wanted:window.__videoShieldPipPlaybackWanted,
      pip:!!document.getElementById('youtoobee-pip-style'), error:window.__videoShieldPipResumeError || null});
    await new Promise(resolve => setTimeout(resolve, 1500));
  }
  return {url:location.href,samples};
})()
