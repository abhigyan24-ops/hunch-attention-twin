import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs';
import path from 'path';
import os from 'os';
import { verifySyncFiles } from '../verify-sync.mjs';
import { scanObjectForPrivacyViolations, scanFileForPrivacy } from '../privacy-scan.mjs';

test('verify-sync detects clean sync and violations', async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'dhyan-sync-test-'));

  const baseEvent = {
    v: 1,
    id: "a0000000-0000-4000-8000-000000000001",
    type: "screen_on",
    ts: 1729864000000,
    device: "phone",
    source: "android_broadcast",
    synthetic: false,
    seq: 1,
    payload: { state: "on" }
  };

  const phoneFile = path.join(tmpDir, 'phone.jsonl');
  const laptopFile = path.join(tmpDir, 'laptop.jsonl');

  // Case 1: Identical synced events
  fs.writeFileSync(phoneFile, JSON.stringify(baseEvent) + '\n');
  fs.writeFileSync(laptopFile, JSON.stringify(baseEvent) + '\n');

  const res1 = await verifySyncFiles(laptopFile, phoneFile);
  assert.equal(res1.valid, true);
  assert.equal(res1.violations.length, 0);

  // Case 2: Duplicate event ID in same file
  fs.writeFileSync(phoneFile, JSON.stringify(baseEvent) + '\n' + JSON.stringify(baseEvent) + '\n');
  const res2 = await verifySyncFiles(laptopFile, phoneFile);
  assert.equal(res2.valid, false);
  assert.ok(res2.violations.some(v => v.includes('Duplicate event id')));

  // Case 3: Non-monotonic sequence
  const eventSeq2 = { ...baseEvent, id: "a0000000-0000-4000-8000-000000000002", seq: 0 };
  fs.writeFileSync(phoneFile, JSON.stringify(baseEvent) + '\n' + JSON.stringify(eventSeq2) + '\n');
  const res3 = await verifySyncFiles(laptopFile, phoneFile);
  assert.equal(res3.valid, false);
  assert.ok(res3.violations.some(v => v.includes('Non-monotonic seq')));

  // Clean up
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test('privacy-scan detects forbidden fields and canary strings', async () => {
  // Clean object
  const clean = {
    category: "messaging",
    count: 3,
    status: "held",
    appName: "chat"
  };
  assert.equal(scanObjectForPrivacyViolations(clean).length, 0);

  // Forbidden field
  const dirtyField = {
    category: "messaging",
    title: "Alice",
    text: "Hello world"
  };
  const v1 = scanObjectForPrivacyViolations(dirtyField);
  assert.equal(v1.length, 2);

  // Canary pattern in string
  const dirtyCanary = {
    details: "Visit https://phishing.site/login for your account"
  };
  const v2 = scanObjectForPrivacyViolations(dirtyCanary);
  assert.ok(v2.length >= 1);
  assert.ok(v2[0].includes('Canary pattern match'));

  // File scan
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'dhyan-priv-test-'));
  const testFile = path.join(tmpDir, 'test.jsonl');
  fs.writeFileSync(testFile, JSON.stringify(dirtyField) + '\n');

  const fileViolations = await scanFileForPrivacy(testFile);
  assert.ok(fileViolations.length >= 2);

  fs.rmSync(tmpDir, { recursive: true, force: true });
});
