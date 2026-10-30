import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {fileURLToPath} from 'node:url';
const output=await build({entryPoints:[fileURLToPath(new URL('../src/pet/pipPolicy.ts',import.meta.url))],bundle:true,format:'esm',platform:'node',write:false});
const {DistractionAssessor,EscalationLadder,BubblePolicy}=await import('data:text/javascript;base64,'+Buffer.from(output.outputFiles[0].text).toString('base64'));
const evidence=(now,extra={})=>({nowMs:now,ts:now,healthy:true,state:'browsing',category:'social',surface:'feed',declaredFocus:false,storagePaused:false,...extra});
const respect={minuteOfDay:720,dayKey:1,breakUntilMs:0,snoozeUntilMs:0,dnd:false,paused:false,calm:false,reducedMotion:false};
function episode(){const a=new DistractionAssessor();a.step(evidence(0,{state:'flowing',category:'coding'}));a.step(evidence(1000));return a;}
function advance(a,end,extra={}){let result;for(let now=2000;now<=end;now+=1000)result=a.step(evidence(now,extra));return result;}
test('all ladder thresholds occur exactly, including a twenty-minute qualified episode',()=>{
 assert.equal(episode().step(evidence(1001)).level,1);
 for(const [duration,level] of [[5000,2],[12000,3],[1200000,4]]){
  const a=episode();const before=advance(a,duration);assert.equal(before.level,level-1);
  assert.equal(a.step(evidence(duration+1000)).level,level);
 }
});
test('watch doubles approved category thresholds; video alone never qualifies',()=>{
 const a=episode();assert.equal(advance(a,10000,{surface:'watch'}).level,1);assert.equal(a.step(evidence(11000,{surface:'watch'})).level,2);
 assert.equal(advance(episode(),181000,{category:'video',surface:'watch'}).level,0);
 assert.equal(advance(new DistractionAssessor(),181000).level,0);
});
test('short gaps preserve dwell without accumulating; long gaps and clock rollback reset',()=>{
 const a=episode();advance(a,31000);const gap=a.step(evidence(32000,{healthy:false}));assert.equal(gap.level,0);
 assert.equal(a.step(evidence(40000)).dwellMs,30000);
 assert.equal(a.step(evidence(41000)).dwellMs,31000);
 a.step(evidence(42000,{healthy:false}));assert.equal(a.step(evidence(65000)).dwellMs,0);
 assert.equal(a.step(evidence(1)).reason,'invalid_clock');
});
test('stale, offline and paused storage cannot accumulate; recovery celebrates once',()=>{
 for(const extra of [{ts:0},{healthy:false},{state:'offline'},{storagePaused:true}])assert.equal(episode().step(evidence(20000,extra)).level,0);
 const a=episode();advance(a,61000);let celebrations=0;
 for(let now=62000;now<150000;now+=1000)if(a.step(evidence(now,{state:'flowing',category:'coding'})).celebrate)celebrations++;
 assert.equal(celebrations,1);
});
const assessment=(level=2,episode=1)=>({level,episode,dwellMs:180000,celebrate:false,reason:'distraction',working:false});
test('respect gates suppress bubbles and motion, including declared focus and gaming DND',()=>{
 for(const extra of [{minuteOfDay:1320},{minuteOfDay:419},{dnd:true},{paused:true},{breakUntilMs:2},{snoozeUntilMs:2}])assert.equal(new EscalationLadder().decide(assessment(3),evidence(1),{...respect,...extra}).showBubble,false);
 for(const extra of [{declaredFocus:true},{state:'flowing'},{state:'offline'},{healthy:false},{storagePaused:true},{category:'games'}])assert.equal(new EscalationLadder().decide(assessment(3),evidence(1,extra),respect).showBubble,false);
 for(const extra of [{calm:true},{reducedMotion:true}]){const r=new EscalationLadder().decide(assessment(3),evidence(1),{...respect,...extra});assert.equal(r.showBubble,true);assert.equal(r.motion,false);}
});
test('cooldown boundaries, hourly cap, daily cap and once-per-episode level four',()=>{
 const l=new EscalationLadder();assert.equal(l.decide(assessment(),evidence(0),respect).showBubble,true);
 assert.equal(l.decide(assessment(),evidence(599999),respect).reason,'cooldown');
 assert.equal(l.decide(assessment(),evidence(600000),respect).showBubble,true);
 l.decide(assessment(3),evidence(600001),respect);l.decide(assessment(4),evidence(600002),respect);
 assert.equal(l.decide(assessment(4,2),evidence(600003),respect).reason,'budget');
 assert.equal(l.decide(assessment(4),evidence(3600000),respect).reason,'episode_limit');
 const day=new EscalationLadder();for(let i=0;i<20;i++)assert.equal(day.decide(assessment(4,i),evidence(i*3600000),respect).showBubble,true);
 assert.equal(day.decide(assessment(4,21),evidence(21*3600000),respect).reason,'budget');
 assert.equal(day.decide(assessment(4,21),evidence(22*3600000),{...respect,dayKey:2}).showBubble,true);
});
test('adaptive threshold requires five nudges and over sixty percent dismissals',()=>{
 const l=new EscalationLadder();for(let i=0;i<5;i++){const category=['social','news','shopping','short_video','social'][i];const now=i*3600000;l.decide(assessment(),evidence(now,{category}),respect);l.feedback(i===2?'accepted':'dismissed',1,2,category,'feed',now);if(i<4)assert.equal(l.thresholdMultiplier(),1);}
 assert.equal(l.thresholdMultiplier(),1.5);
 assert.ok(l.counters.every(row=>Object.values(row).every(value=>typeof value==='number')));
 const a=episode();advance(a,6000);assert.equal(a.step(evidence(7000),1.5).level,1);
});
test('seeded copy is reproducible, has no adjacent repeats and honors reduced text',()=>{
 const a=new BubblePolicy(42),b=new BubblePolicy(42);let previous;
 for(let i=0;i<100;i++){const line=a.next(2);assert.equal(line,b.next(2));assert.notEqual(line,previous);assert.doesNotMatch(line,/https?:|\.exe|CANARY/);previous=line;}
 assert.equal(a.next(1),null);assert.equal(a.next(4,true),null);
});
