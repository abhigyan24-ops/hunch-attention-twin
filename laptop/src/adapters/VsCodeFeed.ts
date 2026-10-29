import {FeedClock} from './FeedClock';import type {FeedMessage,VsCodeStateMessage,EditMessage} from '../ingest/types';
export class VsCodeFeed extends FeedClock {
 private state:VsCodeStateMessage|null=null;private edits:EditMessage|null=null;private editAt=0;private trend:'rising'|'falling'|'stable'|null=null;
 private lost:number|null=null;private returned:number|null=null;private resume:{lostTs:number;returnedTs:number;observedTs:number;delayMs:number}|null=null;
 override connect(){super.connect();this.state=null;this.edits=null;this.trend=null;this.lost=null;this.returned=null;this.resume=null;}
 accept(message:FeedMessage){this.touch();if(message.kind==='ext_heartbeat')this.beat();
  if(message.kind==='vscode_state'){if(!message.windowFocused&&this.state?.windowFocused!==false){this.lost=message.ts;this.returned=null;this.resume=null;}else if(this.state?.windowFocused===false)this.returned=message.ts;this.state={...message};}
  if(message.kind==='edit_burst'){this.trend=this.edits?(message.errors>this.edits.errors?'rising':message.errors<this.edits.errors?'falling':'stable'):null;this.edits={...message};this.editAt=this.now();}
  if(message.kind==='resume_first_edit'){this.resume=this.lost!==null&&this.returned!==null&&message.ts>=this.returned?{lostTs:this.lost,returnedTs:this.returned,observedTs:message.ts,delayMs:message.delayMs}:null;}
 }
 snapshot(){const status=this.status(),burst=status.online&&this.edits&&this.now()-this.editAt<this.edits.windowSec*1000?this.edits:null;return {...status,state:status.online?this.state?.state??null:null,windowFocused:status.online&&this.state?.windowFocused===true,editsPerMin:burst?.editsPerMin??null,errorTrend:status.online?this.trend:null,resumeWindow:status.online&&this.resume?{...this.resume}:null};}
}
