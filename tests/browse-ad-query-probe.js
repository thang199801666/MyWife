(() => ({url:location.href,title:document.title,ready:document.readyState,
  bodyCharacters:document.body?.innerText.length??0,
  watchLinks:document.querySelectorAll('a[href*="watch?v="]').length,
  shield:window.__videoShieldCfg?.enabled}))()
