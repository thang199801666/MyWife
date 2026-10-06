// Run the actual JavaScript embedded in AdBlockScript.kt against deterministic player fixtures.
// This verifies player transitions; live YouTube markup still requires device testing.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/example/videoshield/AdBlockScript.kt'), 'utf8');
const template = source.split('return """')[1].split('""".trimIndent()')[0];
let checks = 0;
function fixture({ ad = false, skip = true, instantSkip = false, safeMode = false, ended = false, readyState = 4, duration = 120, adContainer = false, playbackSpeed = 1.5, mobileLabel = '', mobileOutside = false } = {}) {
  let now = 10000;
  const state = { ad, mobileActive: !!mobileLabel, clicks: 0, reports: [], ended: 0, skipped: 0, segments: 0, qualities: [], qualityChoices:[], scans: 0, buttonScans: 0, timers: new Map(), listeners:new Map() };
  let timerId = 0;
  class Media { pause() { this.paused = true; } }
  const video = { muted: false, playbackRate: 1.5, defaultPlaybackRate: 1.5, currentTime: 12,
    duration, paused: false, ended, readyState, loop: false, play() { this.paused = false; },
    buffered: {length:0}, seeking:false,
    getBoundingClientRect: () => ({left:0,top:0,right:400,bottom:225,width:400,height:225}) };
  Object.setPrototypeOf(video, Media.prototype);
  const player = { classList: { contains: name => state.ad && name === 'ad-showing' }, querySelector: () => state.video,
    querySelectorAll: selector => selector === 'video' && state.video ? [state.video] : [],
    setPlaybackQualityRange: (min, max) => state.qualities.push([min, max]) };
  const moduleStyle = new Map();
  const moduleAttributes = new Map();
  const module = { style:{getPropertyValue:k=>moduleStyle.get(k)||'', getPropertyPriority:()=>'',
    setProperty:(k,v)=>moduleStyle.set(k,v), removeProperty:k=>moduleStyle.delete(k)},
    hasAttribute:k=>moduleAttributes.has(k), setAttribute:(k,v)=>moduleAttributes.set(k,v),
    getAttribute:k=>moduleAttributes.get(k) ?? null, removeAttribute:k=>moduleAttributes.delete(k),
    matches:selector=>selector === '.video-ads,.ytp-ad-module', closest:()=>player };
  const button = { disabled: false, getAttribute: () => null, getClientRects: () => moduleStyle.get('display') === 'none' ? [] : [1],
    click() { state.clicks++; if (instantSkip) state.ad = false; } };
  const mobileButton = { disabled:false, textContent:mobileLabel, getAttribute:()=>null,
    getClientRects:()=>state.mobileActive?[1]:[],
    getBoundingClientRect:()=>({left:250,top:mobileOutside?600:160,right:380,bottom:mobileOutside?650:210,width:130,height:50}),
    click(){ state.clicks++; if(instantSkip) state.mobileActive=false; } };
  state.video = video;
  const cfg = { enabled: true, safeMode, bypassAds: false, autoRepeat: true,
    preferredQuality: 'auto', communitySponsorSkip: true, playbackSpeed };
  const rules = { adSelectors: adContainer ? ['.video-ads'] : [], skipSelectors: ['.skip'], annoyances: {}, version: 300 };
  const sandbox = { URL, Date: { now: () => now }, location: { href: 'https://m.youtube.com/watch?v=testvideo01' },
    document: { title: 'Fixture - YouTube', documentElement: {},
      querySelector: selector => selector === '.html5-video-player' ? player : selector === 'video' ? state.video : selector === '.skip' && skip ? button : null,
      querySelectorAll: selector => { state.scans++; if(selector==='button,[role="button"]') state.buttonScans++; return selector==='button,[role="button"]' && mobileLabel ? [mobileButton] : selector === '.video-ads' && adContainer ? [module] : []; }, getElementById: id => (id === 'youtoobee-pip-style' && state.pip) || (id === 'youtoobee-mini-style' && state.mini) ? {} : null,
      addEventListener(name,listener) {if(!state.listeners.has(name))state.listeners.set(name,[]);state.listeners.get(name).push(listener);} },
    VideoShieldBridge: { onPlaybackState: (...args) => state.reports.push(args), onAdSkipped: () => state.skipped++,
      onPlaybackEnded: () => state.ended++, onQualitySelected: q=>state.qualityChoices.push(q), onSegmentSkipped: () => state.segments++, onCompatibilityReport() {} },
    HTMLMediaElement: Media,
    MutationObserver: class { constructor(callback) { state.mutate = callback; } observe() {} }, requestAnimationFrame() {}, setInterval() {},
    setTimeout(callback, delay) { const id = ++timerId; state.timers.set(id, {callback, delay}); return id; },
    clearTimeout(id) { state.timers.delete(id); } };
  Object.defineProperty(sandbox.document, 'hidden', { configurable: true, get: () => !!state.hidden });
  Object.defineProperty(sandbox.document, 'visibilityState', { configurable: true, get: () => state.hidden ? 'hidden' : 'visible' });
  sandbox.window = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(template.replace('$cfg', JSON.stringify(cfg)).replace('$ruleJson', JSON.stringify(rules)), sandbox);
  assert.equal(typeof sandbox.__videoShieldSweep, 'function', 'script must install successfully');
  return { state, video, button, moduleStyle, sandbox, sweep(gap=800) { now += gap; sandbox.__videoShieldSweep(); } };
}
function test(name, run) { run(); checks++; process.stdout.write(`PASS ${name}\n`); }

