const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('app/src/main/java/com/example/videoshield/SearchPreviewScript.kt','utf8');
const script=source.split('fun build(): String = """')[1].split('""".trimIndent()')[0];
const timers=[],events={},links=[];let observers=0,unobserved=0;
const s={URL,WeakMap,WeakSet,innerHeight:700,location:{href:'https://m.youtube.com/results?search_query=music',pathname:'/results'},
 document:{visibilityState:'visible',documentElement:{},querySelectorAll:()=>links,addEventListener:(name,fn)=>events[name]=fn},
 setTimeout:fn=>timers.push(fn),addEventListener:(name,fn)=>events[name]=fn,
 IntersectionObserver:class{constructor(fn){this.fn=fn;observers++;}observe(){}unobserve(){unobserved++;}},
 MutationObserver:class{constructor(fn){s.mutate=(records=[])=>fn(records);}observe(){}}};
s.window=s;
function row(id,src='',top=0){const image={complete:true,naturalWidth:src?320:0,getAttribute:()=>image.src||'',src};
 const link={href:'https://m.youtube.com/watch?v='+id,querySelector:()=>image,getBoundingClientRect:()=>({top,bottom:top+200})};
 image.closest=()=>link;return{image,link};}
const missing=row('dQw4w9WgXcQ'),existing=row('testvideo01','https://i.ytimg.com/vi/testvideo01/hq720.jpg'),
 external=row('testvideo02'),far=row('testvideo03','',1800);
external.link.href='https://example.com/watch?v=testvideo02';
links.push(missing.link,existing.link,external.link,far.link);
vm.createContext(s);vm.runInContext(script,s);timers.shift()();
assert.equal(missing.image.src,'https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg');
assert.equal(existing.image.src,'https://i.ytimg.com/vi/testvideo01/hq720.jpg');
assert.equal(external.image.src,'');assert.equal(far.image.src,'');
existing.image.naturalWidth=0;events.error({target:existing.image});
assert.equal(existing.image.src,'https://i.ytimg.com/vi/testvideo01/mqdefault.jpg');
missing.link.href='https://m.youtube.com/watch?v=testvideo04';missing.image.src='';
s.mutate();s.mutate();assert.equal(timers.length,1);timers.shift()();
assert.equal(missing.image.src,'https://i.ytimg.com/vi/testvideo04/mqdefault.jpg');
s.document.visibilityState='hidden';s.mutate();assert.equal(timers.length,0);
s.mutate([{removedNodes:[{matches:()=>true,querySelectorAll:()=>[]}]}]);assert.equal(unobserved,1);
s.document.visibilityState='visible';s.location.pathname='/watch';s.mutate();assert.equal(timers.length,0);
vm.runInContext(script,s);assert.equal(observers,1);
console.log('PASS missing/broken thumbnails, retain originals, recycled ID, offscreen/external rejection, batching and hidden/non-search idle');
