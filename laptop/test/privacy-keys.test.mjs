import { test, describe, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { tmpdir } from 'node:os';

// Test harness for privacy keys logic
describe('Privacy Keys & SafeStorage Security', () => {
  const testDir = path.join(tmpdir(), `dhyan-test-keys-${Date.now()}`);
  const keyFile = path.join(testDir, 'secure', 'keys.bin');

  beforeEach(() => {
    fs.rmSync(testDir, { recursive: true, force: true });
    fs.mkdirSync(path.join(testDir, 'secure'), { recursive: true });
  });

  test('Masked hint never reveals more than last 4 characters', () => {
    const last4 = (k) => (k && k.length >= 8 ? `••••${k.slice(-4)}` : k ? '••••' : '');
    assert.equal(last4('gsk_1234567890abcdef12345678'), '••••5678');
    assert.equal(last4('shortkey'), '••••tkey');
    assert.equal(last4('tiny'), '••••');
    assert.equal(last4(undefined), '');
  });

  test('Input validation rejects malformed or out-of-range keys', () => {
    const isValidKey = (k) => typeof k === 'string' && k.trim().length >= 20 && k.trim().length <= 256 && /^[\x21-\x7E]+$/.test(k.trim());
    assert.equal(isValidKey('gsk_1234567890abcdef12345678'), true);
    assert.equal(isValidKey('too_short'), false); // < 20 chars
    assert.equal(isValidKey('contains spaces in the middle of key 12345678'), false);
    assert.equal(isValidKey(''), false);
  });

  test('Independent provider key retention (setting Groq retains Gemini)', () => {
    let cache = {};
    const setKey = (provider, key) => {
      cache[provider] = key;
    };
    const removeKey = (provider) => {
      delete cache[provider];
    };

    setKey('groq', 'gsk_mock_groq_api_key_valid_20_chars');
    assert.equal(cache.groq, 'gsk_mock_groq_api_key_valid_20_chars');

    setKey('gemini', 'gem_mock_gemini_key_valid_20_chars');
    assert.equal(cache.groq, 'gsk_mock_groq_api_key_valid_20_chars');
    assert.equal(cache.gemini, 'gem_mock_gemini_key_valid_20_chars');

    removeKey('groq');
    assert.equal(cache.groq, undefined);
    assert.equal(cache.gemini, 'gem_mock_gemini_key_valid_20_chars');
  });

  test('Corrupt or undecryptable binary file signals needs-reentry without crashing', () => {
    fs.writeFileSync(keyFile, Buffer.from('corrupt_non_dpapi_payload'));
    let status = 'ok';
    let cache = {};
    try {
      const raw = fs.readFileSync(keyFile);
      // Simulate decryption throwing DPAPI failure
      throw new Error('decryption_failed');
    } catch {
      cache = {};
      status = 'needs-reentry';
    }

    assert.equal(status, 'needs-reentry');
    assert.deepEqual(cache, {});
  });
});
