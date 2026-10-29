import {FeedClock} from './FeedClock';import type {FeedMessage,TabMessage} from '../ingest/types';
export class BrowserFeed extends FeedClock {
 private tab:TabMessage|null=null;
 override connect(){super.connect();this.tab=null;}
 accept(message:FeedMessage){this.touch();if(message.kind==='ext_heartbeat')this.beat();if(message.kind==='tab_category')this.tab={...message};}
 snapshot(){const status=this.status();return {...status,tab:status.online&&this.tab?{...this.tab}:null};}
}
