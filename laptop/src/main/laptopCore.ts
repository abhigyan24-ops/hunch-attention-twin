import {AwAdapter} from '../adapters/AwAdapter';
import {ActiveWinFallback} from '../adapters/ActiveWinFallback';
import type {RawLaptopEvent} from '../adapters/LaptopAdapter';
import {JsonlStore,type CategorizedEvent} from './jsonlStore';
import {isPrivateEvent} from './privacyGuard';
import {loadSettings} from './settings';
import {ProcessLabels,WindowNormalizer,type NormalizedChange} from '../normalizer/windowNormalizer';
import {FocusRunner} from '../state/focusState';
import {randomUUID} from 'node:crypto';
/** Local-only Stage 7A runtime. Relay/session control is deliberately separate. */
export async function startLaptopCore(directory:string,schemaVersion:string,idleSeconds:()=>number,now=Date.now){
 const settings=await loadSettings(directory),labels=new ProcessLabels(directory);await labels.load();
 let report:number|null=null,category='unknown',idleSec=0,browserFocused=false,awLast:number|null=null,lastIdle:number|null=null,stuckEvidence=false;
 const switches:number[]=[];let writes=Promise.resolve();let stopping=false;
 const diagnostics={writePipelineFailures:0};
 const emit=(change:NormalizedChange|{type:'gap';source:'system';ts:number;payload:Record<string,unknown>})=>{
 writes=writes.then(async()=>{const seq=await store.allocateSeq();const event:CategorizedEvent={schemaVersion,id:randomUUID(),deviceId:settings.deviceId,device:'laptop',seq,ts:change.ts,ingestedTs:now(),source:change.source,type:change.type,payload:change.payload,synthetic:false};await store.appendEvent(event);}).catch(()=>{diagnostics.writePipelineFailures++;});};
 const gap=(reason:string)=>emit({type:'gap',source:'system',ts:now(),payload:{deviceFrom:'laptop',deviceTo:'laptop',reason}});
 const store=await JsonlStore.open({directory,maxBytes:settings.jsonl.maxBytes,retentionDays:settings.jsonl.retentionDays,now,validate:isPrivateEvent,onWriteFailure:()=>gap('storage_unavailable')});
 const normalizer=new WindowNormalizer(process=>labels.category(process));
 const accept=(raw:RawLaptopEvent)=>{
 if(raw.kind==='process')report=raw.ts;
 if(raw.kind==='process'){const change=normalizer.process(raw.process,raw.ts);if(change){category=String(change.payload.appCat);browserFocused=/^(chrome|msedge|firefox|brave)(\.exe)?$/i.test(raw.process);switches.push(raw.ts);emit(change);}}
 if(raw.kind==='idle'){lastIdle=raw.ts;idleSec=raw.idleSec;const change=normalizer.idleChange(idleSec,raw.ts);if(change)emit(change);}
 if(raw.kind==='domain'&&browserFocused)category=raw.domainCategory;
 };
 const fallback=new ActiveWinFallback(idleSeconds,undefined,now);fallback.onRaw(accept);
 const started=now();const aw=new AwAdapter(undefined,undefined,now);aw.onRaw(raw=>{if(raw.kind==='process'){awLast=now();fallback.stop();}accept(raw);});aw.start();
 const focus=new FocusRunner(()=>{while(switches.length&&switches[0]<now()-60000)switches.shift();return {lastReportMs:lastIdle===null||now()-lastIdle>=15000?null:report,category,idleSec,switchesPerMin:switches.length,continuous:idleSec<60,stuckEvidence,browserFocused};},now,()=>gap('adapter_offline'));focus.start();
 const timer=setInterval(()=>{if(!stopping&&now()-(awLast??started)>15000)fallback.start();void store.retryWrites();},5000);
 return {settings,store,focus,labels,diagnostics,setStuckEvidence:(value:boolean)=>{stuckEvidence=value===true;},stop:async()=>{stopping=true;clearInterval(timer);aw.stop();fallback.stop();focus.stop();await writes;await store.retryWrites();}};
}


