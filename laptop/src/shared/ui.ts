import type {PetInputs} from '../pet/petInputBus';
import type {PetView} from '../pet/petStateMachine';
export interface UiSnapshot {inputs:PetInputs;pet:PetView;focus:{minutes:number|null;provenance:'observed';label:string};pairing:{status:string;label:string};diagnostics:{paused?:boolean;eventsWritten?:number;adapterErrors?:number;dropped?:number};ts:number;demo:boolean}
export interface DhyanUi {schemaVersion:string;snapshot():Promise<UiSnapshot>;declareSession(input:{task:string;plannedMin:number;strict:boolean}):Promise<{ok:boolean;message:string}>;labelProcess(process:string,category:string):Promise<boolean>;openPanel():void;setIgnoreMouseEvents(ignore:boolean):void}
