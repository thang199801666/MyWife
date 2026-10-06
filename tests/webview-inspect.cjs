// Local debug WebView diagnostics. Forward the app's webview_devtools_remote_<pid> to port 9228 first.
(async () => {
const targets = await (await fetch('http://127.0.0.1:9228/json')).json();
for (const target of targets.filter(item => item.url.startsWith('https://m.youtube.com/'))) {
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  const result = await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('WebView evaluation timed out')), 10000);
    socket.onmessage = event => {
      const message = JSON.parse(event.data);
      if (message.id === 1) { clearTimeout(timeout); resolve(message.result); }
    };
    socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { returnByValue: true,
      expression: `JSON.stringify({url:location.href, shield:!!window.__videoShieldInstalled,
        visibility:document.visibilityState, viewport:[innerWidth,innerHeight],
        pipStyle:!!document.getElementById('youtoobee-pip-style'),
        pipResumePending:!!window.__videoShieldPipResumePending, pipResumeError:window.__videoShieldPipResumeError || '',
        playerRect:document.querySelector('.html5-video-player')?.getBoundingClientRect().toJSON(),
        videoRect:document.querySelector('video')?.getBoundingClientRect().toJSON(),
        diagnostics:window.__videoShieldDiagnostics ? window.__videoShieldDiagnostics() : null,
        bridgeType:typeof window.VideoShieldBridge,
        style:!!document.getElementById('youtoobee-browse-surface-style'),
        topbars:Array.from(document.querySelectorAll('ytm-mobile-topbar-renderer,ytm-pivot-bar-renderer')).map(e=>({tag:e.tagName,display:getComputedStyle(e).display})),
        videos:Array.from(document.querySelectorAll('video')).map(v=>({paused:v.paused,time:v.currentTime,duration:v.duration,rate:v.playbackRate,ready:v.readyState})),
        ad:!!document.querySelector('.ad-showing,.ad-interrupting')})` } }));
  });
  console.log(result.result?.value ?? result);
  socket.close();
}
})().catch(error => { console.error(error); process.exitCode = 1; });
