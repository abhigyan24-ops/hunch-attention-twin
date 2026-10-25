import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import schema from '../../../core-spec/event.schema.json';
import forbidden from '../../../core-spec/forbidden-fields.json';
import contextSchema from '../../../core-spec/context-snapshot.schema.json';
const ajv=new Ajv2020({allErrors:false,strict:true});addFormats(ajv);
ajv.addSchema(contextSchema);
const validate=ajv.compile(schema);const banned=new Set(forbidden);
function safe(value:unknown):boolean {if(!value||typeof value!=='object')return true;return Object.entries(value).every(([key,child])=>!banned.has(key)&&safe(child));}
/** Never expose AJV diagnostics containing user input. */
export function isPrivateEvent(event:unknown):boolean {try{return Buffer.byteLength(JSON.stringify(event),'utf8')<=8192&&safe(event)&&Boolean(validate(event));}catch{return false;}}

