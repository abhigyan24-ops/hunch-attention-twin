import fs from 'node:fs';import Ajv2020 from 'ajv/dist/2020.js';import addFormats from 'ajv-formats';import forbidden from '../../../core-spec/forbidden-fields.json';import eventSchema from '../../../core-spec/event.schema.json';import contextSchema from '../../../core-spec/context-snapshot.schema.json';
const ajv=new Ajv2020({strict:true});addFormats(ajv);ajv.addSchema(contextSchema);ajv.addSchema(eventSchema,'https://dhyan.local/event.schema.json');const banned=new Set(forbidden);
const directory=new URL('../../../contracts/sync/schemas/',import.meta.url);
const validators=new Map(fs.readdirSync(directory).filter(f=>f.endsWith('.schema.json')).map(file=>[file.slice(0,-12),ajv.compile(JSON.parse(fs.readFileSync(new URL(file,directory),'utf8')))]));
export function privateFields(value:unknown):boolean{try{return !value||typeof value!=='object'||Object.entries(value).every(([k,v])=>!banned.has(k)&&privateFields(v));}catch{return false;}}
export function validate(name:string,value:unknown){try{const fn=validators.get(name);if(!fn)return false;const valid=Boolean(fn(value));fn.errors=null;return valid;}catch{return false;}}
export function validOuter(value:unknown){if(!value||typeof value!=='object'||Array.isArray(value))return false;const f=value as Record<string,unknown>;return validate(typeof f.t==='string'&&['pair_offer','pair_join','pair_reveal','pair_confirm','pair_cancel','hello'].includes(f.t)?f.t:'encrypted_frame',value);}
export function validBody(type:string,value:unknown){return privateFields(value)&&validate(type,value);}
export type Body=Record<string,any>;
export function relayEndpoint(input:string,development:boolean){let url:URL;try{url=new URL(input);}catch{throw Error('invalid_relay_url');}if(url.username||url.password||url.search||url.hash||url.pathname!=='/ws'||!(url.protocol==='wss:'||development&&url.protocol==='ws:'&&url.hostname==='127.0.0.1'))throw Error('invalid_relay_url');return url.href;}
export function roomUrl(relay:string,room:string,role:string){const url=new URL(relay);url.searchParams.set('room',room);url.searchParams.set('device',role);return url.href;}
