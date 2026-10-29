// Privacy boundary: only categorized identifiers and numeric counts are exposed.
export type RawLaptopEvent =
 | {kind:'process';ts:number;process:string}
 | {kind:'domain';ts:number;domainCategory:string}
 | {kind:'idle';ts:number;idleSec:number}
 | {kind:'edit';ts:number;editsPerMin:number;saves:number;errors:number;warnings:number}
 | {kind:'resume';ts:number;delayMs:number}
 | {kind:'tab';ts:number;domainCat:string;surface:'feed'|'shorts'|'watch'|'chat'|'editor'|'other';videoState?:'playing'|'paused'|'none';scrollCount?:number;tabActive:boolean};
export interface LaptopAdapter {start():void;stop():void;onRaw(callback:(event:RawLaptopEvent)=>void):void;}