test('content sweep scans mobile ad buttons only once and does not retain stale evidence', () => {
  const f=fixture();
  const before=f.state.buttonScans;
  f.sweep();
  assert.equal(f.state.buttonScans,before+1,'reuse mobile detection within one sweep');
  f.state.ad=true;f.sweep();assert.equal(f.state.clicks,1,'fresh ad state on next sweep');
});

test('Vietnamese mobile Skip works without desktop ad classes and never seeks content', () => {
  const f=fixture({mobileLabel:'Bỏ qua',instantSkip:true});
  assert.equal(f.state.clicks,1); assert.equal(f.video.currentTime,12);
  assert.equal(f.video.muted,false); assert.equal(f.video.playbackRate,1.5);
});
test('mobile ad retry does not accelerate shared content media', () => {
  const f=fixture({mobileLabel:'Skip ad'});
  assert.equal(f.state.clicks,1); assert.equal(f.video.currentTime,12);
  assert.equal(f.video.playbackRate,1.5); assert.equal(f.state.reports.length,0);
});
test('skip text outside the video and Skip intro are not ad evidence', () => {
  assert.equal(fixture({mobileLabel:'Bỏ qua',mobileOutside:true}).state.clicks,0);
  assert.equal(fixture({mobileLabel:'Skip intro'}).state.clicks,0);
  assert.equal(fixture({mobileLabel:'Bỏ qua',safeMode:true}).state.clicks,0);
});

test('continuous DOM mutations batch scans without delaying an ad transition indefinitely', () => {
  const f = fixture();
  const scansBefore = f.state.scans;
  for (let i = 0; i < 120; i++) f.state.mutate();
  assert.equal(f.state.scans, scansBefore, 'mutations must not scan synchronously');
  const batch = [...f.state.timers.entries()].filter(([,timer]) => timer.delay === 500);
  assert.equal(batch.length, 1, 'one fixed-deadline scan for a burst of mutations');
  f.state.ad = true;
  f.state.timers.delete(batch[0][0]);
  batch[0][1].callback();
  assert.equal(f.state.clicks, 1, 'the scheduled scan must still handle the newly active ad');
  f.state.mutate();
  assert.equal([...f.state.timers.values()].filter(timer => timer.delay === 500).length, 1,
    'later mutations must be able to schedule another batch');
});

