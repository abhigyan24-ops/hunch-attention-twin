import {AwAdapter} from '../adapters/AwAdapter';
import {ActiveWinFallback} from '../adapters/ActiveWinFallback';
import {JsonlStore} from './jsonlStore';
import {isPrivateEvent} from './privacyGuard';
import {loadSettings} from './settings';
import {ProcessLabels} from '../normalizer/windowNormalizer';
import {LaptopPipeline} from './LaptopPipeline';
export async function startLaptopCore(directory:string,schemaVersion:string,idleSeconds:()=>number,now=Date.now){
 const settings=await loadSettings(directory),labels=new ProcessLabels(directory);await labels.load();
 let pipeline:LaptopPipeline|undefined;
 const store=await JsonlStore.open({directory,maxBytes:settings.jsonl.maxBytes,retentionDays:settings.jsonl.retentionDays,now,validate:isPrivateEvent,onWriteFailure:()=>pipeline?.storageFailed()});
 const aw=new AwAdapter(undefined,undefined,now,()=>pipeline?.adapterError());
 const fallback=new ActiveWinFallback(idleSeconds,undefined,now,()=>pipeline?.adapterError());
 pipeline=new LaptopPipeline({primary:aw,fallback,store,labels,deviceId:settings.deviceId,schemaVersion,now});pipeline.start();
 return {settings,store,labels,pipeline,focus:pipeline.focus,get diagnostics(){return pipeline!.diagnostics;},setStuckEvidence:(value:boolean)=>pipeline!.setStuckEvidence(value),stop:()=>pipeline!.stop()};
}
