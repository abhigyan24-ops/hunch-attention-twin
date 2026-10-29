import {PetInputBus,unpairedUi,type PetInputs} from '../pet/petInputBus';
import {derivePetView,type PipBehavior} from '../pet/petStateMachine';
import type {FocusSnapshot} from '../state/focusState';
import type {LaptopPipeline} from './LaptopPipeline';
import type {UiSnapshot} from '../shared/ui';
export class PetBridge {
 readonly bus=new PetInputBus();private local:FocusSnapshot|null=null;private lastGap=-Infinity;private lastActivity=-Infinity;
 private dispose:Array<()=>void>=[];private demo:PipBehavior|null=null;
 constructor(private pipeline:LaptopPipeline,private now=Date.now){
  this.dispose.push(pipeline.onEvent(event=>{if(event.type==='gap'&&event.payload.reason==='adapter_offline')this.lastGap=event.ts;else if(['window_change','idle_start','idle_end'].includes(event.type))this.lastActivity=event.ts;this.updateLocal();}));
  this.dispose.push(pipeline.onState(state=>{this.local=state;if(state.laptopState!=='offline')this.lastActivity=state.ts;this.updateLocal();}));
 }
 private updateLocal(){if(!this.local)return;const collector=this.lastGap>=this.local.ts&&this.lastGap>=this.lastActivity?'silent':this.lastActivity>=this.lastGap&&this.now()-this.lastActivity<15000?'healthy':'unknown';this.bus.setLocal(this.local,collector);}
 simulate(behavior:PipBehavior|null){this.demo=behavior;}
 snapshot(focus:UiSnapshot['focus']):UiSnapshot {
  this.bus.setStoragePaused(this.pipeline.diagnostics.paused);let inputs=this.bus.snapshot();
  if(this.demo){const ts=this.now();inputs={local:{laptopState:'flowing',confidence:0,minutesInState:0,ts},collector:'healthy',storagePaused:inputs.storagePaused,declaredSession:null,remote:{ts,connected:true,heldCount:this.demo==='held'?1:0,heldItems:[],focusState:this.demo==='distracted'?'distracted':this.demo==='drifting'?'drifting':this.demo==='recovering'?'recovering':'working',fatigueBand:this.demo==='fatigued'?'high':'low',secondScreenOverlap:this.demo==='second_screen_overlap'}};}
  return {inputs,pet:derivePetView(inputs,this.now()),focus:this.demo?{minutes:null,provenance:'observed',label:'DEV SIMULATION: no observed totals'}:focus,pairing:unpairedUi.snapshot(),diagnostics:this.pipeline.diagnostics,ts:this.now(),demo:this.demo!==null};
 }
 stop(){for(const dispose of this.dispose)dispose();this.dispose=[];}
}
