(() => {
  const rows=Array.from(document.querySelectorAll('ytm-video-with-context-renderer'));
  const link=rows[1]?.querySelector('a[href*="/watch?"]');
  if(!link) throw new Error('No second video result');
  const target=link.href; link.click(); return target;
})()
