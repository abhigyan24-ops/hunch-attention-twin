import * as fs from 'node:fs/promises';
import path from 'node:path';
import {validateEvent} from '../validate-events.mjs';
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
/** Mock reference only: atomic settlement ledger. Never persist rejected payloads. */
export class DurableEventLedger {
 constructor(options,state={records:[],upToSeq:0}){this.options=options;this.state=state;this.queue=Promise.resolve();}
 static async open(options){let state;try{state=JSON.parse(await fs.readFile(options.file,'utf8'));if(!state||Object.keys(state).some(k=>!['records','upToSeq'].includes(k))||!Array.isArray(state.records)||state.records.length>(options.maxRecords??50000)||!Number.isSafeInteger(state.upToSeq)||state.upToSeq<0)throw Error();
const seen=new Set();let highest=0;
for(const record of state.records){if(!record||!['event','rejected'].includes(record.kind)||record.device!==options.device||record.deviceId!==options.deviceId||!Number.isSafeInteger(record.seq)||record.seq<0||!uuid.test(record.id)||seen.has(record.seq))throw Error();seen.add(record.seq);highest=Math.max(highest,record.seq);
const keys=record.kind==='event'?['kind','deviceId','device','seq','id','event']:['kind','deviceId','device','seq','id','reason'];if(Object.keys(record).some(k=>!keys.includes(k)))throw Error();
if(record.kind==='event'){if(!validateEvent(record.event).valid||record.event.id!==record.id||record.event.seq!==record.seq||record.event.deviceId!==record.deviceId||record.event.device!==record.device)throw Error();}
else if(!['duplicate','schema_invalid','future_timestamp'].includes(record.reason))throw Error();}
if(state.upToSeq!==highest)throw Error();}catch(error){if(error.code!=='ENOENT')throw Error('ledger_recovery_failed');}return new DurableEventLedger(options,state);}
 get upToSeq(){return this.state.upToSeq;}
 get records(){return structuredClone(this.state.records);}
 settle(body){const operation=this.queue.then(()=>this.apply(body));this.queue=operation.catch(()=>{});return operation;}
 async apply(body){
  if(!body||Object.keys(body).some(k=>!['fromSeq','events'].includes(k))||!Number.isSafeInteger(body.fromSeq)||body.fromSeq<0||!Array.isArray(body.events)||body.events.length>50)throw Error('schema_invalid');
  let prior=-1;for(const event of body.events){
   if(!event||!uuid.test(event.id)||!Number.isSafeInteger(event.seq)||event.seq<0||event.seq<=prior||event.device!==this.options.device||event.deviceId!==this.options.deviceId)throw Error('invalid_identity');
   if(event.schemaVersion!=='1.1.0')throw Error('schema_mismatch');prior=event.seq;
  }
  if(body.events.length&&body.fromSeq!==body.events[0].seq)throw Error('invalid_sequence');
  const records=structuredClone(this.state.records),bySeq=new Map(records.map(r=>[r.seq,r])),ids=new Set(records.map(r=>r.id));let cursor=this.state.upToSeq;const applied=[];let rejected=0,duplicates=0;
  for(const event of body.events){
   const existing=bySeq.get(event.seq);if(existing){if(existing.id!==event.id)throw Error('identity_conflict');duplicates++;continue;}
   if(event.seq<cursor)throw Error('late_unsettled_sequence');
   let reason=null;if(ids.has(event.id)){reason='duplicate';duplicates++;}
   else if(!validateEvent(event).valid){reason='schema_invalid';rejected++;}
   else if(event.ts+(this.options.offsetToReceiver??0)>(this.options.nowFn??Date.now)()+300000){reason='future_timestamp';rejected++;}
   const record=reason?{kind:'rejected',deviceId:event.deviceId,device:event.device,seq:event.seq,id:event.id,reason}:{kind:'event',deviceId:event.deviceId,device:event.device,seq:event.seq,id:event.id,event:structuredClone(event)};
   records.push(record);bySeq.set(event.seq,record);ids.add(event.id);cursor=Math.max(cursor,event.seq);if(!reason)applied.push(structuredClone(event));
  }
  if(records.length>(this.options.maxRecords??50000))throw Error('ledger_full');
  const next={records,upToSeq:cursor};
  try{if(this.options.commit)await this.options.commit(next);else{await fs.mkdir(path.dirname(this.options.file),{recursive:true});const temp=this.options.file+'.tmp',handle=await fs.open(temp,'w');try{await handle.writeFile(JSON.stringify(next));await handle.sync();}finally{await handle.close();}await fs.rename(temp,this.options.file);}}catch{throw Error('persistence_failure');}
  this.state=next;return {upToSeq:cursor,applied,rejected,duplicates};
 }
}
