// TODO (12.1,25.9): category-only ring buffer, asynchronous writes, rotation,
// retention, disk-full diagnostics, dedup, replay and partial-line recovery.
// Fail explicitly until implemented; never pretend persistence succeeded.
export interface CategorizedEvent { schemaVersion:string; id:string; deviceId:string; device:'laptop'|'phone'; seq:number; source:string; type:string; ts:number; ingestedTs?:number; synthetic:boolean; payload:Readonly<Record<string,unknown>> }
export async function appendEvent(_event:CategorizedEvent):Promise<void>{throw new Error('TODO: appendEvent');}
export async function readRecentEvents(_limit:number):Promise<readonly CategorizedEvent[]>{throw new Error('TODO: readRecentEvents');}
export async function rotateIfNeeded():Promise<void>{throw new Error('TODO: rotateIfNeeded');}
export async function recoverPartialLine():Promise<void>{throw new Error('TODO: recoverPartialLine');}
