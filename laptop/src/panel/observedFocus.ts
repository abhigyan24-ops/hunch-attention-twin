import type {CategorizedEvent} from '../main/jsonlStore';
/** Conservative observed intervals: at most one 5-second heartbeat interval, never bridge a gap. */
export function observedFocusToday(events:readonly CategorizedEvent[],nowMs:number):{minutes:number|null;provenance:'observed';label:string}{
 const start=new Date(nowMs);start.setHours(0,0,0,0);let totalMs=0,snapshots=0;
 const ordered=events.filter(e=>e.device==='laptop'&&e.ts>=start.getTime()-5000&&e.ts<=nowMs).slice().sort((a,b)=>a.ts-b.ts||a.seq-b.seq);
 for(let i=0;i<ordered.length;i++){const event=ordered[i];if(event.type!=='state_snapshot')continue;snapshots++;if(event.payload.laptopState!=='flowing')continue;
  const end=Math.min(ordered[i+1]?.ts??nowMs,event.ts+5000,nowMs);totalMs+=Math.max(0,end-Math.max(event.ts,start.getTime()));
 }
 return {minutes:snapshots?totalMs/60000:null,provenance:'observed',label:snapshots?'Observed local rule intervals; gaps excluded':'No data: persisted focus snapshots are not available'};
}
