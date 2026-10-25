import * as fs from 'node:fs/promises';
import path from 'node:path';
import categories from '../../../core-spec/categories.json';
import {categorizeProcess,type Category} from './normalize';
export class ProcessLabels {
 private values:Record<string,Category>={};private queue=Promise.resolve();
 constructor(private directory:string){}
 async load(){try{const values=JSON.parse(await fs.readFile(path.join(this.directory,'labels.json'),'utf8'));if(!values||typeof values!=='object'||Array.isArray(values)||!Object.entries(values).every(([key,value])=>/^[\w .-]{1,100}$/.test(key)&&categories.categories.includes(value as Category)))throw Error('invalid_labels');this.values=values;}catch(error){if((error as NodeJS.ErrnoException).code!=='ENOENT')throw Error('labels_recovery_required');}}
 category(process:string):Category{return this.values[process.toLowerCase()]??categorizeProcess(process);}
 async labelOnce(process:string,category:Category):Promise<boolean>{const operation=this.queue.then(async()=>{const key=process.toLowerCase();if(!/^[\w .-]{1,100}$/.test(key)||!categories.categories.includes(category))return false;if(Object.hasOwn(this.values,key))return false;const next={...this.values,[key]:category};await fs.mkdir(this.directory,{recursive:true});await fs.writeFile(path.join(this.directory,'labels.json.tmp'),JSON.stringify(next));await fs.rename(path.join(this.directory,'labels.json.tmp'),path.join(this.directory,'labels.json'));this.values=next;return true;});this.queue=operation.then(()=>{},()=>{});return operation;}
}
export interface NormalizedChange {type:'window_change'|'idle_start'|'idle_end';source:'window'|'idle';ts:number;payload:Record<string,unknown>}
export class WindowNormalizer {
 private active:string|null=null;private since=0;private candidate:{process:string;since:number}|null=null;private idle=false;private previousIdle=0;
 constructor(private category:(process:string)=>Category){}
 process(process:string,ts:number):NormalizedChange|null {
 if(!/^[\w .-]{1,100}$/.test(process)||!Number.isSafeInteger(ts)||ts<0)return null;
 if(this.active===process){this.candidate=null;return null;}
 if(this.active!==null){if(this.candidate?.process!==process){this.candidate={process,since:ts};return null;}if(ts-this.candidate.since<3000)return null;}
 const minutesInPrev=this.active===null?0:Math.max(0,ts-this.since)/60000;this.active=process;this.since=ts;this.candidate=null;
 return {type:'window_change',source:'window',ts,payload:{app:process,appCat:this.category(process),minutesInPrev}};
 }
 idleChange(idleSec:number,ts:number):NormalizedChange|null {if(!Number.isFinite(idleSec)||idleSec<0)return null;const idle=idleSec>=60,previous=this.previousIdle;this.previousIdle=idleSec;if(idle===this.idle)return null;this.idle=idle;return {type:idle?'idle_start':'idle_end',source:'idle',ts,payload:{idleSec:idle?idleSec:previous}};}
}
