import type {LaptopAdapter,RawLaptopEvent} from './LaptopAdapter';
import {categorizeDomain} from '../normalizer/normalize';
/** AW data is reduced here, before callbacks. Never return its response bodies. */
export class AwAdapter implements LaptopAdapter {
 private callback:(event:RawLaptopEvent)=>void=()=>{};private timer:ReturnType<typeof setInterval>|undefined;private busy=false;private stopped=false;
 constructor(private base='http://127.0.0.1:5600',private request:typeof fetch=fetch,private now=Date.now,private onFailure:()=>void=()=>{}){}
 onRaw(callback:(event:RawLaptopEvent)=>void){this.callback=callback;}
 start(){if(this.timer)return;this.stopped=false;void this.poll();this.timer=setInterval(()=>void this.poll(),5000);}
 stop(){this.stopped=true;if(this.timer)clearInterval(this.timer);this.timer=undefined;}
 private async get(endpoint:string):Promise<unknown>{const response=await this.request(this.base+endpoint,{signal:AbortSignal.timeout(4000)});if(!response.ok)throw Error('aw_unavailable');return response.json();}
 async poll(){if(this.busy)return;this.busy=true;try{
  const buckets=await this.get('/api/0/buckets/');if(!buckets||typeof buckets!=='object'||Array.isArray(buckets))throw Error('aw_shape');
  for(const id of Object.keys(buckets)){const kind=id.startsWith('aw-watcher-window')?'window':id.startsWith('aw-watcher-afk')?'afk':id.startsWith('aw-watcher-web')?'web':null;if(!kind)continue;
   const rows=await this.get('/api/0/buckets/'+encodeURIComponent(id)+'/events?limit=1');if(!Array.isArray(rows)||rows.length===0)continue;
   const row=rows[0];if(!row||typeof row!=='object'||!row.data||typeof row.data!=='object')continue;const data=row.data,ts=Date.parse(row.timestamp);if(!Number.isFinite(ts)||ts>this.now()+300000||this.now()-ts>15000+Math.max(0,Number(row.duration)||0)*1000)continue;
   if(this.stopped)break;
   // Timestamp represents the fresh endpoint of an AW interval, not a newly invented observation.
   const observedTs=Math.min(this.now(),ts+Math.max(0,Number(row.duration)||0)*1000);
   if(kind==='window'&&typeof data.app==='string'&&/^[\w .-]{1,100}$/.test(data.app))this.callback({kind:'process',ts:observedTs,process:data.app});
   if(kind==='afk'&&['afk','not-afk'].includes(data.status))this.callback({kind:'idle',ts:observedTs,idleSec:data.status==='afk'?Math.max(0,Number(row.duration)||0):0});
   if(kind==='web'&&typeof data.url==='string'){try{const category=categorizeDomain(new URL(data.url).hostname);this.callback({kind:'domain',ts:observedTs,domainCategory:category});}catch{/* raw URLs never escape this boundary */}}
  }
 }catch{if(!this.stopped)this.onFailure();}finally{this.busy=false;}}
}
