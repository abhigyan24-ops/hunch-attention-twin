import type {CategorizedEvent} from '../main/jsonlStore';
/** Conservative observed intervals: at most one5second heartbeat, no inference from app categories. */
export function observedFocusToday(events:readonly CategorizedEvent[],nowMs:number):{minutes:number|null;provenance:'observed';label:string}{
 const start=new Date(nowMs);start.setHours(0,0,0,0);let totalMs=0,observedMs=0;
 const ordered=events.filter(e=>e.device==='laptop'&&e.ts>=start.getTime()-5000&&e.ts<=nowMs).slice().sort((a,b)=>a.ts-b.ts||a.seq-b.seq);
 const gaps=new Set(ordered.filter(e=>e.type==='gap').map(e=>e.ts));
 for(let i=0;i<ordered.length;i++){
  const event=ordered[i];if(event.type!=='state_snapshot'||gaps.has(event.ts))continue;
  const begin=Math.max(event.ts,start.getTime()),end=Math.min(ordered[i+1]?.ts??nowMs,event.ts+5000,nowMs);
  if(end<=begin)continue;observedMs+=end-begin;
  if(event.payload.laptopState==='flowing')totalMs+=end-begin;
 }
 return {minutes:observedMs>0?totalMs/60000:null,provenance:'observed',label:observedMs>0?'Observed local rule intervals; gaps excluded':'No data: persisted focus intervals are not available'};
}
