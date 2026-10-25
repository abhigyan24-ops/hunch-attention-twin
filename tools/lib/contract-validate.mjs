import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '../..');

const SCHEMAS_DIR = path.join(ROOT, 'contracts/sync/schemas');
const FORBIDDEN_FIELDS = JSON.parse(fs.readFileSync(path.join(ROOT, 'core-spec/forbidden-fields.json'), 'utf8'));
const FORBIDDEN_SET = new Set(FORBIDDEN_FIELDS);

const ajv = new Ajv2020({ allErrors: true, strict: false });
addFormats(ajv);

// Load core-spec event schema
const coreEventSchema = JSON.parse(fs.readFileSync(path.join(ROOT, 'core-spec/event.schema.json'), 'utf8'));
ajv.addSchema(coreEventSchema, 'https://dhyan.local/event.schema.json');

// Load sync schemas
const schemaFiles = fs.readdirSync(SCHEMAS_DIR).filter(f => f.endsWith('.schema.json'));
const validators = {};

for (const file of schemaFiles) {
  const fullPath = path.join(SCHEMAS_DIR, file);
  const schema = JSON.parse(fs.readFileSync(fullPath, 'utf8'));
  const name = file.replace('.schema.json', '');
  try {
    validators[name] = ajv.compile(schema);
  } catch (err) {
    throw new Error(`Failed to compile schema ${file}: ${err.message}`);
  }
}

/**
 * Validates any outer frame (pair_offer, pair_join, pair_reveal, pair_confirm, pair_cancel, hello, encrypted_frame).
 */
export function validateFrame(frame) {
  if (!frame || typeof frame !== 'object') {
    return { valid: false, errors: ['frame must be an object'] };
  }
  const type = frame.t;
  if (!type) {
    return { valid: false, errors: ['missing frame type "t"'] };
  }

  // Encrypted session frames
  if (frame.c !== undefined) {
    const val = validators['encrypted_frame'];
    const valid = val(frame);
    return { valid, errors: valid ? [] : val.errors.map(e => `${e.instancePath || '/'} ${e.message}`) };
  }

  // Pairing & Hello frames
  const val = validators[type];
  if (!val) {
    return { valid: false, errors: [`unknown outer frame type: ${type}`] };
  }
  const valid = val(frame);
  return { valid, errors: valid ? [] : val.errors.map(e => `${e.instancePath || '/'} ${e.message}`) };
}

/**
 * Validates decrypted plaintext body against its message type schema,
 * plus forbidden-field walk and core-spec event validation for "events".
 */
export function validateBody(type, body) {
  if (!body || typeof body !== 'object') {
    return { valid: false, errors: ['body must be an object'] };
  }
  const val = validators[type];
  if (!val) {
    return { valid: false, errors: [`unknown body type: ${type}`] };
  }

  const valid = val(body);
  if (!valid) {
    return { valid: false, errors: val.errors.map(e => `${e.instancePath || '/'} ${e.message}`) };
  }

  // Check forbidden fields
  const forbiddenError = walkForbiddenFields(body);
  if (forbiddenError) {
    return { valid: false, errors: [forbiddenError] };
  }

  // Special checks for "events"
  if (type === 'events') {
    const eventValidator = ajv.getSchema('https://dhyan.local/event.schema.json');
    for (let i = 0; i < body.events.length; i++) {
      const ev = body.events[i];
      if (!eventValidator(ev)) {
        return {
          valid: false,
          errors: eventValidator.errors.map(e => `events[${i}]${e.instancePath || '/'} ${e.message}`)
        };
      }
      const evForbidden = walkForbiddenFields(ev);
      if (evForbidden) {
        return { valid: false, errors: [`events[${i}]: ${evForbidden}`] };
      }
    }
  }

  return { valid: true, errors: [] };
}

/**
 * Validates extension-to-laptop local ingest messages (contract section 9).
 */
export function validateIngest(msg) {
  if (!msg || typeof msg !== 'object') {
    return { valid: false, errors: ['message must be an object'] };
  }
  const kind = msg.kind;
  if (!kind) {
    return { valid: false, errors: ['missing message kind'] };
  }

  const schemaKey = `ingest_${kind}`;
  const val = validators[schemaKey];
  if (!val) {
    return { valid: false, errors: [`unknown ingest kind: ${kind}`] };
  }

  const valid = val(msg);
  if (!valid) {
    return { valid: false, errors: val.errors.map(e => `${e.instancePath || '/'} ${e.message}`) };
  }

  const forbiddenError = walkForbiddenFields(msg);
  if (forbiddenError) {
    return { valid: false, errors: [forbiddenError] };
  }

  return { valid: true, errors: [] };
}

/**
 * Recursively scans object for forbidden privacy identifiers.
 */
export function walkForbiddenFields(obj, currentPath = '') {
  if (obj === null || typeof obj !== 'object') return null;

  if (Array.isArray(obj)) {
    for (let i = 0; i < obj.length; i++) {
      const err = walkForbiddenFields(obj[i], `${currentPath}[${i}]`);
      if (err) return err;
    }
    return null;
  }

  for (const [key, val] of Object.entries(obj)) {
    if (FORBIDDEN_SET.has(key)) {
      return `Forbidden privacy field detected: "${currentPath ? currentPath + '.' : ''}${key}"`;
    }
    const err = walkForbiddenFields(val, currentPath ? `${currentPath}.${key}` : key);
    if (err) return err;
  }

  return null;
}
