import * as fs from 'node:fs/promises';import path from 'node:path';import type {SecretStorage} from '../ingest/tokenStore';import {decodeBase64,hkdfSha256} from './crypto';
export async function atomicWrite(file:string,text:string|Buffer){await fs.mkdir(path.dirname(file),{recursive:true});const temp=file+'.tmp',h=await fs.open(temp,'w');try{await h.writeFile(text);await h.sync();}finally{await h.close();}await fs.rename(temp,file);}
export interface Credentials{PK:Buffer;sid:string}
export class CredentialStore{
 constructor(private directory:string,private storage:SecretStorage){}
 private file(){return path.join(this.directory,'pairing.bin');}
 private check(){if(!this.storage.isEncryptionAvailable()||process.platform==='linux'&&this.storage.getSelectedStorageBackend?.()==='basic_text')throw Error('secure_storage_unavailable');}
 async load():Promise<Credentials|null>{this.check();try{const value=JSON.parse(this.storage.decryptString(await fs.readFile(this.file())));if(Object.keys(value).sort().join(',')!=='PK,sid'||typeof value.PK!=='string')throw Error();const PK=decodeBase64(value.PK,32);if(value.sid!==hkdfSha256(PK,'','dhyan-room-v1',32).toString('hex'))throw Error();return {PK,sid:value.sid};}catch(e){if((e as NodeJS.ErrnoException).code==='ENOENT')return null;throw Error('credential_recovery_failed');}}
 async save(credentials:Credentials){this.check();try{await atomicWrite(this.file(),this.storage.encryptString(JSON.stringify({PK:credentials.PK.toString('base64'),sid:credentials.sid})));}catch{throw Error('credential_storage_failed');}}
 async remove(){try{await fs.unlink(this.file());}catch(e){if((e as NodeJS.ErrnoException).code!=='ENOENT')throw Error('credential_delete_failed');}}
}
