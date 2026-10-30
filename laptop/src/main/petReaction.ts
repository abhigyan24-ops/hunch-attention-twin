import {DistractionAssessor,EscalationLadder,BubblePolicy,type Category,type Surface,type Evidence,type Level} from '../pet/pipPolicy';
import type {LaptopPipeline} from './LaptopPipeline';
import type {PetInputs} from '../pet/petInputBus';

export interface PetReaction {level:Level;message:string|null;expiresAt:number|null;motion:boolean;reason:string;counters:readonly {day:number;level:Level;shown:number;accepted:number;dismissed:number}[]}
/** Category-only adapter; it never receives raw AW responses or process names. */
export class PetReactionController {
 private assessor=new DistractionAssessor();private ladder=new EscalationLadder();private bubble=new BubblePolicy(42);
 private category:Category='unknown';private surface:Surface='other';private categoryAt=0;private surfaceAt=0;
 private lastTick=-1;private current:PetReaction={level:0,message:null,expiresAt:null,motion:false,reason:'no_data',counters:[]};
 private stopEvent:()=>void;
 snoozeUntil=0;breakUntil=0;dnd=false;calm=false;paused=false;reducedText=false;
 constructor(pipeline:LaptopPipeline){this.stopEvent=pipeline.onEvent(event=>{
  if(event.type==='window_change'&&typeof event.payload.appCat==='string'){this.category=event.payload.appCat as Category;this.categoryAt=event.ts;this.surface='other';}
  if(event.type==='tab_category'&&typeof event.payload.domainCat==='string'&&typeof event.payload.surface==='string'){this.category=event.payload.domainCat as Category;this.surface=event.payload.surface as Surface;this.categoryAt=event.ts;this.surfaceAt=event.ts;}
 });}
 snapshot(inputs:PetInputs,now:number):PetReaction {
  if(now<this.lastTick){this.assessor=new DistractionAssessor();this.ladder=new EscalationLadder();this.lastTick=-1;}
  if(now===this.lastTick)return this.current;
  this.lastTick=now;
  const local=inputs.local, date=new Date(now),category=this.category,surface=this.surface;
  const evidence:Evidence={nowMs:now,ts:local?.ts??-1,healthy:inputs.collector==='healthy',state:local?.laptopState??'offline',category,surface,declaredFocus:inputs.declaredSession!==null,storagePaused:inputs.storagePaused,
   remote:inputs.remote?{ts:inputs.remote.ts,connected:inputs.remote.connected,overlap:inputs.remote.secondScreenOverlap===true}:undefined};
  const assessment=this.assessor.step(evidence,this.ladder.thresholdMultiplier());
  const decision=this.ladder.decide(assessment,evidence,{minuteOfDay:date.getHours()*60+date.getMinutes(),dayKey:date.getFullYear()*10000+(date.getMonth()+1)*100+date.getDate(),breakUntilMs:this.breakUntil,snoozeUntilMs:this.snoozeUntil,dnd:this.dnd,paused:this.paused,calm:this.calm,reducedMotion:false});
  const prior=this.current,retain=decision.reason==='cooldown'&&prior.message!==null&&prior.expiresAt!==null&&now<prior.expiresAt;
  this.current={level:retain?prior.level:decision.level,message:retain?prior.message:decision.showBubble?this.bubble.next(decision.level,this.reducedText):null,expiresAt:retain?prior.expiresAt:decision.expiresAt,motion:decision.motion,reason:decision.reason,counters:this.ladder.counters.map(row=>({...row}))};
  return this.current;
 }
 feedback(kind:'accepted'|'dismissed',now:number){const date=new Date(now);this.ladder.feedback(kind,date.getFullYear()*10000+(date.getMonth()+1)*100+date.getDate(),this.current.level,this.category,this.surface,now);}
 stop(){this.stopEvent();}
}
