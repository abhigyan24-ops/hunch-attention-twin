import fs from 'node:fs';
import { pathToFileURL } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';

const schema = JSON.parse(fs.readFileSync(new URL('./persona.schema.json',import.meta.url),'utf8'));
const validate = new Ajv2020({strict:true}).compile(schema);
export const personaNames = ['rahul','anika','kabir'];
export function validatePersona(persona) {
  return validate(persona) ? {valid:true,errors:[]} : {valid:false,errors:['persona_schema_invalid']};
}
export function validatePersonas() {
  return personaNames.map(name => ({name,...validatePersona(JSON.parse(fs.readFileSync(new URL(`./${name}.json`,import.meta.url),'utf8')))}));
}
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  try {
    const results=validatePersonas();
    for(const r of results)console.log(`${r.name}: ${r.valid?'PASS':'FAIL'}`);
    if(results.some(r=>!r.valid))process.exitCode=1;
  } catch {console.error('Persona validation failed');process.exitCode=1;}
}
