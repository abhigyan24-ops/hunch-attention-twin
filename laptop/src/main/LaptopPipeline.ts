import {randomUUID} from 'node:crypto';
import type {LaptopAdapter,RawLaptopEvent} from '../adapters/LaptopAdapter';
import {JsonlStore,type CategorizedEvent} from './jsonlStore';
import {isPrivateEvent} from './privacyGuard';
import {WindowNormalizer,type ProcessLabels} from '../normalizer/windowNormalizer';
import {FocusRunner,type FocusSnapshot,type FocusInputs} from '../state/focusState';
import categories from '../../../core-spec/categories.json';
export interface PipelineChange {type:string;source:string;ts:number;payload:Record<string,unknown>}
export interface PipelineOptions {
 primary:LaptopAdapter;fallback:LaptopAdapter;store:JsonlStore;deviceId:string;schemaVersion:string;
 labels:Pick<ProcessLabels,'category'>;now?:()=>number;queueLimit?:number;automaticTicks?:boolean;
 normalize?:WindowNormalizer;
}
/** Category-only feed boundary. 7C must authenticate and translate wire messages first. */
export type FeedEvent=RawLaptopEvent;
export class LaptopPipeline {
 private readonly now:()=>number;private readonly normalizer:WindowNormalizer;
 private counters={adapterErrors:0,stageErrors:0,subscriberErrors:0,queueDropped:0,guardRejected:0,sequenceFailures:0};
 private queue:PipelineChange[]=[];private draining:Promise<void>|null=null;private maintenance:Promise<void>|null=null;
 private events=new Set<(event:CategorizedEvent)=>void>();private states=new Set<(state:FocusSnapshot)=>void>();
 private running=false;private accepting=false;private started=0;private primaryReport:number|null=null;
 private lastProcess:number|null=null;private lastIdle:number|null=null;private idleSec=0;private category='unknown';private browserFocused=false;
 private switches:number[]=[];private hasProcess=false;private fallbackActive=false;private offlineReported=false;private storageReported=false;
 private feedProvider:(()=>Pick<FocusInputs,'vscode'|'browser'>)|null=null;
 setFeedProvider(provider:()=>Pick<FocusInputs,'vscode'|'browser'>){this.feedProvider=provider;}
 private stuckEvidence=false;private stuckAt:number|null=null;private timer:ReturnType<typeof setInterval>|undefined;
 readonly focus:FocusRunner;
 constructor(private readonly options:PipelineOptions){
  this.now=options.now??Date.now;this.normalizer=options.normalize??new WindowNormalizer(p=>options.labels.category(p));
  if(!Number.isInteger(options.queueLimit??2000)||(options.queueLimit??2000)<1)throw Error('invalid_queue_limit');
  this.focus=new FocusRunner(()=>this.inputs(),this.now);
  this.focus.onState(state=>this.publish(this.states,state));
 }
 get diagnostics(){const health=this.options.store.health;return {...health,eventsWritten:health.eventsWritten,rejected:health.invalid+this.counters.guardRejected,dropped:health.dropped+this.counters.queueDropped,truncatedLinesRecovered:health.recoveredLines,...this.counters,queueDepth:this.queue.length,fallbackActive:this.fallbackActive};}
 onEvent(callback:(event:CategorizedEvent)=>void):()=>void {this.events.add(callback);return ()=>{this.events.delete(callback);};}
 onState(callback:(state:FocusSnapshot)=>void):()=>void {this.states.add(callback);return ()=>{this.states.delete(callback);};}
 private publish<T>(subscribers:Set<(value:T)=>void>,value:T){for(const callback of subscribers){try{callback(structuredClone(value));}catch{this.counters.subscriberErrors++;}}}
 adapterError(){this.counters.adapterErrors++;}
 start(){if(this.running)return;this.running=true;this.accepting=true;this.started=this.now();this.primaryReport=null;this.lastProcess=null;this.lastIdle=null;this.offlineReported=false;this.fallbackActive=false;this.switches=[];
  try{this.options.primary.onRaw(raw=>this.accept(raw,'primary'));}catch{this.adapterError();}
  try{this.options.fallback.onRaw(raw=>this.accept(raw,'fallback'));}catch{this.adapterError();}
  try{this.options.primary.start();}catch{this.adapterError();}
  this.tick();if(this.options.automaticTicks!==false)this.timer=setInterval(()=>this.tick(),5000);
 }
 private inputs(){const now=this.now();this.switches=this.switches.filter(ts=>ts>now-60000&&ts<=now);
  const report=this.lastProcess===null||this.lastIdle===null?null:Math.min(this.lastProcess,this.lastIdle);
  let feeds:Pick<FocusInputs,'vscode'|'browser'>={};try{feeds=this.feedProvider?.()??{};}catch{this.counters.stageErrors++;}
  return {...feeds,lastReportMs:report,category:this.category,idleSec:this.idleSec,switchesPerMin:this.switches.length,continuous:this.idleSec<60,stuckEvidence:this.feedProvider?Boolean(feeds.vscode?.windowFocused&&feeds.vscode.state==='stuck'):this.stuckEvidence&&this.stuckAt!==null&&now-this.stuckAt<15000,browserFocused:this.browserFocused};
 }
 tick():FocusSnapshot|undefined {if(!this.running)return;
  const now=this.now();
  if(now-(this.primaryReport??this.started)>=15000&&!this.fallbackActive){this.fallbackActive=true;try{this.options.fallback.start();}catch{this.adapterError();}}
  const inputs=this.inputs(),silent=inputs.lastReportMs===null||now-inputs.lastReportMs>=15000;
  if(silent&&now-this.started>=15000){if(!this.offlineReported){this.offlineReported=true;this.gap('adapter_offline');}}else if(!silent)this.offlineReported=false;
  if(!this.maintenance){const wasPaused=this.options.store.health.paused;this.maintenance=this.options.store.retryWrites().then(()=>{if(wasPaused&&!this.options.store.health.paused)this.storageReported=false;}).catch(()=>{this.counters.stageErrors++;}).finally(()=>{this.maintenance=null;});}
  try{return this.focus.tick();}catch{this.counters.stageErrors++;return undefined;}
 }
 setStuckEvidence(value:boolean){this.stuckEvidence=value===true;this.stuckAt=this.now();}
 injectFeedEvent(event:FeedEvent):boolean {if(!this.accepting)return false;return this.accept(event,'feed');}
 private safeRaw(raw:RawLaptopEvent):boolean {
  if(!raw||typeof raw!=='object'||!Number.isSafeInteger(raw.ts)||raw.ts<0||raw.ts>this.now()+300000)return false;
  const keys:Record<string,string[]>={process:['kind','ts','process'],idle:['kind','ts','idleSec'],domain:['kind','ts','domainCategory'],edit:['kind','ts','editsPerMin','saves','errors','warnings'],resume:['kind','ts','delayMs'],tab:['kind','ts','domainCat','surface','videoState','scrollCount','tabActive']};
  const allowed=keys[raw.kind];if(!allowed||Object.keys(raw).some(key=>!allowed.includes(key)))return false;
  if(raw.kind==='resume')return Number.isSafeInteger(raw.delayMs)&&raw.delayMs>=0&&raw.delayMs<=3600000;
  if(raw.kind==='tab')return categories.categories.includes(raw.domainCat)&&categories.surfaces.includes(raw.surface)&&typeof raw.tabActive==='boolean'&&(raw.videoState===undefined||['playing','paused','none'].includes(raw.videoState))&&(raw.scrollCount===undefined||Number.isSafeInteger(raw.scrollCount)&&raw.scrollCount>=0);
  if(raw.kind==='process')return typeof raw.process==='string'&&/^[\w .-]{1,100}$/.test(raw.process);
  if(raw.kind==='domain')return categories.categories.includes(raw.domainCategory);
  if(raw.kind==='idle')return Number.isFinite(raw.idleSec)&&raw.idleSec>=0;
  return ['editsPerMin','saves','errors','warnings'].every(key=>Number.isSafeInteger((raw as unknown as Record<string,number>)[key])&&(raw as unknown as Record<string,number>)[key]>=0)&&raw.editsPerMin<=2000;
 }
 private accept(raw:RawLaptopEvent,origin:'primary'|'fallback'|'feed'):boolean {
  if(!this.accepting||origin==='fallback'&&!this.fallbackActive)return false;
  if(!this.safeRaw(raw)){this.counters.guardRejected++;return false;}
  if(origin==='primary'&&raw.kind==='process'){
   this.primaryReport=raw.ts;if(this.fallbackActive){this.fallbackActive=false;try{this.options.fallback.stop();}catch{this.adapterError();}}
  }
  try{
   if(raw.kind==='process'){
    this.lastProcess=raw.ts;const change=this.normalizer.process(raw.process,raw.ts);
    if(change){if(this.hasProcess)this.switches.push(raw.ts);this.hasProcess=true;this.category=String(change.payload.appCat);this.browserFocused=/^(chrome|msedge|firefox|brave)(\.exe)?$/i.test(raw.process);this.enqueue(change);}
   }else if(raw.kind==='idle'){this.lastIdle=raw.ts;this.idleSec=raw.idleSec;const change=this.normalizer.idleChange(raw.idleSec,raw.ts);if(change)this.enqueue(change);
   }else if(raw.kind==='domain'&&this.browserFocused){this.category=raw.domainCategory;
   }else if(raw.kind==='resume'){this.enqueue({type:'resume_first_edit',source:'vscode',ts:raw.ts,payload:{delayMs:raw.delayMs}});
   }else if(raw.kind==='tab'){this.enqueue({type:'tab_category',source:'browser',ts:raw.ts,payload:{domainCat:raw.domainCat,surface:raw.surface,...(raw.videoState!==undefined?{videoState:raw.videoState}:{}),...(raw.scrollCount!==undefined?{scrollCount:raw.scrollCount}:{})}});
   }else if(raw.kind==='edit'){this.enqueue({type:'edit_burst',source:'vscode',ts:raw.ts,payload:{editsPerMin:raw.editsPerMin,saves:raw.saves,errors:raw.errors,warnings:raw.warnings}});}
   return true;
  }catch{this.counters.stageErrors++;return false;}
 }
 private enqueue(change:PipelineChange){if(this.queue.length>=(this.options.queueLimit??2000)){this.queue.shift();this.counters.queueDropped++;}this.queue.push(structuredClone(change));this.scheduleDrain();}
 private scheduleDrain(){if(this.draining)return;this.draining=Promise.resolve().then(()=>this.drain()).finally(()=>{this.draining=null;if(this.queue.length)this.scheduleDrain();});}
 private async drain(){while(this.queue.length){const change=this.queue.shift()!;let seq:number;
  try{seq=await this.options.store.allocateSeq();}catch{this.counters.sequenceFailures++;this.counters.queueDropped++;this.storageFailed();continue;}
  const event:CategorizedEvent={schemaVersion:this.options.schemaVersion,id:randomUUID(),deviceId:this.options.deviceId,device:'laptop',seq,ts:change.ts,ingestedTs:this.now(),source:change.source,type:change.type,payload:change.payload,synthetic:false};
  if(!isPrivateEvent(event)){this.counters.guardRejected++;continue;}
  try{await this.options.store.appendEvent(event);this.publish(this.events,event);}catch{this.counters.stageErrors++;this.storageFailed();}
 }}
 storageFailed(){if(!this.accepting||this.storageReported)return;this.storageReported=true;this.gap('storage_unavailable');}
 private gap(reason:'storage_unavailable'|'adapter_offline'){this.enqueue({type:'gap',source:'system',ts:this.now(),payload:{deviceFrom:'laptop',deviceTo:'laptop',reason}});}
 async flush(force=false){while(this.draining||this.queue.length){if(!this.draining)this.scheduleDrain();await this.draining;}await this.options.store.retryWrites(force);}
 async stop(){if(!this.running)return;this.accepting=false;this.running=false;if(this.timer)clearInterval(this.timer);this.timer=undefined;
  for(const adapter of [this.options.primary,this.options.fallback])try{adapter.stop();}catch{this.adapterError();}
  this.focus.stop();await this.maintenance;await this.flush(true);
 }
}




