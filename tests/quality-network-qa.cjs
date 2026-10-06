// Debug-only network observation; never prints signed media URLs or cookies.
(async()=>{
  const targets=await(await fetch('http://127.0.0.1:9228/json')).json();
  const target=targets.find(t=>t.url.includes('/watch?'));if(!target)throw Error('No player');
  const socket=new WebSocket(target.webSocketDebuggerUrl),events=[],hosts=new Map();
  await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
  socket.onmessage=event=>{
    const m=JSON.parse(event.data),p=m.params||{};
    if(m.method==='Network.requestWillBeSent'){
      const host=new URL(p.request.url).hostname;
      if(host.endsWith('googlevideo.com'))hosts.set(p.requestId,host);
    }
    if(m.method==='Network.responseReceived'&&hosts.has(p.requestId))events.push({status:p.response.status,type:p.type});
    if(m.method==='Network.loadingFailed'&&hosts.has(p.requestId))events.push({error:p.errorText,type:p.type,cancelled:p.canceled});
  };
  socket.send(JSON.stringify({id:1,method:'Network.enable'}));
  await new Promise(resolve=>setTimeout(resolve,12000));
  console.log(JSON.stringify({mediaRequests:hosts.size,events}));socket.close();
})().catch(error=>{console.error(error);process.exitCode=1;});
