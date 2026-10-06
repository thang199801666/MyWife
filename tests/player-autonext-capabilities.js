(() => {
  const p=document.querySelector('.html5-video-player');
  return {methods:Object.keys(p||{}).filter(k=>/autonav|autoplay/i.test(k)),state:p?.getAutonavState?.(),
    controls:Array.from(document.querySelectorAll('[aria-checked],.ytp-autonav-toggle-button')).map(n=>({
      label:n.getAttribute('aria-label'),checked:n.getAttribute('aria-checked'),title:n.getAttribute('title'),
      role:n.getAttribute('role')})).slice(0,15)};
})()
