const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('app/src/main/java/com/example/videoshield/EarlyAdScript.kt','utf8');
const template=source.split('return """')[1].split('""".trimIndent()')[0];
const player=()=>({playabilityStatus:{status:'OK'},videoDetails:{videoId:'testvideo01',author:'Creator'},
  streamingData:{formats:[{url:'content-media'}]},playerAds:[{ad:'pre'}],adPlacements:[{ad:'mid'}],adSlots:[]});
function fixture(policy={enabled:true,safeMode:false,whitelist:[]}) {
  class XHR {
    constructor() { this.readyState=4; this.status=200; this.responseType=''; this.responseURL='https://m.youtube.com/youtubei/v1/player'; this.body=JSON.stringify(player()); }
    get responseText() { if(this.responseType && this.responseType!=='text') throw new Error('InvalidStateError'); return this.body; }
    get response() { return this.responseType==='json' ? this.object : this.responseType==='arraybuffer' ? this.binary : this.body; }
  }
  const sandbox={URL,Headers,Response,location:{href:'https://m.youtube.com/watch?v=testvideo01',origin:'https://m.youtube.com'},
    XMLHttpRequest:XHR,nativeParses:0,
    fetch:async()=>{
      const response=new Response(JSON.stringify(player()),{headers:{'content-type':'application/json','content-encoding':'gzip','content-length':'999'}});
      Object.defineProperty(response,'url',{value:'https://m.youtube.com/youtubei/v1/player'});
      Object.defineProperty(response,'redirected',{value:true});
      return response;
    }};
  sandbox.window=sandbox; vm.createContext(sandbox);
  vm.runInContext('const nativeParse=JSON.parse; JSON.parse=function(...args){nativeParses++;return nativeParse.apply(this,args)};',sandbox);
  vm.runInContext(template.replace('$policy',JSON.stringify(policy)),sandbox);
  return sandbox;
}
(async()=>{
  const s=fixture(); s.ytInitialPlayerResponse=player();
  assert.equal(s.ytInitialPlayerResponse.playerAds,undefined);
  assert.equal(s.ytInitialPlayerResponse.streamingData.formats[0].url,'content-media');
  assert.equal(s.ytInitialPlayerResponse.videoDetails.videoId,'testvideo01');
  const parsed=vm.runInContext(`JSON.parse(${JSON.stringify(JSON.stringify({playerResponse:player()}))})`,s);
  assert.equal(parsed.playerResponse.adPlacements,undefined);
  const response=await s.fetch('/youtubei/v1/player'); const data=await response.json();
  assert.equal(data.adSlots,undefined); assert.equal(response.status,200);
  assert.equal(response.url,'https://m.youtube.com/youtubei/v1/player');
  assert.equal(response.redirected,true);
  assert.equal(response.headers.get('content-encoding'),null);
  assert.equal(response.headers.get('content-length'),null);
  const external=await s.fetch('https://example.com/youtubei/v1/player');
  assert.ok((await external.json()).playerAds);
  for(const policy of [{enabled:false,safeMode:false,whitelist:[]},{enabled:true,safeMode:true,whitelist:[]},
    {enabled:true,safeMode:false,whitelist:['creator']}]) {
    const f=fixture(policy);f.ytInitialPlayerResponse=player(); assert.ok(f.ytInitialPlayerResponse.playerAds);
  }
  const unrelated=vm.runInContext('JSON.parse(\'{"playerAds":[1],"title":"article"}\')',s);
  assert.equal(unrelated.playerAds[0],1);
  const xhr=new s.XMLHttpRequest();
  const before=s.nativeParses;
  const raw=xhr.body;
  const filtered=xhr.responseText;
  assert.equal(JSON.parse(filtered).playerAds,undefined);
  assert.equal(JSON.parse(filtered).streamingData.formats[0].url,'content-media');
  assert.equal(xhr.response,filtered);
  assert.equal(xhr.responseText,filtered);
  assert.equal(s.nativeParses,before+1,'only one parse for repeated response/responseText reads');
  s.__videoShieldCfg={enabled:false};
  assert.equal(xhr.responseText,raw,'disabling blocking immediately returns untouched text');
  s.__videoShieldCfg={enabled:true,bypassAds:true};
  assert.equal(xhr.responseText,raw);
  s.__videoShieldCfg={enabled:true};
  s.__voTuibeEarlyPolicy.whitelist=['creator'];
  assert.equal(xhr.responseText,raw,'whitelist changes apply to cached responses');
  s.__voTuibeEarlyPolicy.whitelist=[];
  assert.equal(xhr.responseText,filtered);
  xhr.body=JSON.stringify({...player(),videoDetails:{videoId:'secondvideo',author:'Creator'}});
  assert.equal(JSON.parse(xhr.responseText).videoDetails.videoId,'secondvideo','XHR reuse refreshes its cache');
  xhr.responseType='json';xhr.object=player();
  assert.equal(xhr.response.playerAds,undefined);
  assert.throws(()=>xhr.responseText,/InvalidStateError/,'native getter exception is preserved');
  xhr.responseType='arraybuffer';xhr.binary=new Uint8Array([1,2,3]).buffer;
  assert.equal(xhr.response,xhr.binary,'binary response is untouched');
  xhr.responseType='';xhr.body=raw;
  for(const url of ['https://example.com/youtubei/v1/player','https://m.youtube.com/api/stats/playback','https://m.youtube.com/youtubei/v1/player_extra']) {
    xhr.responseURL=url;assert.equal(xhr.responseText,raw);
  }
  xhr.responseURL='https://m.youtube.com/youtubei/v1/player';
  xhr.readyState=3;assert.equal(xhr.responseText,raw,'partial JSON is untouched');
  xhr.readyState=4;xhr.status=403;assert.equal(xhr.responseText,raw,'error response is untouched');
  xhr.status=200;xhr.body='invalid json';assert.equal(xhr.responseText,'invalid json');
  xhr.body=' '.repeat(4*1024*1024+1);assert.equal(xhr.responseText,xhr.body,'oversized text is not parsed');
  for(const policy of [{enabled:false,safeMode:false,whitelist:[]},{enabled:true,safeMode:true,whitelist:[]},
    {enabled:true,safeMode:false,whitelist:['creator']}]) {
    const f=fixture(policy),request=new f.XMLHttpRequest(); assert.equal(request.responseText,request.body);
  }
  const frozen=Object.freeze(player());s.ytInitialPlayerResponse=frozen;
  assert.ok(s.ytInitialPlayerResponse.playerAds,'read-only object does not break page initialization');
  s.__videoShieldCfg={enabled:false}; s.ytInitialPlayerResponse=player();assert.ok(s.ytInitialPlayerResponse.playerAds);
  console.log('PASS early ad filtering: initial/JSON/fetch/XHR, metadata, content preservation, cache/reuse, dynamic policy, binary/partial/error/oversized responses and frozen objects');
})().catch(error=>{console.error(error);process.exitCode=1;});
