import type {LaptopAdapter,RawLaptopEvent} from './LaptopAdapter';
import {categorizeDomain} from '../normalizer/normalize';
/** Raw AW responses never cross this adapter. URLs live only until hostname categorization. */
export class AwAdapter implements LaptopAdapter {
 private callback:(event:RawLaptopEvent)=>void=()=>{};private timer:ReturnType<typeof setInterval>|undefined;private busy=false;private stopped=false;
 private discovered:string[]=[];
 constructor(private base='http://127.0.0.1:5600',private request:typeof fetch=fetch,private now=Date.now,private onFailure:()=>void=()=>{}){}
 get bucketIds():readonly string[]{return [...this.discovered];}
 onRaw(callback:(event:RawLaptopEvent)=>void){this.callback=callback;}
 start(){if(this.timer)return;this.stopped=false;void this.poll();this.timer=setInterval(()=>void this.poll(),5000);}
 stop(){this.stopped=true;if(this.timer)clearInterval(this.timer);this.timer=undefined;}
 private failure(){try{this.onFailure();}catch{/* diagnostics never stop polling */}}
 private send(event:RawLaptopEvent){if(!this.stopped)try{this.callback(event);}catch{this.failure();}}
 private async get(endpoint:string):Promise<unknown>{const response=await this.request(this.base+endpoint,{signal:AbortSignal.timeout(4000)});if(!response.ok)throw Error('aw_unavailable');return response.json();}
 async poll(){if(this.busy)return;this.busy=true;try{
  const buckets=await this.get('/api/0/buckets/');if(!buckets||typeof buckets!=='object'||Array.isArray(buckets))throw Error('aw_shape');
  this.discovered=Object.keys(buckets).filter(id=>/^aw-watcher-(window|afk|web|vscode)(?:[_-]|$)/.test(id));
  for(const id of this.discovered){if(this.stopped)break;
   const kind=id.startsWith('aw-watcher-window')?'window':id.startsWith('aw-watcher-afk')?'afk':id.startsWith('aw-watcher-web')?'web':null;
   if(!kind)continue; // vscode is discovered, optional counts will enter through 7C's authenticated feed.
   try{const rows=await this.get('/api/0/buckets/'+encodeURIComponent(id)+'/events?limit=1');if(!Array.isArray(rows))throw Error('aw_shape');if(rows.length===0)continue;
    const row=rows[0];if(!row||typeof row!=='object'||!row.data||typeof row.data!=='object')throw Error('aw_shape');
    const ts=Date.parse(row.timestamp),duration=Number(row.duration);if(!Number.isFinite(ts)||!Number.isFinite(duration)||duration<0||ts>this.now()+300000)continue;
    const observedTs=Math.min(this.now(),ts+duration*1000);if(this.now()-observedTs>=15000)continue;
    const data=row.data;
    if(kind==='window'&&typeof data.app==='string'&&/^[\w .-]{1,100}$/.test(data.app))this.send({kind:'process',ts:observedTs,process:data.app});
    if(kind==='afk'&&['afk','not-afk'].includes(data.status))this.send({kind:'idle',ts:observedTs,idleSec:data.status==='afk'?duration:0});
    if(kind==='web'&&typeof data.url==='string'){let category;try{category=categorizeDomain(new URL(data.url).hostname);}catch{continue;}this.send({kind:'domain',ts:observedTs,domainCategory:category});}
   }catch{if(!this.stopped)this.failure();} // An optional or broken bucket must not suppress required feeds.
  }
 }catch{if(!this.stopped)this.failure();}finally{this.busy=false;}}
}
