export type LaptopState='flowing'|'stuck'|'idle_open'|'browsing'|'gaming'|'away'|'offline';
export interface FocusInputs {nowMs:number;lastReportMs:number|null;category:string;idleSec:number;switchesPerMin:number;continuous:boolean;stuckEvidence:boolean;browserFocused:boolean}
/** Unclassified activity remains offline rather than fabricating a focus state. */
export function inferFocusState(input:FocusInputs):LaptopState {
 if(input.lastReportMs===null||input.nowMs-input.lastReportMs>=15000)return 'offline';
 if(input.idleSec>=300)return 'away';const work=['coding','docs','learning'].includes(input.category);
 if(work&&input.idleSec>=60)return 'idle_open';if(work&&input.stuckEvidence)return 'stuck';
 if(work&&input.continuous&&input.switchesPerMin<3)return 'flowing';
 if(input.category==='games')return 'gaming';if(input.browserFocused&&['social','news','shopping','video'].includes(input.category))return 'browsing';return 'offline';
}
export interface FocusSnapshot {laptopState:LaptopState;minutesInState:number;confidence:number;ts:number}
export class FocusRunner {
 private state:LaptopState='offline';private since:number;private timer:ReturnType<typeof setInterval>|undefined;
 private callbacks=new Set<(state:FocusSnapshot)=>void>();
 constructor(private inputs:()=>Omit<FocusInputs,'nowMs'>,private now=Date.now,private onOffline:()=>void=()=>{}){this.since=now();}
 onState(callback:(state:FocusSnapshot)=>void){this.callbacks.add(callback);return ()=>this.callbacks.delete(callback);}
 tick(){const ts=this.now(),next=inferFocusState({...this.inputs(),nowMs:ts});if(next!==this.state){this.state=next;this.since=ts;if(next==='offline')this.onOffline();}const snapshot={laptopState:next,minutesInState:Math.max(0,ts-this.since)/60000,confidence:0,ts};for(const cb of this.callbacks){try{cb({...snapshot});}catch{/* one subscriber cannot stop heartbeat */}}return snapshot;}
 start(){if(!this.timer){this.tick();this.timer=setInterval(()=>this.tick(),5000);}}
 stop(){if(this.timer)clearInterval(this.timer);this.timer=undefined;}
}
export function currentFocusState():LaptopState{return 'offline';}
