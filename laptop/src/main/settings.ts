import {randomUUID} from 'node:crypto';
import * as fs from 'node:fs/promises';
import path from 'node:path';
export interface Settings { ingest:{port:number;allowedExtensionIds:string[]};jsonl:{maxBytes:number;retentionDays:number};deviceId:string }
export async function loadSettings(directory:string):Promise<Settings>{
 await fs.mkdir(directory,{recursive:true});const file=path.join(directory,'settings.json');
 try{const s=JSON.parse(await fs.readFile(file,'utf8')) as Settings;
 if(!s||!Number.isInteger(s.ingest?.port)||s.ingest.port<1||s.ingest.port>65535||!Array.isArray(s.ingest.allowedExtensionIds)||!s.ingest.allowedExtensionIds.every(id=>/^[a-p]{32}$/.test(id))||!Number.isSafeInteger(s.jsonl?.maxBytes)||s.jsonl.maxBytes<8192||!Number.isInteger(s.jsonl.retentionDays)||s.jsonl.retentionDays<1||!/^[0-9a-f-]{36}$/.test(s.deviceId))throw Error('invalid_settings');return s;
 }catch(error){if((error as NodeJS.ErrnoException).code!=='ENOENT')throw Error('settings_recovery_required');}
 const settings:Settings={ingest:{port:47821,allowedExtensionIds:[]},jsonl:{maxBytes:10*1024*1024,retentionDays:14},deviceId:randomUUID()};
 await fs.writeFile(file,JSON.stringify(settings),{flag:'wx'});return settings;
}
