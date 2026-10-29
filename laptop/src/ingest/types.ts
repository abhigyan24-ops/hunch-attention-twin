export type ClientKind='vscode'|'browser';
export type AuthMessage={kind:'auth';token:string;client:ClientKind;clientVersion:string};
export type HeartbeatMessage={kind:'ext_heartbeat';ts:number};
export type EditMessage={kind:'edit_burst';ts:number;editsPerMin:number;saves:number;errors:number;warnings:number;windowSec:number};
export type ResumeMessage={kind:'resume_first_edit';ts:number;delayMs:number};
export type VsCodeStateMessage={kind:'vscode_state';ts:number;state:'flowing'|'stuck'|'idle_open';windowFocused:boolean;debugging?:boolean};
export type TabMessage={kind:'tab_category';ts:number;domainCat:string;surface:'feed'|'shorts'|'watch'|'chat'|'editor'|'other';videoState?:'playing'|'paused'|'none';scrollCount?:number;tabActive:boolean};
export type FeedMessage=HeartbeatMessage|EditMessage|ResumeMessage|VsCodeStateMessage|TabMessage;
export type IngestMessage=AuthMessage|FeedMessage;
export interface FeedStatus {connected:boolean;online:boolean;lastSeenTs:number|null;lastHeartbeatTs:number|null}
