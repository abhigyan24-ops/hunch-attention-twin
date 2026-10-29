import type {PetInputs} from './petInputBus';
export type PipBehavior='deep_focus'|'held'|'drifting'|'distracted'|'recovering'|'fatigued'|'second_screen_overlap';
export interface PetView {behavior:PipBehavior|null;availability:'no_data'|'stale'|'offline'|'unclassified'|'unknown'|'active';label:string;heldCount:number|null;calibration:'rule-based, not calibrated'}
const calibration='rule-based, not calibrated' as const;
/** Priority: no-data/stale/silent/uncertain local -> neutral; then fresh remote overlap,
 * confirmed held count, fatigue, release/celebrate, distracted, drift/nudge; then
 * fresh local flowing -> quiet working. Local stuck -> gentle pause cue only.
 * No remote signal is synthesized from local categories. Missing remote means local-only.
 * A remote connection becoming stale clears all remote behavioral claims.
 */
export function derivePetView(input:PetInputs,nowMs:number):PetView {
 const base={behavior:null,heldCount:null,calibration} as const;
 if(!input.local)return {...base,availability:'no_data',label:'No data yet'};
 if(nowMs<input.local.ts||nowMs-input.local.ts>=15000)return {...base,availability:'stale',label:'Laptop data is stale'};
 if(input.collector==='silent')return {...base,availability:'offline',label:'Collector offline'};
 if(input.collector==='unknown')return {...base,availability:'unknown',label:'Collector status unavailable'};
 if(input.local.laptopState==='offline')return {...base,availability:'unclassified',label:'Active but unclassified'};
 const remote=input.remote&&input.remote.connected&&nowMs>=input.remote.ts&&nowMs-input.remote.ts<30000?input.remote:null;
 const recentEvent=remote?.petEvent&&nowMs>=remote.petEvent.ts&&nowMs-remote.petEvent.ts<15000?remote.petEvent:null;
 let behavior:PipBehavior|null=null;
 if(remote?.secondScreenOverlap)behavior='second_screen_overlap';
 else if(remote&&remote.heldCount>0)behavior='held';
 else if(remote?.fatigueBand==='high')behavior='fatigued';
 else if(recentEvent&&['released','celebrate'].includes(recentEvent.kind))behavior='recovering';
 else if(remote?.focusState==='recovering')behavior='recovering';
 else if(remote?.focusState==='distracted')behavior='distracted';
 else if(remote?.focusState==='drifting'||recentEvent?.kind==='nudge')behavior='drifting';
 else if(input.local.laptopState==='stuck')behavior='drifting';
 else if(input.local.laptopState==='flowing')behavior='deep_focus';
 return {behavior,heldCount:remote?remote.heldCount:null,availability:'active',label:input.local.laptopState==='flowing'?'Working (local rule)':input.local.laptopState.replace('_',' '),calibration};
}
