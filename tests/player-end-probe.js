(async () => {
  const v = document.querySelector('video');
  if (!v || v.readyState < 3 || !v.seekable.length || !Number.isFinite(v.duration) || v.loop) throw new Error('Seekable video with Repeat off required');
  v.currentTime = v.duration - 1;
  await v.play();
  if (v.currentTime < v.duration - 2) throw new Error('Player did not accept the end seek; inspect loading state before retrying');
  return {url:location.href, time:v.currentTime, duration:v.duration};
})()
