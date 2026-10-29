import Ajv2020 from 'ajv/dist/2020.js';
import auth from '../../../contracts/sync/schemas/ingest_auth.schema.json';
import heartbeat from '../../../contracts/sync/schemas/ingest_ext_heartbeat.schema.json';
import edit from '../../../contracts/sync/schemas/ingest_edit_burst.schema.json';
import resume from '../../../contracts/sync/schemas/ingest_resume_first_edit.schema.json';
import state from '../../../contracts/sync/schemas/ingest_vscode_state.schema.json';
import tab from '../../../contracts/sync/schemas/ingest_tab_category.schema.json';
import forbidden from '../../../core-spec/forbidden-fields.json';
import type {IngestMessage} from './types';
const ajv=new Ajv2020({strict:true}),banned=new Set(forbidden);
const validators=new Map([auth,heartbeat,edit,resume,state,tab].map(schema=>[schema.properties.kind.const,ajv.compile(schema)]));
function privateKeys(value:unknown):boolean {return !value||typeof value!=='object'||Object.entries(value).every(([key,child])=>!banned.has(key)&&privateKeys(child));}
export function validateIngest(value:unknown):value is IngestMessage {try{if(!value||typeof value!=='object'||Array.isArray(value)||!privateKeys(value))return false;const kind=(value as {kind?:unknown}).kind;if(typeof kind!=='string')return false;const validator=validators.get(kind);if(!validator)return false;const valid=Boolean(validator(value));validator.errors=null;return valid;}catch{return false;}}
