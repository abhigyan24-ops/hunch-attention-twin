import {randomUUID} from 'node:crypto';
import {isPrivateEvent} from './privacyGuard';
import type {CategorizedEvent,JsonlStore} from './jsonlStore';
import type {DeclaredSession} from '../pet/petInputBus';
export interface Declaration {task:string;plannedMin:number;strict:boolean}
export function declaredSessionEvent(input:Declaration,context:{deviceId:string;schemaVersion:string;seq:number;nowMs:number}):CategorizedEvent|null {
 if(!input||typeof input.task!=='string'||typeof input.strict!=='boolean'||!Number.isInteger(input.plannedMin)||input.plannedMin<5||input.plannedMin>480)return null;
 const event:CategorizedEvent={id:randomUUID(),schemaVersion:context.schemaVersion,deviceId:context.deviceId,device:'laptop',seq:context.seq,ts:context.nowMs,ingestedTs:context.nowMs,source:'system',type:'declared_session',synthetic:false,payload:{task:input.task,plannedMin:input.plannedMin,strict:input.strict}};
 return isPrivateEvent(event)?event:null;
}
export async function declareLocally(input:Declaration,store:JsonlStore,context:{deviceId:string;schemaVersion:string;nowMs:number}):Promise<{session:DeclaredSession;persisted:boolean}|null>{
 const seq=await store.allocateSeq(),event=declaredSessionEvent(input,{...context,seq});if(!event)return null;
 await store.appendEvent(event);return {session:{...input,startedTs:context.nowMs,taskSource:'declared'},persisted:!store.health.paused};
}
