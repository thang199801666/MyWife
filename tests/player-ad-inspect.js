(() => {
  const p = document.querySelector('.html5-video-player');
  return {url:location.href, ad:p?.classList.contains('ad-showing'),
    ads:Array.from(document.querySelectorAll('.video-ads,.ytp-ad-module,.ytp-ad-skip-button-modern,.ytp-skip-ad-button')).map(n => ({tag:n.tagName,class:n.className,hiddenByShield:n.getAttribute('data-videoshield-reason'),display:getComputedStyle(n).display,buttons:Array.from(n.querySelectorAll('button')).map(b=>({class:b.className,rects:b.getClientRects().length}))})),
    initialAds:Object.keys(window.ytInitialPlayerResponse || {}).filter(k=>/ad/i.test(k))};
})()
