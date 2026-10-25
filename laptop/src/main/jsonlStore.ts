import * as fs from 'node:fs/promises';
import path from 'node:path';
export interface CategorizedEvent { schemaVersion:string; id:string; deviceId:string; device:'laptop'|'phone'; seq:number; source:string; type:string; ts:number; ingestedTs?:number; synthetic:boolean; payload:Readonly<Record<string,unknown>> }
export interface StoreHealth { paused:boolean; eventsWritten:number; dropped:number; invalid:number; writeFailures:number; recoveredLines:number }
export interface StoreOptions {
 directory:string; maxBytes?:number; retentionDays?:number; now?:()=>number;
 validate:(event:unknown)=>boolean; onWriteFailure?:()=>void;
 appendFile?:typeof fs.appendFile;
}
/** One owner per directory. Collectors never see filesystem exceptions. */
export class JsonlStore {
 readonly health:StoreHealth={paused:false,eventsWritten:0,dropped:0,invalid:0,writeFailures:0,recoveredLines:0};
 private ring:CategorizedEvent[]=[]; private pending:CategorizedEvent[]=[];
 private queue:Promise<void>=Promise.resolve(); private bytes=0; private nextSeq=1; private reserved=0;
 private repairTo:number|null=null; private nextRetry=0; private failures=0; private failureReported=false;
 private constructor(private options:StoreOptions){}
 static async open(options:StoreOptions):Promise<JsonlStore>{const store=new JsonlStore(options);await fs.mkdir(options.directory,{recursive:true});await store.recoverPartialLine();await store.retain();return store;}
 private file(name='events.jsonl'){return path.join(this.options.directory,name);}
 private now(){return (this.options.now??Date.now)();}
 private serial<T>(action:()=>Promise<T>):Promise<T>{const result=this.queue.then(action);this.queue=result.then(()=>{},()=>{});return result;}
 async allocateSeq():Promise<number>{return this.serial(async()=>{if(this.nextSeq>this.reserved){const high=this.nextSeq+99;await fs.writeFile(this.file('seq.json.tmp'),JSON.stringify({highWater:high}));await fs.rename(this.file('seq.json.tmp'),this.file('seq.json'));this.reserved=high;}return this.nextSeq++;});}
 async appendEvent(event:CategorizedEvent):Promise<void>{return this.serial(async()=>{
  if(!this.options.validate(event)){this.health.invalid++;return;}
  // Snapshot the event: caller mutations cannot cross the validated boundary.
  const copy=JSON.parse(JSON.stringify(event)) as CategorizedEvent;
  this.ring.push(copy);if(this.ring.length>2000)this.ring.shift();
  if(this.pending.length===2000){this.pending.shift();this.health.dropped++;}
  this.pending.push(copy);await this.flushInternal();
 });}
 readRecentEvents(limit=2000):readonly CategorizedEvent[]{const count=Math.max(0,Math.min(2000,Math.floor(limit)));return count?structuredClone(this.ring.slice(-count)):[];}
 async retryWrites():Promise<void>{return this.serial(()=>this.flushInternal());}
 private async flushInternal(){
  if(this.health.paused&&this.now()<this.nextRetry)return;
  try{
   if(this.repairTo!==null){await fs.truncate(this.file(),this.repairTo);this.bytes=this.repairTo;this.repairTo=null;}
   while(this.pending.length){const line=JSON.stringify(this.pending[0])+'\n';const size=Buffer.byteLength(line);
    await this.rotateInternal(size);this.repairTo=this.bytes;
    await (this.options.appendFile??fs.appendFile)(this.file(),line,'utf8');
    this.bytes+=size;this.repairTo=null;this.pending.shift();this.health.eventsWritten++;
   }
   this.health.paused=false;this.failures=0;this.failureReported=false;
  }catch{
   this.health.paused=true;this.health.writeFailures++;this.nextRetry=this.now()+Math.min(30000,1000*2**Math.min(this.failures++,5));
   if(!this.failureReported){this.failureReported=true;try{this.options.onWriteFailure?.();}catch{/* diagnostics must not break collectors */}}
  }
 }
 async rotateIfNeeded():Promise<void>{return this.serial(()=>this.rotateInternal(0));}
 private async rotateInternal(incoming:number){
  if(this.bytes>0&&this.bytes+incoming>=(this.options.maxBytes??10*1024*1024)){
   // UUID suffix avoids overwriting a rotation when an injected clock is unchanged.
   const {randomUUID}=await import('node:crypto');await fs.rename(this.file(),this.file(`events-${this.now()}-${randomUUID()}.jsonl`));
   this.bytes=0;await fs.writeFile(this.file(),'');await this.retain();
  }
 }
 private async retain(){const cutoff=this.now()-(this.options.retentionDays??14)*86400000;for(const name of await fs.readdir(this.options.directory)){const match=/^events-(\d+)-[0-9a-f-]+\.jsonl$/.exec(name);if(match&&Number(match[1])<cutoff)await fs.unlink(this.file(name));}}
 async recoverPartialLine():Promise<void>{
  let maxSeq=0;
  const names=(await fs.readdir(this.options.directory)).filter(n=>n==='events.jsonl'||/^events-\d+-[0-9a-f-]+\.jsonl$/.test(n));
  for(const name of names){const data=await fs.readFile(this.file(name));const end=data.lastIndexOf(10)+1;
   if(end!==data.length){await fs.truncate(this.file(name),end);this.health.recoveredLines++;}
   for(const line of data.subarray(0,end).toString('utf8').split('\n')){if(!line)continue;try{const event:unknown=JSON.parse(line);if(!this.options.validate(event)){this.health.invalid++;continue;}const e=event as CategorizedEvent;maxSeq=Math.max(maxSeq,e.seq);if(name==='events.jsonl'){this.ring.push(e);if(this.ring.length>2000)this.ring.shift();}}catch{this.health.invalid++;}}
   if(name==='events.jsonl')this.bytes=end;
  }
  let highWater=0;try{const state=JSON.parse(await fs.readFile(this.file('seq.json'),'utf8'));if(!Number.isSafeInteger(state.highWater)||state.highWater<0)throw Error('invalid_sequence_state');highWater=state.highWater;}catch(error){if((error as NodeJS.ErrnoException).code!=='ENOENT')throw Error('sequence_recovery_failed');}
  this.reserved=Math.max(highWater,maxSeq);this.nextSeq=this.reserved+1;
  await fs.appendFile(this.file(),'');
 }
}

