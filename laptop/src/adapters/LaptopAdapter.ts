// Privacy boundary: only categorized identifiers and numeric counts are exposed.
export type RawLaptopEvent =
 | {kind:'process';ts:number;process:string}
 | {kind:'domain';ts:number;domainCategory:string}
 | {kind:'idle';ts:number;idleSec:number}
 | {kind:'edit';ts:number;editsPerMin:number;saves:number;errors:number;warnings:number};
export interface LaptopAdapter {start():void;stop():void;onRaw(callback:(event:RawLaptopEvent)=>void):void;}
