const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const src=fs.readFileSync('app/src/main/java/com/example/videoshield/NextVideoScript.kt','utf8').split('"""')[1]
  .replace('${org.json.JSONObject.quote(completedId)}',JSON.stringify('currentv01')).replaceAll("${'$'}",'$');
function link(href,{promoted=false,title='Next video'}={}){
  const row={querySelector:()=>({textContent:title})};
  return {href,textContent:title,getAttribute:()=>title,closest:selector=>selector.includes('promoted')?(promoted?{}:null):row};
}
function next(rows){const result=vm.runInNewContext(src,{URL,location:{href:'https://m.youtube.com/watch?v=currentv01'},
  document:{querySelectorAll:()=>rows}});return result?JSON.parse(result):null;}
assert.equal(next([]),null);
assert.equal(next([link('https://youtube.com/watch?v=currentv01')]),null);
assert.equal(next([link('https://youtube.com.evil.test/watch?v=nextvideo01'),link('http://youtube.com/watch?v=nextvideo01')]),null);
assert.equal(next([link('https://youtube.com/watch?v=nextvideo01',{promoted:true})]),null);
assert.equal(next([link('https://youtube.com/shorts/nextvideo01')]),null);
assert.deepEqual(next([link('/watch?v=nextvideo01',{title:'A "quoted" title'})]),
  {url:'https://m.youtube.com/watch?v=nextvideo01',title:'A "quoted" title'});
assert.equal(next(Array.from({length:200},()=>link('https://youtube.com/watch?v=currentv01')).concat(link('/watch?v=nextvideo01'))),null);
console.log('PASS next video: trusted related source, same-video/ad/foreign/Shorts exclusion, title, bounded scan');
