(async () => {
  // Controlled public ad-host request. Check native blocked-request statistics before/after.
  // This verifies WebView interception only; it does not prove suppression of a real ad episode.
  const url = 'https://googleads.g.doubleclick.net/pagead/id?youtoobee_probe=' + Date.now();
  try { const response = await fetch(url, {mode:'no-cors', cache:'no-store'}); return {host:new URL(url).host, result:response.type}; }
  catch (error) { return {host:new URL(url).host, error:error.name}; }
})()
