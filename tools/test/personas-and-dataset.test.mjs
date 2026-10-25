import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {validatePersona,validatePersonas} from '../personas/validate-personas.mjs';
import {checkNoPersonaImports} from '../check-no-persona-imports.mjs';
import {generateNotifications,sampleRows} from '../dataset/generate_notifications.mjs';
import {validateDataset,validateRow,deduplicateRows,readDataset,labels,senderTypes,languages} from '../dataset/validate-dataset.mjs';

const root=fileURLToPath(new URL('../../',import.meta.url));
const persona=name=>JSON.parse(fs.readFileSync(new URL(`../personas/${name}.json`,import.meta.url),'utf8'));
function temporary(t){const folder=fs.mkdtempSync(path.join(os.tmpdir(),'dhyan-1e-'));t.after(()=>fs.rmSync(folder,{recursive:true,force:true}));return path.join(folder,'notifications.jsonl');}
for(const name of ['rahul','anika','kabir'])test(`hidden persona ${name} validates`,()=>assert.equal(validatePersona(persona(name)).valid,true));
test('Rahul exactly preserves every 15.1 parameter',()=>{
  const {name,role,...parameters}=persona('rahul');assert.equal(name,'Rahul');assert.equal(role,'developer');
  assert.deepEqual(parameters,{pickupProb:{'social_notif|coding':0.62,'msg_known|coding':0.55,'promo|coding':0.12},dwell:{social:{mu:2.6,sigma:0.7},short_video:{mu:2.8,sigma:0.9}},resume:{coding:{mu:2,sigma:0.6}},fatigueMultiplier:[1,1.2,1.5],focusPeaks:[10,11,15,21],selfInitiatedPerHour:1.4,importantSenders:['mom','teammate_a']});
});
test('assumed personas disclose assumptions and intended synthetic regimes',()=>{
  const kabir=persona('kabir'),anika=persona('anika');
  for(const p of [kabir,anika]){assert.equal(p.assumed,true);assert.ok(p._notes.length);}
  assert.equal(kabir.selfInitiatedPerHour,4.5);assert.ok(Object.values(kabir.pickupProb).every(x=>x<0.2));
  assert.ok(anika.pickupProb['lecture_watch|studying']<anika.pickupProb['shorts|studying']);
  assert.ok(anika.focusPeaks.includes(23));assert.ok(anika.dwell.short_video.mu>persona('rahul').dwell.short_video.mu);
  assert.ok(validatePersonas().every(x=>x.valid));
});
test('persona invalid probabilities, sigmas, hours, multipliers, extras and omissions fail',()=>{
  for(const value of [-0.01,1.01]){const p=persona('rahul');p.pickupProb['social_notif|coding']=value;assert.equal(validatePersona(p).valid,false);}
  for(const family of ['dwell','resume'])for(const value of [0,-0.1]){const p=persona('rahul');Object.values(p[family])[0].sigma=value;assert.equal(validatePersona(p).valid,false);}
  for(const hour of [-1,24,1.5]){const p=persona('rahul');p.focusPeaks=[hour];assert.equal(validatePersona(p).valid,false);}
  for(const multipliers of [[1,2],[1,2,3,4],[1,0,1]]){const p=persona('rahul');p.fatigueMultiplier=multipliers;assert.equal(validatePersona(p).valid,false);}
  const p=persona('rahul');p.selfInitiatedPerHour=-1;assert.equal(validatePersona(p).valid,false);p.selfInitiatedPerHour=1.4;
  p.dwell.social.extra=true;assert.equal(validatePersona(p).valid,false);delete p.dwell.social.extra;
  p.extra=true;assert.equal(validatePersona(p).valid,false);delete p.extra;delete p.role;assert.equal(validatePersona(p).valid,false);
});
test('production trees do not reference hidden ground truth',()=>assert.match(checkNoPersonaImports(root),/PASS/));
test('persona import guard excludes generated caches but still rejects owned source',t=>{
  const file=temporary(t),scratch=path.dirname(file);
  for(const dir of ['phone/.gradle','phone/build','laptop/node_modules','laptop/out','laptop/.cache']){
    fs.mkdirSync(path.join(scratch,dir),{recursive:true});fs.writeFileSync(path.join(scratch,dir,'generated.txt'),'tools/personas');
  }
  assert.match(checkNoPersonaImports(scratch),/PASS/);
  fs.mkdirSync(path.join(scratch,'phone/collectors'),{recursive:true});
  fs.writeFileSync(path.join(scratch,'phone/collectors/Example.kt'),'tools/personas');
  assert.throws(()=>checkNoPersonaImports(scratch),/Production module references/);
});
test('dry run makes zero network calls without credentials and writes 20 covering rows',async t=>{
  const outputPath=temporary(t);let calls=0;
  const result=await generateNotifications({dryRun:true,env:{},outputPath,fetchFn:()=>{calls++;throw new Error('network forbidden');}});
  assert.equal(calls,0);assert.equal(result.valid,true);assert.equal(result.rowCount,20);
  const rows=readDataset(outputPath);assert.equal(rows.length,20);assert.equal(validateDataset(rows).valid,true);
  assert.deepEqual(new Set(rows.map(x=>x.lang)),new Set(languages));assert.deepEqual(new Set(rows.map(x=>x.senderType)),new Set(senderTypes));
  for(const label of labels)assert.equal(result.labelCounts[label],4);
  assert.ok(rows.every(row=>row.synthetic===true));
});
test('dry-run and validator CLIs work from another cwd with credentials absent',()=>{
  const env={...process.env};delete env.OPENAI_API_KEY;delete env.OPENAI_MODEL;
  const result=spawnSync(process.execPath,[path.join(root,'tools/dataset/generate_notifications.mjs'),'--dry-run'],{cwd:os.tmpdir(),env,encoding:'utf8'});
  assert.equal(result.status,0,result.stderr);assert.match(result.stdout,/wrote 20 deduplicated valid rows/);
  const file=path.join(root,'tools/dataset/out/notifications.sample.jsonl');assert.equal(readDataset(file).length,20);
  const validation=spawnSync(process.execPath,[path.join(root,'tools/dataset/validate-dataset.mjs'),file],{encoding:'utf8'});
  assert.equal(validation.status,0,validation.stderr);assert.match(validation.stdout,/PASS; rows=20/);
  for(const row of readDataset(file)){assert.ok(!result.stdout.includes(row.body));assert.ok(!validation.stdout.includes(row.body));}
});
test('dedup normalizes case/whitespace and rejects conflicting labels or ID collisions',()=>{
  const rows=sampleRows(),copy={...rows[0],id:'another-id',title:` ${rows[0].title.toUpperCase()} `,body:` ${rows[0].body} `};
  assert.equal(deduplicateRows([...rows,copy,rows[0]]).length,20);
  assert.throws(()=>deduplicateRows([rows[0],{...copy,label:'promo'}]),/conflicting_duplicate/);
  assert.throws(()=>deduplicateRows([rows[0],{...rows[1],id:rows[0].id}]),/conflicting_duplicate/);
  assert.equal(validateDataset([...rows,copy]).valid,false);
});
test('dataset rejects non-synthetic rows, unknown labels/senders/categories, extra fields and missing coverage',()=>{
  for(const [key,value]of [['synthetic',false],['label','other'],['senderType','stranger'],['appCat','other'],['extra',true]]){
    const row={...sampleRows()[0],[key]:value};assert.equal(validateRow(row).valid,false);
  }
  assert.equal(validateDataset(sampleRows().filter(x=>x.lang==='EN')).valid,false);
  assert.equal(validateDataset([]).valid,false);
});
test('mocked Responses API uses fixed synthetic prompt, chosen model and no stored response',async t=>{
  const outputPath=temporary(t),requests=[];
  const result=await generateNotifications({count:20,model:'selected-model',env:{OPENAI_API_KEY:'FAKE_TEST_KEY',OPENAI_MODEL:'fallback-model'},outputPath,fetchFn:async(url,request)=>{
    requests.push({url,request});return {ok:true,json:async()=>({status:'completed',output:[{type:'message',content:[{type:'output_text',text:JSON.stringify({rows:sampleRows()})}]}]})};
  }});
  assert.equal(result.valid,true);assert.equal(requests.length,1);assert.equal(requests[0].url,'https://api.openai.com/v1/responses');
  const body=JSON.parse(requests[0].request.body);assert.equal(body.model,'selected-model');assert.equal(body.store,false);assert.equal(body.text.format.type,'json_schema');assert.equal(body.text.format.strict,true);
  assert.ok(!requests[0].request.body.includes('FAKE_TEST_KEY'));assert.ok(readDataset(outputPath).every(x=>x.id.startsWith('generated-')));
});
test('live mode validates credentials/model and rejects invalid responses without writing',async t=>{
  const outputPath=temporary(t);let calls=0;const fetchFn=async()=>{calls++;return {ok:false};};
  await assert.rejects(generateNotifications({env:{},outputPath,fetchFn}),/missing_api_key/);
  await assert.rejects(generateNotifications({env:{OPENAI_API_KEY:'FAKE_TEST_KEY'},outputPath,fetchFn}),/missing_model/);assert.equal(calls,0);
  await assert.rejects(generateNotifications({env:{OPENAI_API_KEY:'FAKE_TEST_KEY',OPENAI_MODEL:'selected-model'},count:20,outputPath,fetchFn}),/api_request_failed/);
  assert.ok(!fs.existsSync(outputPath));
  const rows=sampleRows();rows[0].synthetic=false;
  await assert.rejects(generateNotifications({env:{OPENAI_API_KEY:'FAKE_TEST_KEY',OPENAI_MODEL:'selected-model'},count:20,outputPath,fetchFn:async()=>({ok:true,json:async()=>({status:'completed',output:[{type:'message',content:[{type:'output_text',text:JSON.stringify({rows})}]}]})})}),/dataset_row_schema_invalid/);
  assert.ok(!fs.existsSync(outputPath));
});
