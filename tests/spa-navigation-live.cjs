const fs=require('node:fs');
(async()=>{
  const targets=await(await fetch('http://127.0.0.1:9228/json')).json();
  const home=targets.find(t=>new URL(t.url).pathname==='/');
  if(!home) throw new Error('No Home WebView');
  const socket=new WebSocket(home.webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
  const result=await new Promise((resolve,reject)=>{
    const timeout=setTimeout(()=>reject(new Error('CDP timeout')),10000);
    socket.onmessage=e=>{const m=JSON.parse(e.data);if(m.id===1){clearTimeout(timeout);resolve(m.result);}};
    socket.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression:fs.readFileSync('tests/spa-open-video.js','utf8'),returnByValue:true}}));
  });
  console.log(JSON.stringify(result));socket.close();
  await new Promise(r=>setTimeout(r,3000));
  const after=await(await fetch('http://127.0.0.1:9228/json')).json();
  console.log('After navigation:',JSON.stringify(after.map(t=>t.url)));
  if(!after.some(t=>t.url.includes('watch?v=9bZkp7q19f0'))) throw new Error('SPA video did not reach player');
})().catch(e=>{console.error(e);process.exitCode=1;});
