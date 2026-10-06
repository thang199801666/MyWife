// Bounded document-start observation in a debug WebView. No ad or video data is injected.
// Usage: node tests/webview-ad-watch.cjs https://m.youtube.com/watch?v=<id>
(async () => {
  const targets = await (await fetch('http://127.0.0.1:9228/json')).json();
  const target = targets.find(t=>t.url.includes('/watch?'));
  if (!target) throw new Error('No player target');
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
  let seq=0;
  const pending=new Map();
  socket.onmessage=event=>{const m=JSON.parse(event.data);if(m.id&&pending.has(m.id)){const p=pending.get(m.id);pending.delete(m.id);clearTimeout(p.timer);m.error?p.reject(new Error(JSON.stringify(m.error))):p.resolve(m.result);}};
  const call=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;const timer=setTimeout(()=>{pending.delete(id);reject(new Error(method+' timed out'));},12000);pending.set(id,{resolve,reject,timer});socket.send(JSON.stringify({id,method,params}));});
  let identifier;
  try {
    const source=`(()=>{window.__adWatch={episodes:0,samples:0,firstAdTime:null};let wasAd=false;const timer=setInterval(()=>{const p=document.querySelector('.html5-video-player');const ad=!!p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting'));const r=window.__adWatch;r.samples++;if(ad&&!wasAd){r.episodes++;if(r.firstAdTime===null)r.firstAdTime=document.querySelector('video')?.currentTime??null;}wasAd=ad;},50);window.__adWatch.stop=()=>clearInterval(timer);})()`;
    await call('Page.enable');
    identifier=(await call('Page.addScriptToEvaluateOnNewDocument',{source})).identifier;
    await call('Page.navigate',{url:process.argv[2]});
    await new Promise(resolve=>setTimeout(resolve,25000));
    const result=await call('Runtime.evaluate',{returnByValue:true,expression:`({url:location.href,watch:window.__adWatch,shield:window.__videoShieldCfg?.enabled,diagnostics:window.__videoShieldDiagnostics?.(),video:(()=>{const v=document.querySelector('video');return v?{time:v.currentTime,duration:v.duration,paused:v.paused,ready:v.readyState}:null;})()})`});
    if(result.exceptionDetails)throw new Error(JSON.stringify(result.exceptionDetails));
    console.log(JSON.stringify(result.result?.value??result));
  } finally {
    await call('Runtime.evaluate',{expression:'window.__adWatch?.stop?.()'}).catch(()=>{});
    if(identifier)await call('Page.removeScriptToEvaluateOnNewDocument',{identifier}).catch(()=>{});
    socket.close();
  }
})().catch(error=>{console.error(error);process.exitCode=1;});
