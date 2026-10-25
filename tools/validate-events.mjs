import fs from 'node:fs';
import { pathToFileURL } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const read = name => JSON.parse(fs.readFileSync(new URL(`../core-spec/${name}`, import.meta.url), 'utf8'));
const forbidden = new Set(read('forbidden-fields.json'));
const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);
ajv.addSchema(read('context-snapshot.schema.json'));
const schemaValidator = ajv.compile(read('event.schema.json'));

// Return codes only: rejected input and Ajv values must never reach diagnostics.
export function validateEvent(event) {
  const errors = [];
  let encoded;
  try { encoded = JSON.stringify(event); } catch { return { valid: false, errors: ['not_json'] }; }
  if (encoded === undefined) return { valid: false, errors: ['not_json'] };
  if (Buffer.byteLength(encoded, 'utf8') > 8192) errors.push('event_too_large');
  const scan = value => {
    if (value === null || typeof value !== 'object') return;
    for (const key of Object.keys(value)) {
      if (forbidden.has(key)) errors.push('forbidden_field');
      scan(value[key]);
    }
  };
  scan(event);
  if (!schemaValidator(event)) errors.push('schema_invalid');
  return { valid: errors.length === 0, errors: [...new Set(errors)] };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const files = process.argv.slice(2);
  if (!files.length) { console.error('Usage: node tools/validate-events.mjs <event.json> [...]'); process.exitCode = 2; }
  let failed = 0;
  for (const file of files) {
    let result;
    try { result = validateEvent(JSON.parse(fs.readFileSync(file, 'utf8'))); }
    catch { result = { valid: false, errors: ['unreadable_or_invalid_json'] }; }
    if (!result.valid) { failed++; console.error(`Event rejected: ${result.errors.join(', ')}`); }
  }
  if (files.length) console.log(`Validated ${files.length} event file(s): ${files.length - failed} valid, ${failed} invalid.`);
  if (failed) process.exitCode = 1;
}
