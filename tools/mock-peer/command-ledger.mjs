import * as fs from 'node:fs/promises';
import path from 'node:path';
/** Test receiver reference: persist command identity BEFORE any injected side effect. No args on disk. */
export class DurableCommandLedger {
 constructor(options,state=[]){this.options=options;this.state=state;this.queue=Promise.resolve();}
 static async open(options){let state=[];try{state=JSON.parse(await fs.readFile(options.file,'utf8'));if(!Array.isArray(state)||state.length>(options.maxRecords??4096)||new Set(state.map(r=>r?.cmdId)).size!==state.length||state.some(r=>!r||Object.keys(r).some(k=>!['cmdId','seenAt','status'].includes(k))||typeof r.cmdId!=='string'||!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(r.cmdId)||!Number.isSafeInteger(r.seenAt)||r.seenAt<0||!['reserved','applied','rejected'].includes(r.status)))throw Error();}catch(error){if(error.code!=='ENOENT')throw Error('command_recovery_failed');}return new DurableCommandLedger(options,state);}
 execute(command,nowMs,handler){const operation=this.queue.then(async()=>{const retained=this.state.filter(r=>nowMs-r.seenAt<86400000);if(retained.some(r=>r.cmdId===command.cmdId))return {status:'duplicate',reason:'outcome_unknown'};
 if(retained.length>=(this.options.maxRecords??4096))return {status:'rejected',reason:'idempotency_full'};
 const reservation={cmdId:command.cmdId,seenAt:nowMs,status:'reserved'};await this.persist([...retained,reservation]);let status='rejected';try{status=await handler(command)===true?'applied':'rejected';}catch{/* never expose handler exception/content */}
 return {status};});this.queue=operation.catch(()=>{});return operation;}
 async persist(state){try{if(this.options.commit)await this.options.commit(state);else{await fs.mkdir(path.dirname(this.options.file),{recursive:true});const temp=this.options.file+'.tmp',handle=await fs.open(temp,'w');try{await handle.writeFile(JSON.stringify(state));await handle.sync();}finally{await handle.close();}await fs.rename(temp,this.options.file);}this.state=state;}catch{throw Error('command_persistence_failure');}}
}
