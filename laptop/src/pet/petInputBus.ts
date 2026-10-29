import type {FocusSnapshot} from '../state/focusState';
export type CollectorStatus='healthy'|'silent'|'unknown';
export type FocusState='deep_focus'|'working'|'drifting'|'distracted'|'recovering'|'break';
export interface HeldItem {heldId:string;appCat:string;heldAt:number;expiresAt:number;status:'HELD'|'SNOOZED'|'SCHEDULED'|'RELEASED'|'EXPIRED'|'FAILED_OPEN'|'ACTUATION_UNKNOWN'}
export interface DeclaredSession {task:string;plannedMin:number;strict:boolean;startedTs:number;taskSource:'declared'}
export interface RemotePetInput {ts:number;connected:boolean;heldCount:number;heldItems:HeldItem[];focusState?:FocusState;fatigueBand?:'low'|'med'|'high';secondScreenOverlap?:boolean;petEvent?:{kind:'held'|'released'|'nudge'|'celebrate';ts:number};declaredSession?:DeclaredSession|null}
export interface PetInputs {local:FocusSnapshot|null;collector:CollectorStatus;remote:RemotePetInput|null;declaredSession:DeclaredSession|null;storagePaused:boolean}
export interface PairingUi {snapshot():{status:'not_paired'|'pending'|'paired';label:string}}
export const unpairedUi:PairingUi={snapshot:()=>({status:'not_paired',label:'Not paired'})};
/** Process-local presentation bus, never a command or inference engine. 8L validates wire inputs before publishing. */
export class PetInputBus {
 private inputs:PetInputs={local:null,collector:'unknown',remote:null,declaredSession:null,storagePaused:false};
 private listeners=new Set<(input:PetInputs)=>void>();
 snapshot():PetInputs{return structuredClone(this.inputs);}
 subscribe(listener:(input:PetInputs)=>void):()=>void{this.listeners.add(listener);return ()=>{this.listeners.delete(listener);};}
 private publish(){for(const listener of this.listeners)try{listener(this.snapshot());}catch{/* independent presentation subscribers */}}
 setLocal(local:FocusSnapshot,collector:CollectorStatus){this.inputs.local={...local};this.inputs.collector=collector;this.publish();}
 setStoragePaused(paused:boolean){this.inputs.storagePaused=paused;this.publish();}
 setDeclaredSession(session:DeclaredSession|null){this.inputs.declaredSession=session?{...session}:null;this.publish();}
 setRemote(remote:RemotePetInput|null){this.inputs.remote=remote?structuredClone(remote):null;this.publish();}
}
