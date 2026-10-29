import {createReadStream} from 'node:fs';
import {readdir} from 'node:fs/promises';
import {createInterface} from 'node:readline';
import path from 'node:path';
import type {CategorizedEvent} from './jsonlStore';
import {isPrivateEvent} from './privacyGuard';
import {observedFocusToday} from '../panel/observedFocus';
export async function readObservedToday(directory:string,nowMs:number){
 const start=new Date(nowMs);start.setHours(0,0,0,0);const events:CategorizedEvent[]=[];
 for(const name of await readdir(directory)){if(name!=='events.jsonl'&&!/^events-\d+-[0-9a-f-]+\.jsonl$/.test(name))continue;
  const reader=createInterface({input:createReadStream(path.join(directory,name)),crlfDelay:Infinity});
  try{for await(const line of reader){if(Buffer.byteLength(line)>8192)continue;let event;try{event=JSON.parse(line);}catch{continue;}if(!isPrivateEvent(event)||event.ts<start.getTime()-5000||event.ts>nowMs)continue;events.push(event);if(events.length>50000)return {minutes:null,provenance:'observed' as const,label:'No data: history exceeds the bounded UI read limit'};}}finally{reader.close();}
 }
 return observedFocusToday(events,nowMs);
}
