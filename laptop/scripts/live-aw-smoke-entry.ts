import {AwAdapter} from '../src/adapters/AwAdapter';
import {categorizeProcess} from '../src/normalizer/normalize';
import {FocusRunner} from '../src/state/focusState';
export async function runSmoke(){
 const counts:Record<string,number>={};let category='unknown',lastReportMs:number|null=null,idleSec=0,browserFocused=false,changes=0,lastState='offline';
 const aw=new AwAdapter();const focus=new FocusRunner(()=>({lastReportMs,category,idleSec,switchesPerMin:0,continuous:idleSec<60,stuckEvidence:false,browserFocused}));
 aw.onRaw(event=>{if(event.kind==='process'){category=categorizeProcess(event.process);lastReportMs=event.ts;browserFocused=/^(chrome|msedge|firefox|brave)(\.exe)?$/i.test(event.process);counts[category]=(counts[category]??0)+1;}if(event.kind==='idle')idleSec=event.idleSec;if(event.kind==='domain'){counts[event.domainCategory]=(counts[event.domainCategory]??0)+1;if(browserFocused)category=event.domainCategory;}});
 focus.onState(state=>{if(state.laptopState!==lastState){changes++;lastState=state.laptopState;}});
 aw.start();focus.start();await new Promise(resolve=>setTimeout(resolve,60000));aw.stop();focus.stop();
 console.log(JSON.stringify({bucketIds:aw.bucketIds,categoryCounts:counts,stateChanges:changes}));
}