test('content playback does not click unrelated skip buttons', () => {
  const f = fixture();
  assert.equal(f.state.clicks, 0);
  assert.equal(f.video.currentTime, 12);
  assert.equal(f.video.loop, true);
  assert.equal(f.state.reports.length, 1);
});
test('ad playback does not publish content progress, repeat or community skips', () => {
  const f = fixture({ ad: true });
  assert.equal(f.state.clicks, 1);
  assert.equal(f.state.skipped, 1);
  assert.equal(f.state.reports.length, 0);
  assert.equal(f.video.loop, false);
  f.sandbox.__videoShieldSetSegments('testvideo01', [{ start: 0, end: 120, category: 'sponsor' }]);
  f.sweep();
  assert.equal(f.state.clicks, 1, 'throttle repeat clicks');
  assert.equal(f.state.segments, 0);
  f.sweep();
  assert.equal(f.state.clicks, 2, 'retry a stuck skip button');
  assert.equal(f.state.skipped, 1, 'count once per ad episode');
});
test('synchronous skip does not fast forward the main video', () => {
  const f = fixture({ ad: true, instantSkip: true });
  assert.equal(f.video.currentTime, 12);
  assert.equal(f.video.muted, false);
  assert.equal(f.video.playbackRate, 1.5);
});
test('midroll temporarily disables repeat and restores it for content', () => {
  const f = fixture({ skip: false });
  assert.equal(f.video.loop, true);
  f.state.ad = true;
  f.sweep();
  assert.equal(f.video.loop, false);
  f.state.ad = false;
  f.sweep();
  assert.equal(f.video.loop, true);
});
test('user playback speed survives an ad', () => {
  const f = fixture({ ad: true, skip: false });
  f.sandbox.__videoShieldSetRate(2);
  f.state.ad = false;
  f.sweep();
  assert.equal(f.video.playbackRate, 2);
  assert.equal(f.video.muted, false);
});
test('disabling blocking restores original mute and rate', () => {
  const f = fixture({ ad: true, skip: false });
  f.sandbox.__videoShieldCfg.enabled = false;
  f.sweep();
  assert.equal(f.video.playbackRate, 1.5);
  assert.equal(f.video.muted, false);
});
test('replacement video does not inherit the old media mute state', () => {
  const f = fixture({ ad: true, skip: false });
  const replacement = { ...f.video, muted: true, playbackRate: 0.75 };
  f.state.video = replacement;
  f.sweep();
  assert.equal(f.video.muted, false, 'restore detached original video');
  f.state.ad = false;
  f.sweep();
  assert.equal(replacement.muted, false, 'replacement content uses system audio');
  assert.equal(replacement.playbackRate, 1.5, 'replacement content uses the configured speed');
});
test('safe mode leaves ad media untouched', () => {
  const f = fixture({ ad: true, safeMode: true, ended: true });
  assert.equal(f.state.clicks, 0);
  assert.equal(f.video.currentTime, 12);
  assert.equal(f.video.playbackRate, 1.5);
  assert.equal(f.state.ended, 0);
});
test('disabled skip button is not clicked', () => {
  const f = fixture({ ad: true });
  f.button.disabled = true;
  f.sweep(); f.sweep();
  assert.equal(f.state.clicks, 1);
});
test('PiP blocks site pause while native pause retains control', () => {
  const f = fixture();
  f.state.pip = true;
  f.sandbox.__videoShieldPipPlaybackWanted = true;
  f.video.pause();
  assert.equal(f.video.paused, false);
  f.sandbox.__videoShieldControl('pause');
  assert.equal(f.video.paused, true);
  f.sandbox.__videoShieldControl('play');
  f.video.pause();
  assert.equal(f.video.paused, false);
});
test('pause policy leaves normal playback, ended media and ads alone', () => {
  const f = fixture();
  f.sandbox.__videoShieldPipPlaybackWanted = true;
  f.video.pause();
  assert.equal(f.video.paused, true, 'no PiP style means no pause interception');
  f.state.pip = true;
  f.video.paused = false;
  f.video.ended = true;
  f.video.pause();
  assert.equal(f.video.paused, true);
  f.video.ended = false;
  f.video.paused = false;
  f.state.ad = true;
  f.video.pause();
  assert.equal(f.video.paused, true, 'ad transitions must retain their pause behavior');
});
test('mini-player preserves playback while native pause and toggle remain authoritative', () => {
  const f = fixture(); f.state.mini = true;
  f.sandbox.__videoShieldMiniPlaybackWanted = true;
  f.video.pause(); assert.equal(f.video.paused, false);
  f.sandbox.__videoShieldControl('pause'); assert.equal(f.video.paused, true);
  f.sandbox.__videoShieldControl('toggle'); assert.equal(f.video.paused, false);
  f.video.pause(); assert.equal(f.video.paused, false);
  f.sandbox.__videoShieldControl('toggle'); assert.equal(f.video.paused, true);
});
test('mini-player pause protection requires its style and preserves ended and ad transitions', () => {
  const f = fixture(); f.sandbox.__videoShieldMiniPlaybackWanted = true;
  f.video.pause(); assert.equal(f.video.paused, true);
  f.state.mini = true; f.video.paused = false; f.video.ended = true;
  f.video.pause(); assert.equal(f.video.paused, true);
  f.video.ended = false; f.video.paused = false; f.state.ad = true;
  f.video.pause(); assert.equal(f.video.paused, true);
});
test('background visibility follows the opt-in playback preference', () => {
  const f = fixture();
  f.state.hidden = true;
  assert.equal(f.sandbox.document.hidden, true);
  f.sandbox.__videoShieldCfg.backgroundPlayback = true;
  assert.equal(f.sandbox.document.hidden, false);
  assert.equal(f.sandbox.document.visibilityState, 'visible');
  f.sandbox.__videoShieldCfg.backgroundPlayback = false;
  assert.equal(f.sandbox.document.visibilityState, 'hidden');
});
test('unloaded video cannot overwrite saved playback progress', () => {
  const f = fixture({ readyState: 0, duration: NaN });
  f.video.currentTime = 0;
  f.sweep();
  assert.equal(f.state.reports.length, 0);
  f.video.readyState = 4;
  f.video.duration = 120;
  f.video.currentTime = 42;
  f.sweep();
  assert.equal(f.state.reports.length, 1);
  assert.equal(f.state.reports[0][5], 42000);
});
test('live duration produces a finite bridge payload', () => {
  const f = fixture({ duration: Infinity });
  assert.equal(f.state.reports.length, 1);
  assert.equal(f.state.reports[0][5], 12000);
  assert.equal(f.state.reports[0][6], 0);
});
test('Auto releases a previous fixed quality range', () => {
  const f = fixture();
  f.sandbox.__videoShieldCfg.preferredQuality = 'hd720';
  f.sandbox.__videoShieldPreferencesChanged();
  f.sweep();
  assert.deepEqual(f.state.qualities.at(-1), ['hd720', 'hd720']);
  f.sandbox.__videoShieldCfg.preferredQuality = 'auto';
  f.sandbox.__videoShieldPreferencesChanged();
  f.sweep();
  assert.deepEqual(f.state.qualities.at(-1), ['auto', 'auto']);
});
test('community segments skip again after rewinding or looping', () => {
  const f = fixture();
  f.sandbox.__videoShieldSetSegments('testvideo01', [{start:10, end:20, category:'sponsor'}]);
  f.sweep();
  assert.equal(f.video.currentTime, 20.03);
  assert.equal(f.state.segments, 1);
  f.sweep();
  assert.equal(f.state.segments, 1, 'no duplicate report after a normal skip');
  f.video.currentTime = 12;
  f.sweep();
  assert.equal(f.video.currentTime, 20.03);
  assert.equal(f.state.segments, 2, 'rewind into the segment should skip again');
  f.video.currentTime = 0;
  f.sweep();
  f.video.currentTime = 12;
  f.sweep();
  assert.equal(f.state.segments, 3, 'new repeat pass should skip again');
});
test('page ad filtering preserves player Skip controls across ad transitions', () => {
  const f = fixture({adContainer:true, instantSkip:true});
  assert.notEqual(f.moduleStyle.get('display'), 'none', 'idle player ad module must stay available');
  f.state.ad = true;
  f.sweep();
  assert.equal(f.state.clicks, 1, 'a Skip inside the player ad module must remain visible');
  assert.equal(f.state.ad, false);
  assert.equal(f.video.currentTime, 12, 'successful Skip must preserve content time');
});
test('manual replay reports a new ended event for queue advancement', () => {
  const f = fixture();
  f.sandbox.__videoShieldCfg.autoRepeat = false;
  f.video.ended = true;
  f.sweep();
  assert.equal(f.state.ended, 1);
  f.sweep();
  assert.equal(f.state.ended, 1, 'ended callbacks must not repeat while the video remains ended');
  f.video.ended = false;
  f.sweep();
  f.video.ended = true;
  f.sweep();
  assert.equal(f.state.ended, 2, 'a replay is a new pass through the queue');
});
test('configured speed survives media reload and video element replacement', () => {
  const f = fixture({playbackSpeed:1.25});
  assert.equal(f.video.playbackRate, 1.25);
  f.video.readyState = 0;
  f.video.playbackRate = 1;
  f.video.defaultPlaybackRate = 1;
  f.sweep();
  assert.equal(f.video.playbackRate, 1.25, 'apply configured speed even before replacement media is ready');
  f.video.readyState = 4;
  f.sweep();
  assert.equal(f.video.playbackRate, 1.25);
  assert.equal(f.video.defaultPlaybackRate, 1.25);
  f.state.video = {...f.video, playbackRate:1, defaultPlaybackRate:1};
  f.sweep();
  assert.equal(f.state.video.playbackRate, 1.25);
  f.sandbox.__videoShieldSetRate(2);
  f.sweep();
  assert.equal(f.state.video.playbackRate, 2, 'native speed changes update the sweep preference');
});
test('configured content speed does not interfere with ad acceleration', () => {
  const f = fixture({ad:true, skip:false, playbackSpeed:1.25});
  assert.equal(f.video.playbackRate, 16);
  f.sweep();
  assert.equal(f.video.playbackRate, 16);
  f.state.ad = false;
  f.sweep();
  assert.equal(f.video.playbackRate, 1.25);
});
test('paused seeks update native progress without repeated idle reports', () => {
  const f = fixture();
  f.sandbox.__videoShieldControl('pause'); f.sweep();
  const before = f.state.reports.length;
  f.sandbox.__videoShieldSetPosition(45); f.sweep();
  assert.equal(f.state.reports.length, before + 1);
  assert.equal(f.state.reports.at(-1)[5], 45000);
  f.sweep(); f.sweep();
  assert.equal(f.state.reports.length, before + 1, 'unchanged paused media stays quiet');
  f.sandbox.__videoShieldControl('seekBack'); f.sweep();
  assert.equal(f.state.reports.at(-1)[5], 35000);
});
test('paused ad seeks do not overwrite content progress', () => {
  const f = fixture();
  f.sandbox.__videoShieldControl('pause'); f.sweep();
  const before = f.state.reports.length;
  f.state.ad = true; f.video.currentTime = 3; f.sweep();
  assert.equal(f.state.reports.length, before);
});
test('content uses full player audio while previews and ad muting remain separate', () => {
  const f = fixture();
  f.video.muted = true; f.video.volume = 0.2; f.sweep();
  assert.equal(f.video.muted, false);
  assert.equal(f.video.volume, 1);
  f.state.ad = true; f.sweep();
  assert.equal(f.video.muted, true, 'ad suppression may temporarily mute');
  f.state.ad = false; f.sweep();
  assert.equal(f.video.muted, false, 'content sound returns after the ad');
  delete f.sandbox.VideoShieldBridge;
  f.video.muted = true; f.video.volume = 0.2; f.sweep();
  assert.equal(f.video.muted, true, 'browse previews do not become audible');
  assert.equal(f.video.volume, 0.2);
});
test('Highest waits for source qualities and chooses the actual maximum without a cap', () => {
  const f = fixture();
  const player = f.sandbox.document.querySelector('.html5-video-player');
  let levels = ['auto'];
  player.getAvailableQualityLevels = () => levels;
  f.sandbox.__videoShieldCfg.preferredQuality = 'highres';
  f.sandbox.__videoShieldPreferencesChanged();
  const before = f.state.qualities.length;
  f.sweep();
  assert.equal(f.state.qualities.length, before, 'retry when source list is not ready');
  levels = ['medium', 'hd1080', 'auto', 'hd4320', 'hd2160'];
  f.sweep();
  assert.deepEqual(f.state.qualities.at(-1), ['hd4320', 'hd4320']);
  const applied = f.state.qualities.length;
  f.sweep();
  assert.equal(f.state.qualities.length, applied, 'do not force quality repeatedly');
  f.sandbox.__videoShieldCfg.preferredQuality = 'medium';
  f.sandbox.__videoShieldPreferencesChanged(); f.sweep();
  assert.deepEqual(f.state.qualities.at(-1), ['medium', 'medium'], 'manual choice remains usable');
});
function adaptiveFixture() {
  const f=fixture(); const player=f.sandbox.document.querySelector('.html5-video-player');
  player.getAvailableQualityLevels=()=>['hd1080','hd720','large','medium','tiny','auto'];
  let preferred='auto';
  player.setPlaybackQuality=q=>{preferred=q;}; player.getPreferredQuality=()=>preferred;
  f.prefer=q=>{preferred=q;};
  f.sandbox.__videoShieldCfg.preferredQuality='adaptive'; f.sandbox.__videoShieldPreferencesChanged();f.sweep();
  return f;
}
test('adaptive starts highest but leaves room for automatic lower qualities',()=>{
  const f=adaptiveFixture();assert.deepEqual(f.state.qualities.at(-1),['tiny','hd1080']);
  assert.equal(f.sandbox.__videoShieldQualityState().target,'hd1080');
});
test('sustained low-buffer stall downgrades once and respects cooldown',()=>{
  const f=adaptiveFixture();f.video.readyState=2;
  for(let i=0;i<5;i++)f.sweep();
  assert.deepEqual(f.state.qualities.at(-1),['tiny','hd720']);
  for(let i=0;i<10;i++)f.sweep();
  assert.equal(f.sandbox.__videoShieldQualityState().target,'hd720');
  for(let i=0;i<9;i++)f.sweep();
  assert.equal(f.sandbox.__videoShieldQualityState().target,'large');
});
test('pause, seeking, buffered content and background timer gaps do not downgrade',()=>{
  for(const reason of ['pause','seek','buffer','background','progress']) {
    const f=adaptiveFixture();f.video.readyState=2;
    if(reason==='pause')f.video.paused=true;
    if(reason==='seek')f.video.seeking=true;
    if(reason==='buffer')f.video.buffered={length:1,start:()=>0,end:()=>100};
    for(let i=0;i<30;i++){if(reason==='progress')f.video.currentTime+=0.8;f.sweep(reason==='background'?5000:800);}
    assert.equal(f.sandbox.__videoShieldQualityState().target,'hd1080',reason);
  }
});
test('unrelated preference changes preserve the adaptive downgrade',()=>{
  const f=adaptiveFixture();f.video.readyState=2;for(let i=0;i<5;i++)f.sweep();
  const before=f.state.qualities.length;f.sandbox.__videoShieldCfg.autoRepeat=false;
  f.sandbox.__videoShieldPreferencesChanged();f.sweep();
  assert.equal(f.state.qualities.length,before);
  assert.equal(f.sandbox.__videoShieldQualityState().target,'hd720');
});
test('manual 720 replaces highest constraints and never gets adaptive overrides',()=>{
  const f=adaptiveFixture();f.sandbox.__videoShieldCfg.preferredQuality='hd720';
  f.sandbox.__videoShieldPreferencesChanged();f.sweep();
  assert.deepEqual(f.state.qualities.slice(-2),[['auto','auto'],['hd720','hd720']]);
  const before=f.state.qualities.length;f.video.readyState=2;for(let i=0;i<40;i++)f.sweep();
  assert.equal(f.state.qualities.length,before);
});
test('website manual quality is reported and applied as a fixed choice',()=>{
  const f=adaptiveFixture();f.prefer('hd720');for(let i=0;i<5;i++)f.sweep();
  assert.deepEqual(f.state.qualityChoices,['hd720']);
  assert.equal(f.sandbox.__videoShieldQualityState().manual,true);
  assert.equal(f.sandbox.__videoShieldQualityState().target,'hd720');
  assert.deepEqual(f.state.qualities.at(-1),['hd720','hd720']);
});
test('website Auto restores the adaptive mode and reports the choice',()=>{
  const f=adaptiveFixture();f.sandbox.__videoShieldCfg.preferredQuality='hd720';
  f.sandbox.__videoShieldPreferencesChanged();f.sweep();f.prefer('auto');
  for(let i=0;i<5;i++)f.sweep();
  assert.deepEqual(f.state.qualityChoices,['adaptive']);
  assert.equal(f.sandbox.__videoShieldQualityState().mode,'adaptive');
});
test('explicit reselecting adaptive restarts highest even after a website choice',()=>{
  const f=adaptiveFixture();f.prefer('hd720');for(let i=0;i<4;i++)f.sweep();
  f.sandbox.__videoShieldCfg.preferredQuality='adaptive';
  f.sandbox.__videoShieldResetQuality();
  assert.equal(f.sandbox.__videoShieldQualityState().manual,false);
  assert.deepEqual(f.state.qualities.at(-1),['tiny','hd1080']);
});
test('unsupported manual resolution selects the nearest lower source',()=>{
  const f=adaptiveFixture();f.sandbox.__videoShieldCfg.preferredQuality='hd1440';
  f.sandbox.__videoShieldPreferencesChanged();f.sweep();
  assert.deepEqual(f.state.qualities.at(-1),['hd1080','hd1080']);
});
test('manual resolution waits for metadata before selecting a supported source',()=>{
  const f=adaptiveFixture(),p=f.sandbox.document.querySelector('.html5-video-player');let levels=['auto'];
  p.getAvailableQualityLevels=()=>levels;f.sandbox.__videoShieldCfg.preferredQuality='hd720';
  f.sandbox.__videoShieldPreferencesChanged();const before=f.state.qualities.length;f.sweep();
  assert.equal(f.state.qualities.length,before);
  levels=['large','medium'];f.sweep();assert.deepEqual(f.state.qualities.at(-1),['large','large']);
});
test('stable buffered playback raises quality one step at a time',()=>{
  const f=adaptiveFixture();f.video.readyState=2;for(let i=0;i<25;i++)f.sweep();
  assert.equal(f.sandbox.__videoShieldQualityState().target,'large');
  f.video.readyState=4;f.video.buffered={length:1,start:()=>0,end:()=>f.video.currentTime+20};
  for(let i=0;i<30;i++){f.video.currentTime+=0.8;f.sweep();}
  assert.equal(f.sandbox.__videoShieldQualityState().target,'large','no early upgrade');
  for(let i=0;i<10;i++){f.video.currentTime+=0.8;f.sweep();}
  assert.equal(f.sandbox.__videoShieldQualityState().target,'hd720');
  for(let i=0;i<40;i++){f.video.currentTime+=0.8;f.sweep();}
  assert.equal(f.sandbox.__videoShieldQualityState().target,'hd1080');
  assert.deepEqual(f.state.qualityChoices,[],'automatic changes must not persist as manual');
});
test('manual choice persists on the next video and never upgrades when healthy',()=>{
  const f=adaptiveFixture();f.sandbox.__videoShieldCfg.preferredQuality='hd720';
  f.sandbox.__videoShieldPreferencesChanged();f.sweep();
  f.sandbox.location.href='https://m.youtube.com/watch?v=testvideo02';f.sweep();
  f.video.buffered={length:1,start:()=>0,end:()=>f.video.currentTime+20};
  for(let i=0;i<80;i++){f.video.currentTime+=0.8;f.sweep();}
  assert.equal(f.sandbox.__videoShieldQualityState().mode,'hd720');
  assert.equal(f.sandbox.__videoShieldQualityState().target,'hd720');
});
test('expand resize pause protection never defeats explicit native Pause',()=>{
  const f=fixture();f.sandbox.document.documentElement.getAttribute=()=> 'expanded';
  f.sandbox.__videoShieldExpandPlaybackWanted=true;f.video.pause();assert.equal(f.video.paused,false);
  f.sandbox.__videoShieldControl('pause');assert.equal(f.video.paused,true);
  assert.equal(f.sandbox.__videoShieldExpandPlaybackWanted,false);
});
test('native next-video handling disables the independent website countdown',()=>{
  const f=fixture(),p=f.sandbox.document.querySelector('.html5-video-player'),states=[];
  f.sandbox.__videoShieldCfg.autoRepeat=false;
  p.setAutonavState=s=>states.push(s);f.sweep();f.sweep();
  assert.deepEqual(states,[1]);
  for(let i=0;i<4;i++)f.sweep();assert.deepEqual(states,[1,1]);
});
function restoreQuality(f) {
  const timer=[...f.state.timers].find(([,t])=>t.delay===120 || t.delay===250);
  assert(timer,'quality restore scheduled');
  f.state.timers.delete(timer[0]);timer[1].callback();
}
function changeWithPause(f) {
  const p=f.sandbox.document.querySelector('.html5-video-player'),original=p.setPlaybackQuality;
  p.setPlaybackQuality=q=>{original(q);f.video.pause();};
  f.sandbox.__videoShieldCfg.preferredQuality='hd720';
  f.sandbox.__videoShieldPreferencesChanged();f.sweep();
}
test('quality setter pauses content but playback resumes without a seek',()=>{
  const f=adaptiveFixture(),position=f.video.currentTime;
  changeWithPause(f);assert.equal(f.video.paused,true);restoreQuality(f);
  assert.equal(f.video.paused,false);assert.equal(f.video.currentTime,position);
  f.video.pause();restoreQuality(f);assert.equal(f.video.paused,false,'later stream reload pause also resumes');
});
test('quality selection remembers playback before a native menu pauses the renderer',()=>{
  const f=adaptiveFixture();f.sandbox.__videoShieldPrepareQualityChange();f.video.pause();
  changeWithPause(f);restoreQuality(f);assert.equal(f.video.paused,false);
});
test('quality changes retain an explicit paused state even if the setter autoplays',()=>{
  const f=adaptiveFixture();f.sandbox.__videoShieldControl('pause');
  const p=f.sandbox.document.querySelector('.html5-video-player'),original=p.setPlaybackQuality;
  p.setPlaybackQuality=q=>{original(q);f.video.play();};
  f.sandbox.__videoShieldCfg.preferredQuality='hd720';f.sandbox.__videoShieldPreferencesChanged();f.sweep();
  restoreQuality(f);assert.equal(f.video.paused,true);
});
test('native Pause and website interaction cancel a pending quality resume',()=>{
  for(const via of ['native','website']) {
    const f=adaptiveFixture();changeWithPause(f);
    if(via==='native')f.sandbox.__videoShieldControl('pause');
    else for(const listener of f.state.listeners.get('pointerdown'))listener({target:{closest:()=>null}});
    assert.equal([...f.state.timers.values()].filter(t=>t.delay===120 || t.delay===250).length,0);
    assert.equal(f.video.paused,true);
  }
});
test('quality resume never starts another video or an ad and expires',()=>{
  for(const cause of ['navigate','ad','timeout','ended']) {
    const f=adaptiveFixture();changeWithPause(f);
    if(cause==='navigate')f.sandbox.location.href='https://m.youtube.com/watch?v=testvideo02';
    if(cause==='ad')f.state.ad=true;
    if(cause==='timeout')f.sweep(9000);
    if(cause==='ended')f.video.ended=true;
    restoreQuality(f);assert.equal(f.video.paused,true,cause);
  }
});
test('website quality menu preserves the intent before the site pauses playback',()=>{
  const f=adaptiveFixture();
  for(const listener of f.state.listeners.get('pointerdown'))listener({target:{closest:()=>({})}});
  f.prefer('hd720');f.video.pause();f.sweep(3000);f.sweep();restoreQuality(f);
  assert.equal(f.video.paused,false);assert.equal(f.sandbox.__videoShieldQualityState().target,'hd720');
});
test('initial quality metadata does not force a new autoplay video to stay paused',()=>{
  const f=adaptiveFixture();f.sandbox.__videoShieldCancelQualityChange();
  f.sandbox.location.href='https://m.youtube.com/watch?v=testvideo02';f.video.paused=true;
  f.sweep();f.video.play();
  assert.equal([...f.state.timers.values()].filter(t=>t.delay===120 || t.delay===250).length,0);
  assert.equal(f.video.paused,false);
});
process.stdout.write(`${checks} player regression fixtures passed\n`);
