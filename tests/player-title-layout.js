// Run through webview-evaluate.cjs on a debug watch page, at scroll position zero.
// Checks real layout rather than merely matching the injected CSS text.
(() => {
  const player = document.querySelector('#player-container-id');
  const title = document.querySelector('.slim-video-information-title');
  const video = document.querySelector('.html5-video-player video');
  if (!player || !title || !video) throw new Error('Watch page has not loaded');
  if (document.documentElement.getAttribute('data-votuibe-surface') !== 'expanded')
    throw new Error('Expand the player before checking the title');
  if (Math.abs(scrollY) > 1) throw new Error('Scroll to the top before checking the title');
  const frame = player.getBoundingClientRect();
  const heading = title.getBoundingClientRect();
  const media = video.getBoundingClientRect();
  if (heading.height <= 0 || heading.top < frame.bottom - 1)
    throw new Error('Player overlaps the video title');
  if (media.bottom > frame.bottom + 1 || media.top < frame.top - 1)
    throw new Error('Video extends outside the player');
  return {title: title.textContent, gap: heading.top - frame.bottom,
    player: frame.toJSON(), heading: heading.toJSON(), video: media.toJSON()};
})()
