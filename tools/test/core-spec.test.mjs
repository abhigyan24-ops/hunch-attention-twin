import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { validateEvent } from '../validate-events.mjs';
import { checkSchemaSync } from '../check-schema-sync.mjs';
import { checkTwinPurity } from '../check-twin-purity.mjs';
import { checkNoPersonaImports } from '../check-no-persona-imports.mjs';

const root = fileURLToPath(new URL('../../', import.meta.url));
const read = name => JSON.parse(fs.readFileSync(path.join(root, 'core-spec', name), 'utf8'));
const validFiles = fs.readdirSync(path.join(root, 'core-spec/fixtures/valid'));
const invalidFiles = fs.readdirSync(path.join(root, 'core-spec/fixtures/invalid'));
const fixture = type => read(`fixtures/valid/${type}.json`);
for (const file of validFiles) test(`valid fixture: ${file}`, () => assert.deepEqual(validateEvent(read(`fixtures/valid/${file}`)), { valid: true, errors: [] }));
for (const file of invalidFiles) test(`invalid fixture: ${file}`, () => assert.equal(validateEvent(read(`fixtures/invalid/${file}`)).valid, false));

test('exactly 20 distinct event types and 8 negative fixtures', () => {
  assert.equal(validFiles.length, 20); assert.equal(invalidFiles.length, 8);
  assert.deepEqual(validFiles.map(f => read(`fixtures/valid/${f}`).type).sort(), read('event.schema.json').properties.type.enum.slice().sort());
});
test('required envelope and payload fields cannot be omitted for any event type', () => {
  const schema = read('event.schema.json');
  for (const file of validFiles) {
    const original = read(`fixtures/valid/${file}`);
    for (const field of schema.required) {
      const e = structuredClone(original); delete e[field]; assert.equal(validateEvent(e).valid, false, `${original.type}.${field}`);
    }
    const branch = schema.allOf.find(b => b.if.properties.type.const === original.type);
    const payload = branch.then.properties.payload;
    const required = payload.required ?? read('context-snapshot.schema.json').required;
    for (const field of required) {
      const e = structuredClone(original); delete e.payload[field]; assert.equal(validateEvent(e).valid, false, `${original.type}.payload.${field}`);
    }
  }
});
test('all forbidden keys detected recursively, including arrays; diagnostics contain no content', () => {
  for (const key of read('forbidden-fields.json')) {
    const event = fixture('screen_on'); event.payload.nested = [{ deeper: { [key]: 'DO_NOT_LOG_SENTINEL' } }];
    const result = validateEvent(event);
    assert.ok(result.errors.includes('forbidden_field'));
    assert.ok(!JSON.stringify(result).includes('DO_NOT_LOG_SENTINEL'));
  }
});
test('8 KB boundary measures UTF-8 bytes of the entire event', () => {
  const event = fixture('notif_posted'); event.payload.key = '';
  const baseline = Buffer.byteLength(JSON.stringify(event));
  event.payload.key = 'x'.repeat(8192 - baseline);
  assert.equal(Buffer.byteLength(JSON.stringify(event)), 8192);
  assert.equal(validateEvent(event).valid, true);
  event.payload.key += 'x'; assert.ok(validateEvent(event).errors.includes('event_too_large'));
  event.payload.key = 'ध'.repeat(2800); assert.ok(validateEvent(event).errors.includes('event_too_large'));
});
test('negative durations, counts, timestamps, enum values, and extra fields rejected', () => {
  for (const [type, field] of [['app_background','durationMs'],['scroll_burst','windowSec'],['window_change','minutesInPrev'],['idle_start','idleSec'],['idle_end','idleSec'],['resume_first_edit','delayMs'],['declared_session','plannedMin'],['edit_burst','saves']]) {
    const e = fixture(type); e.payload[field] = -1; assert.equal(validateEvent(e).valid, false, `${type}.${field}`);
  }
  for (const field of ['seq', 'ts', 'ingestedTs']) { const e = fixture('screen_on'); e[field] = -1; assert.equal(validateEvent(e).valid, false); }
  for (const field of ['device', 'source', 'type']) { const e = fixture('screen_on'); e[field] = 'invalid'; assert.equal(validateEvent(e).valid, false); }
  const e = fixture('screen_on'); e.unlisted = true; assert.equal(validateEvent(e).valid, false);
  for (const score of [-0.1, 1.5]) { const e = fixture('notif_posted'); e.payload.urgencyScore = score; assert.equal(validateEvent(e).valid, false); }
  for (const score of [0, 1]) { const e = fixture('notif_posted'); e.payload.urgencyScore = score; assert.equal(validateEvent(e).valid, true); }
});
test('ingestedTs optional at emission; source-specific payload and UUID enforced', () => {
  const e = fixture('screen_on'); delete e.ingestedTs; assert.equal(validateEvent(e).valid, true);
  e.source = 'browser'; assert.equal(validateEvent(e).valid, false);
  e.source = 'persona'; assert.equal(validateEvent(e).valid, true);
  e.id = 'invalid'; assert.equal(validateEvent(e).valid, false);
});
test('ContextSnapshot exact shape, enums, active declaration, and closed nested properties', () => {
  const e = fixture('state_snapshot');
  e.payload.declaredSession = { task: 'coding', plannedMin: 30, strict: true, startedTs: 1730000000000 };
  assert.equal(validateEvent(e).valid, true);
  for (const hour of ['night', 'morning', 'afternoon', 'evening', 'late']) { e.payload.hourBand = hour; assert.equal(validateEvent(e).valid, true); }
  e.payload.hourBand = 'noon'; assert.equal(validateEvent(e).valid, false); e.payload.hourBand = 'morning';
  e.payload.declaredSession.extra = true; assert.equal(validateEvent(e).valid, false);
  delete e.payload.declaredSession.extra; delete e.payload.declaredSession.startedTs; assert.equal(validateEvent(e).valid, false);
});
test('exact taxonomy and starter maps', () => {
  const c = read('categories.json');
  assert.deepEqual(c.categories, 'coding docs communication social video short_video games shopping news music learning utility system unknown'.split(' '));
  assert.deepEqual(c.surfaces, 'feed shorts watch chat editor other'.split(' '));
  for (const map of ['androidPackages','windowsProcesses','domains']) { assert.ok(Object.keys(c[map]).length); for (const value of Object.values(c[map])) assert.ok(c.categories.includes(value)); }
  assert.ok(c.androidPackageNotes.every(note => note.verify === true));
});
test('all 20 Appendix A rows, symbols, units, ranges, and priors preserved', () => {
  const p = read('default-params.json');
  assert.deepEqual(p.appendixA.map(r => [r.symbol,r.default]), [
    ['N','500'],['W','30 min'],['κ','5'],['γ','0.995 / episode'],['n0','3'],['σ_min','0.25'],['κ_d','3.0 min/hr'],['λ_miss','5.0 min'],['c_digest','0.8 min'],['c_rel','0.25'],['c_glance','0.4 min'],['I_max','0.85'],['I_silent','0.16'],['ε','0.05 (when I < 0.4)'],['conf_min','0.40'],['none','0.35 / 0.65'],['none','5 s'],['none','10–30 s'],['τ_digest_max','120 min'],['none','1 s → 30 s']
  ]);
  assert.deepEqual(p.priors, {pickup:{social_notif_working:{alpha:3,beta:4},known_contact_message:{alpha:4,beta:3},promo:{alpha:1,beta:6}},dwell:{social:{m:'ln 9',s:0.8},short_video:{m:'ln 14',s:0.9},messaging:{m:'ln 3',s:0.6}},resumeDelay:{coding:{m:'ln 6',s:0.6},studying:{m:'ln 4',s:0.6}},fatiguePickupOddsMultiplier:{low:1,med:1.2,high:1.5}});
});
test('relay strict frame variants and command-specific allowlists', () => {
  const ajv = new Ajv2020({ strict: true }); addFormats(ajv);
  ajv.addSchema(read('context-snapshot.schema.json')); ajv.addSchema(read('event.schema.json'));
  const validate = ajv.compile(read('relay-frame.schema.json'));
  const session = { task:'coding', plannedMin:30, strict:true };
  const frames = [{type:'hello',device:'phone',version:'1.1.0',lastSeqAcked:0}, ...['ping','pong'].map(type => ({type,t0:0,t1:1,t2:2,t3:3})), {type:'events',fromSeq:1,events:[fixture('screen_on')]},{type:'ack',upToSeq:1},{type:'state',focusState:'working',phoneState:'screen_off',confidence:0.5,declared:null,heldCount:0},{type:'pet_event',kind:'held',count:1},{type:'encrypted'}, ...Object.entries({declare_session:session,end_session:{},show_now:{heldId:'fixture-id'},kill_switch:{on:true}}).map(([cmd,args]) => ({type:'command',commandId:fixture('screen_on').id,issuedAt:1,expiresAt:2,cmd,args}))];
  for (const frame of frames) {
    assert.equal(validate(frame),true,JSON.stringify(validate.errors));
    assert.equal(validate({...frame,extra:true}),false);
  }
  assert.equal(validate({type:'encrypted',ciphertext:'undefined-contract'}),false);
  assert.equal(validate({type:'events',fromSeq:0,events:Array(51).fill(fixture('screen_on'))}),false);
  const command = frames.find(f => f.cmd === 'show_now'); assert.equal(validate({...command,args:{text:'forbidden'}}),false);
  assert.equal(validate({type:'events',fromSeq:1,events:[{...fixture('screen_on'),payload:{text:'forbidden'}}]}),false);
});
test('all three checks pass on repository and reject violations in isolated scratch trees', t => {
  assert.match(checkSchemaSync(root), /PASS/); assert.match(checkTwinPurity(root), /PASS/); assert.match(checkNoPersonaImports(root), /PASS/);
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(),'dhyan-1a-'));
  t.after(() => fs.rmSync(scratch,{recursive:true,force:true}));
  const put = (p,text) => {const file=path.join(scratch,p);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,text);};
  put('core-spec/SCHEMA_VERSION','1.1.0');put('core-spec/event.schema.json',JSON.stringify(read('event.schema.json')));
  put('phone/app/SchemaVersion.kt','const val SCHEMA_VERSION = "1.1.0"');assert.match(checkSchemaSync(scratch),/PASS/);
  put('SchemaVersion.kt','const val SCHEMA_VERSION = "2.0.0"');assert.throws(() => checkSchemaSync(scratch));
  put('SchemaVersion.kt','const val SCHEMA_VERSION = "1.1.0"');
  put('phone/app/SchemaVersion.kt','const val SCHEMA_VERSION = "2.0.0"');assert.throws(() => checkSchemaSync(scratch));
  put('phone/app/SchemaVersion.kt','const val SCHEMA_VERSION = "1.1.0"');put('core-spec/SCHEMA_VERSION','2.0.0');assert.throws(() => checkSchemaSync(scratch));
  for (const prefix of ['android','androidx']) {put('phone/app/twin/params/Test.kt',`import ${prefix}.example.Test`);assert.throws(() => checkTwinPurity(scratch));}
  put('phone/app/twin/params/Test.kt','import kotlin.math.exp');assert.match(checkTwinPurity(scratch),/PASS/);
  for (const module of ['phone','laptop']) for (const ref of ['../../tools/personas/test','..\\tools\\personas\\test']) {
    put(`${module}/Test.txt`,ref);assert.throws(() => checkNoPersonaImports(scratch));put(`${module}/Test.txt`,'safe');
  }
  for (const script of ['check-schema-sync','check-twin-purity','check-no-persona-imports']) {
    const result = spawnSync(process.execPath,[path.join(root,`tools/${script}.mjs`)],{encoding:'utf8'});assert.equal(result.status,0,result.stderr);assert.match(result.stdout,/PASS/);
    put('phone/app/twin/params/Test.kt','import androidx.example.Test');
    put('laptop/Test.txt','../../tools/personas/test');
    const bad = spawnSync(process.execPath,[path.join(root,`tools/${script}.mjs`),scratch],{encoding:'utf8'});
    assert.equal(bad.status,1,script);
  }
});
test('root hygiene and governance preserve stage boundaries and complete spec inventory', () => {
  const text = p => fs.readFileSync(path.join(root,p),'utf8');
  const ignore = text('.gitignore').split(/\r?\n/);
  for (const pattern of ['node_modules/','dist/','out/','build/','.gradle/','.kotlin/','.idea/','local.properties','*.apk','.env','.env.*','!.env.example','*.log','tools/dataset/out/*.raw']) assert.ok(ignore.includes(pattern));
  assert.equal(text('.env.example').replace(/\r\n/g,'\n'),'OPENAI_API_KEY=\nOPENAI_MODEL=\nRELAY_URL=\n');
  assert.equal(text('core-spec/SCHEMA_VERSION'),'1.1.0');
  const pkg=JSON.parse(text('package.json'));assert.equal(pkg.type,'module');
  assert.equal(pkg.scripts.test,'node --test tools/test/*.test.mjs');
  assert.deepEqual(Object.keys(pkg.devDependencies).sort(),['ajv','ajv-formats']);
  for (const name of ['vscode-ext/.gitkeep','browser-ext/.gitkeep']) assert.equal(text(name),'');
  assert.match(text('README.md'),/ActivityWatch/);
  for (let section=1;section<=25;section++) assert.match(text('TRACEABILITY.md'),new RegExp(`S${section}(?:-|\\.)`));
  assert.match(text('STAGE_GATES.md'),/Stage 1 is pending/);
});
test('validator CLI returns failure without leaking input values', () => {
  const cli=path.join(root,'tools/validate-events.mjs');
  const good=spawnSync(process.execPath,[cli,path.join(root,'core-spec/fixtures/valid/notif_posted.json')],{encoding:'utf8'});assert.equal(good.status,0,good.stderr);
  const bad=spawnSync(process.execPath,[cli,path.join(root,'core-spec/fixtures/invalid/nested-forbidden-key.json')],{encoding:'utf8'});assert.equal(bad.status,1);assert.ok(!bad.stderr.includes('SYNTHETIC_FORBIDDEN_SENTINEL'));
  assert.equal(spawnSync(process.execPath,[cli],{encoding:'utf8'}).status,2);
});
