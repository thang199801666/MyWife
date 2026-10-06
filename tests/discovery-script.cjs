const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const kotlin = fs.readFileSync('app/src/main/java/com/example/videoshield/DiscoveryBridge.kt','utf8');
const script = kotlin.match(/fun build[\s\S]*?"""([\s\S]*?)"""/)[1];
const reports=[]; const timers=[];
function card(id,{ad=false,host='m.youtube.com',title='Guitar lesson'}={}) {
  const anchor={href:`https://${host}/watch?v=${id}`,getAttribute:()=>null};
  return {closest:()=>ad?{}:null,querySelector:s=>s.includes('/watch?')?anchor:s.startsWith('h3')?{textContent:title}:s==='a[href^="/@"]'?{getAttribute:()=>'/@Music',textContent:'Music'}:null};
}
const state={cards:[card('dQw4w9WgXcQ'),card('dQw4w9WgXcQ'),card('xOXolSQcEb4',{ad:true}),card('bad'),card('izGwDsrQ1eQ',{host:'evil.example'})]};
const context={URL,location:{href:'https://m.youtube.com/'},document:{querySelectorAll:()=>state.cards},YouTooBeeDiscovery:{candidates:p=>reports.push(JSON.parse(p))},setTimeout:f=>timers.push(f),setInterval:f=>timers.push(f)};
context.window=context; vm.createContext(context);
vm.runInContext(script.replace('$enabled','true'),context); timers[0]();
assert.equal(reports[0].length,1); assert.equal(reports[0][0].id,'dQw4w9WgXcQ'); assert.equal(reports[0][0].channel,'@Music');
timers[1](); assert.equal(reports.length,2,'unchanged payload retries after a native cooldown');
vm.runInContext(script.replace('$enabled','false'),context); timers[1](); assert.equal(reports.length,2,'disabled learning sends no candidates');
assert.equal(timers.length,2,'reinjection must not add timers');
console.log('PASS discovery: ad/external/invalid/duplicate exclusion, handles, bounded retry, opt-out, single installation');
