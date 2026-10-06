// Exercise real Chromium XHR/fetch getters in the installed debug WebView.
// DevTools fulfills only requests bearing the unique QA marker; no source
// requests are modified and policy changes are restored in finally.
const assert=require('node:assert/strict');
(async()=>{
  const targets=await(await fetch('http://127.0.0.1:9228/json')).json();
  const target=targets.find(t=>t.url.includes('/watch?')) || targets.find(t=>t.url.startsWith('https://m.youtube.com/'));
  if(!target) throw new Error('No YouTube debug WebView');
  const ws=new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;});
  let seq=0;const pending=new Map();
  const call=(method,params={})=>new Promise((resolve,reject)=>{
    const id=++seq,timer=setTimeout(()=>{pending.delete(id);reject(new Error(method+' timed out'));},20000);
    pending.set(id,{resolve,reject,timer});ws.send(JSON.stringify({id,method,params}));
  });
  const fixture={playabilityStatus:{status:'OK'},videoDetails:{videoId:'testvideo01',author:'QA Creator'},
    streamingData:{formats:[{url:'fixture-content'}]},playerAds:[{}],adPlacements:[{}],adSlots:[],adBreakHeartbeatParams:{}};
  const body=Buffer.from(JSON.stringify(fixture)).toString('base64');
  ws.onmessage=event=>{
    const m=JSON.parse(event.data);
    if(m.id && pending.has(m.id)){
      const p=pending.get(m.id);pending.delete(m.id);clearTimeout(p.timer);
      m.error?p.reject(new Error(JSON.stringify(m.error))):p.resolve(m.result);
    } else if(m.method==='Fetch.requestPaused') {
      call('Fetch.fulfillRequest',{requestId:m.params.requestId,responseCode:200,
        responseHeaders:[{name:'Content-Type',value:'application/json'}],body}).catch(e=>process.stderr.write(e.message+'\n'));
    }
  };
  try {
    await call('Fetch.enable',{patterns:[{urlPattern:'*votuibe_ad_qa*',requestStage:'Request'}]});
    const result=await call('Runtime.evaluate',{awaitPromise:true,returnByValue:true,expression:`(async()=>{
      const previous=window.__videoShieldCfg,policy=window.__voTuibeEarlyPolicy;
      const initial=window.__voTuibeEarlyAdFieldsRemoved;
      const xhr=(path,type='')=>new Promise((resolve,reject)=>{
        const request=new XMLHttpRequest();request.open('GET',path+'?votuibe_ad_qa=1');request.responseType=type;request.timeout=5000;
        request.onload=()=>resolve(request);request.onerror=()=>reject(Error('XHR error'));request.ontimeout=()=>reject(Error('XHR timeout'));request.send();
      });
      try {
        window.__videoShieldCfg={...previous,enabled:true,safeMode:false,bypassAds:false};
        window.__voTuibeEarlyPolicy={...policy,whitelist:[]};
        const text=await xhr('/youtubei/v1/player');
        const filtered=text.responseText;
        const content=JSON.parse(filtered).streamingData.formats[0].url;
        const consistent=text.response===filtered && text.responseText===filtered;
        const json=await xhr('/youtubei/v1/player','json');
        const jsonClean=!('playerAds' in json.response);
        let nativeError=false;try{json.responseText;}catch(e){nativeError=e.name==='InvalidStateError';}
        const response=await fetch('/youtubei/v1/player?votuibe_ad_qa=fetch');
        const fetchClean=!('playerAds' in await response.json());
        window.__videoShieldCfg.enabled=false;
        const disabled=text.responseText.includes('playerAds');
        window.__videoShieldCfg.enabled=true;window.__videoShieldCfg.safeMode=true;
        const safe=text.responseText.includes('playerAds');
        window.__videoShieldCfg.safeMode=false;window.__voTuibeEarlyPolicy.whitelist=['qa creator'];
        const whitelisted=text.responseText.includes('playerAds');
        window.__voTuibeEarlyPolicy.whitelist=[];
        const unrelated=await xhr('/youtubei/v1/browse');
        return {installed:!!window.__voTuibeEarlyInstalled,textClean:!filtered.includes('playerAds'),content,consistent,
          jsonClean,nativeError,fetchClean,disabled,safe,whitelisted,unrelated:unrelated.responseText.includes('playerAds'),
          fieldsRemoved:window.__voTuibeEarlyAdFieldsRemoved-initial,fetchURL:response.url};
      } finally {window.__videoShieldCfg=previous;window.__voTuibeEarlyPolicy=policy;}
    })()`});
    if(result.exceptionDetails)throw new Error(JSON.stringify(result.exceptionDetails));
    const value=result.result.value;
    for(const key of ['installed','textClean','consistent','jsonClean','nativeError','fetchClean','disabled','safe','whitelisted','unrelated'])assert.equal(value[key],true,key);
    assert.equal(value.content,'fixture-content');assert.equal(value.fieldsRemoved,12);
    assert.ok(value.fetchURL.includes('/youtubei/v1/player'));
    console.log(JSON.stringify(value));
  } finally {
    await call('Fetch.disable').catch(()=>{});
    for(const p of pending.values())clearTimeout(p.timer);
    ws.close();
  }
})().catch(error=>{console.error(error);process.exitCode=1;});
