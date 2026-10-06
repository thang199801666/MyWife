(() => {
  const selectors = ['ytm-ad-slot-renderer','ytm-promoted-sparkles-web-renderer','ytm-promoted-video-renderer','ytm-companion-ad-renderer','ytd-ad-slot-renderer','[data-ad-impressions]'];
  const nodes = Array.from(document.querySelectorAll(selectors.join(','))).map(n => ({tag:n.tagName,visible:!!n.getClientRects().length && getComputedStyle(n).display!=='none',textCharacters:n.innerText?.trim().length??0,hiddenByShield:n.getAttribute('data-videoshield-reason')}));
  return {url:location.href,title:document.title,ready:document.readyState,shield:window.__videoShieldCfg?.enabled,watchLinks:document.querySelectorAll('a[href*="watch?v="]').length,visiblePopulatedAdNode:nodes.some(n=>n.visible&&n.textCharacters>0),nodes};
})()
