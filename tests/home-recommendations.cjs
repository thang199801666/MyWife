const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('app/src/main/java/com/example/videoshield/HomeRecommendationsScript.kt','utf8');
const script=source.split('return """')[1].split('""".trimIndent()')[0];
const nodes=new Map();let replacements=0;
const element=tag=>({tag,dataset:{},style:{},children:[],append(...items){this.children.push(...items);},
 replaceChildren(){this.children=[];replacements++;},remove(){nodes.delete(this.id);}});
const s={location:{pathname:'/'},document:{getElementById:id=>nodes.get(id),createElement:element,
 body:{prepend:e=>nodes.set(e.id,e)}}};vm.createContext(s);
function render(enabled,rows,heading='Dành cho bạn',hint='Xem hoặc tìm kiếm video để khám phá thêm nội dung phù hợp với bạn.'){vm.runInContext(script.replace('$enabled',String(enabled)).replace('$payload',JSON.stringify(rows)).replaceAll('$headingJson',JSON.stringify(heading)).replaceAll('$hintJson',JSON.stringify(hint)),s);}
const rows=[{id:'dQw4w9WgXcQ',title:'<script>alert(1)</script>',channel:'Rick',reason:'Because you watch Rick'}];
render(true,rows);let section=nodes.get('votuibe-home-recommendations');
assert.equal(section.children[2].href,'/watch?v=dQw4w9WgXcQ');
assert.equal(section.children[2].children[1].textContent,rows[0].title);
assert.equal(section.children[2].children[0].loading,'lazy');
render(true,rows);assert.equal(replacements,1,'unchanged ranking should not rebuild DOM');
render(true,rows,'For you','Discover videos');
assert.equal(section.children[1].textContent,'For you','language change updates unchanged rankings');
render(true,[]);assert.equal(section.children.length,3,'empty state stays visible');
render(false,[]);assert.equal(nodes.size,0,'opt-out removes learned feed');
s.location.pathname='/results';render(true,rows);assert.equal(nodes.size,0,'search remains untouched');
console.log('PASS Home cards, text-safe titles, lazy images, unchanged-render cache, empty state, opt-out and route isolation');
