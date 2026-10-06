// Temporary runtime tracing in the debug emulator, not shipped in the application.
(async () => {
  const target = (await (await fetch('http://127.0.0.1:9228/json')).json()).find(t => t.url.includes('/watch?'));
  if (!target) throw new Error('Open a test video first');
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  let expression;
  if (process.argv.includes('--install')) expression = `(() => {
    if (window.__ytbPauseTrace) return 'already installed';
    window.__ytbPauseTrace = [];
    const record = (kind, extra) => { window.__ytbPauseTrace.push({kind, at:Date.now(), hidden:document.hidden,
      time:document.querySelector('video')?.currentTime, extra}); window.__ytbPauseTrace = window.__ytbPauseTrace.slice(-30); };
    const original = HTMLMediaElement.prototype.pause;
    HTMLMediaElement.prototype.pause = function(...args) { record('pause-call', new Error().stack.split('\\n').slice(1,7)); return original.apply(this,args); };
    document.addEventListener('pause', () => record('pause-event'), true);
    document.addEventListener('play', () => record('play-event'), true);
    document.addEventListener('visibilitychange', () => record('visibility'), true);
    window.addEventListener('resize', () => record('resize', [innerWidth,innerHeight]));
    return 'installed';
  })()`;
  else if (process.argv.includes('--layout')) expression = `JSON.stringify((() => { const rows=[]; let el=document.querySelector('video');
    while(el && rows.length<12) { const s=getComputedStyle(el); rows.push({tag:el.tagName,id:el.id,cls:el.className,
      rect:el.getBoundingClientRect().toJSON(),overflow:s.overflow,clip:s.clipPath,transform:s.transform,opacity:s.opacity});el=el.parentElement; } return rows; })())`;
  else if (process.argv.includes('--play')) expression = `document.querySelector('video').play().then(()=> 'play accepted').catch(e=>e.name)`;
  else expression = `JSON.stringify(window.__ytbPauseTrace || [])`;
  const result = await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('trace timed out')), 10000);
    socket.onmessage = e => { const m = JSON.parse(e.data); if(m.id === 1) { clearTimeout(timeout); resolve(m.result); } };
    socket.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression,returnByValue:true,awaitPromise:true}}));
  });
  console.log(result.result?.value ?? result);
  socket.close();
})().catch(error => { console.error(error); process.exitCode=1; });
