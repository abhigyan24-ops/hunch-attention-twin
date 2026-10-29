import type {FeedStatus} from '../ingest/types';
export class FeedClock {
 protected connected=false;protected heartbeat:number|null=null;protected seen:number|null=null;
 constructor(protected now=Date.now){}
 connect(){this.connected=true;this.heartbeat=null;this.seen=null;}
 disconnect(){this.connected=false;this.heartbeat=null;}
 beat(){this.heartbeat=this.now();this.touch();}
 protected touch(){this.seen=this.now();}
 status():FeedStatus{return {connected:this.connected,online:this.connected&&this.heartbeat!==null&&this.now()-this.heartbeat>=0&&this.now()-this.heartbeat<15000,lastSeenTs:this.seen,lastHeartbeatTs:this.heartbeat};}
}
