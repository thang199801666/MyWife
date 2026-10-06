const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('app/src/main/java/com/example/videoshield/BrowseNavigationBridge.kt','utf8');
const script=source.split('fun build(): String = """')[1].split('""".trimIndent()')[0];
const events={},opened=[],intervals=[];
const s={URL,location:{href:'https://m.youtube.com/'},document:{addEventListener:(name,fn)=>events[name]=fn},
  history:{pushState(a,b,url){s.location.href=new URL(url,s.location.href).href;},replaceState(a,b,url){s.location.href=new URL(url,s.location.href).href;}},
  VoTuibeNavigation:{openVideo:url=>opened.push(url)},setInterval:fn=>intervals.push(fn),addEventListener:(name,fn)=>events[name]=fn};
s.window=s;vm.createContext(s);vm.runInContext(script,s);
s.history.pushState({},'', '/watch?v=testvideo01');assert.equal(opened.length,1);
s.history.replaceState({},'', '/watch?v=testvideo02');assert.equal(opened.length,2);
intervals[0]();assert.equal(opened.length,2);
s.location.href='https://m.youtube.com/shorts/testvideo03';events.popstate();assert.equal(opened.length,3);
let prevented=false;
events.click({button:0,target:{closest:()=>({href:'https://m.youtube.com/watch?v=testvideo04'})},preventDefault(){prevented=true;},stopImmediatePropagation(){}});
assert.ok(prevented);assert.equal(opened.length,4);
s.location.href='https://example.com/watch?v=testvideo05';intervals[0]();assert.equal(opened.length,4);
vm.runInContext(script,s);assert.equal(intervals.length,1);
console.log('PASS SPA pushState/replaceState/popstate, link capture, deduplication, external rejection and single installation');
