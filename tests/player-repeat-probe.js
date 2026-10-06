(async () => {
  const video = document.querySelector('video');
  if (!video || !Number.isFinite(video.duration) || !video.loop) throw new Error('Loaded video with Repeat enabled required');
  video.currentTime = video.duration - 1.5;
  await video.play();
  const before = {time:video.currentTime, duration:video.duration, loop:video.loop};
  await new Promise(resolve => setTimeout(resolve, 4500));
  return {before, after:{time:video.currentTime, paused:video.paused, ended:video.ended, loop:video.loop}};
})()
