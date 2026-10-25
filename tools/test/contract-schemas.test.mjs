import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { validateFrame, validateBody, validateIngest, walkForbiddenFields } from '../lib/contract-validate.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '../..');
const FIXTURES_DIR = path.join(ROOT, 'contracts/sync/fixtures');

test('all valid fixtures validate according to their schema', () => {
  const validFiles = fs.readdirSync(path.join(FIXTURES_DIR, 'valid')).filter(f => f.endsWith('.json'));
  assert.ok(validFiles.length >= 20, `Expected at least 20 valid fixtures, found ${validFiles.length}`);

  for (const file of validFiles) {
    const content = JSON.parse(fs.readFileSync(path.join(FIXTURES_DIR, 'valid', file), 'utf8'));

    if (file.startsWith('ingest_')) {
      const res = validateIngest(content);
      assert.ok(res.valid, `Valid ingest fixture ${file} failed: ${res.errors.join(', ')}`);
    } else if (file.startsWith('pair_') || file === 'hello.json' || file === 'encrypted_frame.json') {
      const res = validateFrame(content);
      assert.ok(res.valid, `Valid frame fixture ${file} failed: ${res.errors.join(', ')}`);
    } else {
      const type = file.replace('.json', '');
      const res = validateBody(type, content);
      assert.ok(res.valid, `Valid body fixture ${file} failed: ${res.errors.join(', ')}`);
    }
  }
});

test('all invalid fixtures are rejected', () => {
  const invalidFiles = fs.readdirSync(path.join(FIXTURES_DIR, 'invalid')).filter(f => f.endsWith('.json'));
  assert.ok(invalidFiles.length >= 5, `Expected at least 5 invalid fixtures, found ${invalidFiles.length}`);

  for (const file of invalidFiles) {
    const content = JSON.parse(fs.readFileSync(path.join(FIXTURES_DIR, 'invalid', file), 'utf8'));
    // Try frame, body, or ingest validation
    let valid = false;
    if (content.kind) {
      valid = validateIngest(content).valid;
    } else if (content.v !== undefined && content.t && (content.t.startsWith('pair_') || content.t === 'hello' || content.c)) {
      valid = validateFrame(content).valid;
    } else {
      // Body or frame
      const fRes = validateFrame(content);
      const bRes = validateBody(content.t || 'events', content);
      valid = fRes.valid || bRes.valid;
    }
    assert.strictEqual(valid, false, `Invalid fixture ${file} unexpectedly validated as true`);
  }
});

test('walkForbiddenFields catches forbidden keys in shallow and nested structures', () => {
  assert.equal(walkForbiddenFields({ harmless: 123 }), null);
  assert.match(walkForbiddenFields({ text: "secret" }), /Forbidden privacy field/);
  assert.match(walkForbiddenFields({ title: "Alice" }), /Forbidden privacy field/);
  assert.match(walkForbiddenFields({ url: "https://example.com" }), /Forbidden privacy field/);
  assert.match(walkForbiddenFields({ nested: [{ rawText: "leak" }] }), /Forbidden privacy field/);
  assert.match(walkForbiddenFields({ payload: { keystrokes: [1, 2] } }), /Forbidden privacy field/);
});

test('body schemas reject extra properties and invalid bounds', () => {
  // Reject extra properties on ping
  const extraPing = { t0: 1729864000000, extra: "not_allowed" };
  const pingRes = validateBody('ping', extraPing);
  assert.strictEqual(pingRes.valid, false);

  // Reject invalid surface in tab_category
  const badTab = {
    kind: "tab_category",
    ts: 1729864000000,
    domainCat: "dev",
    surface: "non_existent_surface",
    tabActive: true
  };
  const tabRes = validateIngest(badTab);
  assert.strictEqual(tabRes.valid, false);

  // Reject editsPerMin out of bounds (0..2000)
  const badBurst = {
    kind: "edit_burst",
    ts: 1729864000000,
    editsPerMin: 2500,
    saves: 1,
    errors: 0,
    warnings: 0,
    windowSec: 30
  };
  const burstRes = validateIngest(badBurst);
  assert.strictEqual(burstRes.valid, false);
});
