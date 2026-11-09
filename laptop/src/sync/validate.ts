import Ajv2020 from 'ajv/dist/2020.js';import addFormats from 'ajv-formats';import forbidden from '../../../core-spec/forbidden-fields.json';import eventSchema from '../../../core-spec/event.schema.json';import contextSchema from '../../../core-spec/context-snapshot.schema.json';
import ackSchema from '../../../contracts/sync/schemas/ack.schema.json';
import cmdAckSchema from '../../../contracts/sync/schemas/cmd_ack.schema.json';
import commandSchema from '../../../contracts/sync/schemas/command.schema.json';
import encryptedFrameSchema from '../../../contracts/sync/schemas/encrypted_frame.schema.json';
import errorSchema from '../../../contracts/sync/schemas/error.schema.json';
import eventsSchema from '../../../contracts/sync/schemas/events.schema.json';
import frameEnvelopeSchema from '../../../contracts/sync/schemas/frame_envelope.schema.json';
import heldListSchema from '../../../contracts/sync/schemas/held_list.schema.json';
import helloSchema from '../../../contracts/sync/schemas/hello.schema.json';
import ingestAuthSchema from '../../../contracts/sync/schemas/ingest_auth.schema.json';
import ingestEditBurstSchema from '../../../contracts/sync/schemas/ingest_edit_burst.schema.json';
import ingestExtHeartbeatSchema from '../../../contracts/sync/schemas/ingest_ext_heartbeat.schema.json';
import ingestResumeFirstEditSchema from '../../../contracts/sync/schemas/ingest_resume_first_edit.schema.json';
import ingestTabCategorySchema from '../../../contracts/sync/schemas/ingest_tab_category.schema.json';
import ingestVscodeStateSchema from '../../../contracts/sync/schemas/ingest_vscode_state.schema.json';
import pairCancelSchema from '../../../contracts/sync/schemas/pair_cancel.schema.json';
import pairConfirmSchema from '../../../contracts/sync/schemas/pair_confirm.schema.json';
import pairJoinSchema from '../../../contracts/sync/schemas/pair_join.schema.json';
import pairOfferSchema from '../../../contracts/sync/schemas/pair_offer.schema.json';
import pairRevealSchema from '../../../contracts/sync/schemas/pair_reveal.schema.json';
import petEventSchema from '../../../contracts/sync/schemas/pet_event.schema.json';
import pingSchema from '../../../contracts/sync/schemas/ping.schema.json';
import pongSchema from '../../../contracts/sync/schemas/pong.schema.json';
import stateSchema from '../../../contracts/sync/schemas/state.schema.json';
const ajv=new Ajv2020({strict:true});addFormats(ajv);ajv.addSchema(contextSchema);ajv.addSchema(eventSchema,'https://dhyan.local/event.schema.json');const banned=new Set(forbidden);
const schemas:[string,Record<string,unknown>][]=[['ack',ackSchema],['cmd_ack',cmdAckSchema],['command',commandSchema],['encrypted_frame',encryptedFrameSchema],['error',errorSchema],['events',eventsSchema],['frame_envelope',frameEnvelopeSchema],['held_list',heldListSchema],['hello',helloSchema],['ingest_auth',ingestAuthSchema],['ingest_edit_burst',ingestEditBurstSchema],['ingest_ext_heartbeat',ingestExtHeartbeatSchema],['ingest_resume_first_edit',ingestResumeFirstEditSchema],['ingest_tab_category',ingestTabCategorySchema],['ingest_vscode_state',ingestVscodeStateSchema],['pair_cancel',pairCancelSchema],['pair_confirm',pairConfirmSchema],['pair_join',pairJoinSchema],['pair_offer',pairOfferSchema],['pair_reveal',pairRevealSchema],['pet_event',petEventSchema],['ping',pingSchema],['pong',pongSchema],['state',stateSchema]];
const validators=new Map(schemas.map(([name,schema])=>[name,ajv.compile(schema)]));
export function privateFields(value:unknown):boolean{try{return !value||typeof value!=='object'||Object.entries(value).every(([k,v])=>!banned.has(k)&&privateFields(v));}catch{return false;}}
export function validate(name:string,value:unknown){try{const fn=validators.get(name);if(!fn)return false;const valid=Boolean(fn(value));fn.errors=null;return valid;}catch{return false;}}
export function validOuter(value:unknown){if(!value||typeof value!=='object'||Array.isArray(value))return false;const f=value as Record<string,unknown>;return validate(typeof f.t==='string'&&['pair_offer','pair_join','pair_reveal','pair_confirm','pair_cancel','hello'].includes(f.t)?f.t:'encrypted_frame',value);}
export function validBody(type:string,value:unknown){return privateFields(value)&&validate(type,value);}
export type Body=Record<string,any>;
export function relayEndpoint(input:string,development:boolean){let url:URL;try{url=new URL(input);}catch{throw Error('invalid_relay_url');}if(url.username||url.password||url.search||url.hash||url.pathname!=='/ws'||!(url.protocol==='wss:'||development&&url.protocol==='ws:'&&url.hostname==='127.0.0.1'))throw Error('invalid_relay_url');return url.href;}
export function roomUrl(relay:string,room:string,role:string){const url=new URL(relay);url.searchParams.set('room',room);url.searchParams.set('device',role);return url.href;}
