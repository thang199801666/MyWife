(async()=>{
  const link=Array.from(document.querySelectorAll('a[href*="/watch?"]')).find(a=>a.querySelector('img.video-thumbnail-img'));
  if(!link) throw new Error('No search thumbnail link');
  const image=link.querySelector('img.video-thumbnail-img');
  const before=image.currentSrc||image.src;
  image.removeAttribute('src');
  image.dispatchEvent(new Event('error',{bubbles:true}));
  await new Promise(resolve=>setTimeout(resolve,2000));
  return {url:location.href,before,after:image.currentSrc||image.src,width:image.naturalWidth,
    fallback:(image.currentSrc||image.src).includes('/mqdefault.jpg'),
    title:link.closest('ytm-video-with-context-renderer')?.querySelector('h3,h4')?.textContent};
})()
