import * as fs from 'node:fs/promises';import path from 'node:path';import {randomBytes} from 'node:crypto';
export interface SecretStorage {isEncryptionAvailable():boolean;encryptString(value:string):Buffer;decryptString(value:Buffer):string;getSelectedStorageBackend?():string}
export async function loadIngestToken(directory:string,storage:SecretStorage):Promise<string>{
 if(!storage.isEncryptionAvailable()||(process.platform==='linux'&&storage.getSelectedStorageBackend?.()==='basic_text'))throw Error('secure_storage_unavailable');
 const file=path.join(directory,'ingest-token.bin');let bytes:Buffer;
 try{bytes=await fs.readFile(file);}catch(error){if((error as NodeJS.ErrnoException).code!=='ENOENT')throw Error('token_storage_failed');const token=randomBytes(32).toString('base64url');try{await fs.mkdir(directory,{recursive:true});await fs.writeFile(file,storage.encryptString(token),{flag:'wx'});return token;}catch{throw Error('token_storage_failed');}}
 try{const token=storage.decryptString(bytes);if(!/^[A-Za-z0-9_-]{43}$/.test(token))throw Error();return token;}catch{throw Error('token_recovery_failed');}
}
