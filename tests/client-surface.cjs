const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const src=fs.readFileSync('app/src/main/java/com/example/videoshield/ClientSurfaceScript.kt','utf8');
const scripts={};for(const key of ['mini','pip','player'])scripts[key]=src.split(`fun ${key}(`)[1].split('"""')[1];
function fixture(){
  const attrs=new Map(),styles=new Map(),frames=[],events=[],timers=[];
  const root={setAttribute:(k,v)=>attrs.set(k,v),getAttribute:k=>attrs.get(k),hasAttribute:k=>attrs.has(k)};
  const head={appendChild:n=>styles.set(n.id,n)};
  const video={paused:true,ended:false,play(){this.paused=false;return Promise.resolve();}};
  const document={documentElement:root,head,querySelector:()=>video,querySelectorAll:()=>[],getElementById:id=>styles.get(id),
    createElement:()=>({remove(){styles.delete(this.id);}})};
  const ctx={document,VideoShieldBridge:{requestDownload(){}},Event:class{constructor(type){this.type=type;}},
    requestAnimationFrame:f=>frames.push(f),setTimeout:(f,delay)=>timers.push({f,delay}),dispatchEvent:e=>events.push(e.type)};
  ctx.window=ctx;vm.createContext(ctx);
  function run(key,enabled=false,resume=false){vm.runInContext(scripts[key].replaceAll('$enabled',String(enabled)).replaceAll('$resumePlaying',String(resume))
    .replaceAll('${org.json.JSONObject.quote(downloadLabel)}',JSON.stringify('Download')),ctx);}
  return {run,attrs,styles,frames,events,ctx,video,timers};
}
const f=fixture();f.run('player');assert.equal(f.attrs.get('data-votuibe-surface'),'expanded');
f.run('mini',true);assert.equal(f.attrs.get('data-votuibe-surface'),'mini');
f.run('mini',false);assert.equal(f.attrs.get('data-votuibe-surface'),'expanded');
assert(!f.styles.has('youtoobee-mini-style'));
while(f.frames.length)f.frames.shift()();assert.deepEqual(f.events,['resize']);
f.run('mini',true);f.run('pip',true);assert.equal(f.attrs.get('data-votuibe-surface'),'pip');
f.run('player');assert.equal(f.attrs.get('data-votuibe-surface'),'pip','installing player chrome preserves PiP');
f.run('pip',false);assert.equal(f.attrs.get('data-votuibe-surface'),'mini');
f.run('mini',false);assert.equal(f.attrs.get('data-votuibe-surface'),'expanded');
assert(!f.styles.has('youtoobee-pip-style'));
for(const playing of [true,false]){
  const g=fixture();g.run('player');g.run('mini',true,playing);g.run('mini',false);
  while(g.frames.length)g.frames.shift()();
  assert.equal(g.video.paused,!playing,'expanding preserves playback intent');
  g.timers.find(t=>t.delay===400).f();assert.equal(g.ctx.__videoShieldExpandPlaybackWanted,false);
}
console.log('PASS surface transitions: mini restore, resize, PiP layering, expanded mode');
