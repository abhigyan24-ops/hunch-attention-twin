import test from 'node:test';
import assert from 'node:assert/strict';
import { startMockIngest } from '../mock-ingest/server.mjs';
import { DhyanVsCodeSensor } from '../../vscode-ext/src/extension.js';
import { validateIngest } from '../lib/contract-validate.mjs';
import { scanObjectForPrivacyViolations } from '../privacy-scan.mjs';

test('vscode sensor connects, authenticates, and sends telemetry to mock-ingest', async () => {
  const token = 'test-token-vscode-32-chars-long';
  const ingest = await startMockIngest({ port: 0, token });

  // Mock VS Code API
  const callbacks = {
    docChange: null,
    docSave: null,
    windowState: null,
    diagnostics: null,
    debugStart: null,
    debugEnd: null
  };

  const mockVsCode = {
    workspace: {
      onDidChangeTextDocument: (cb) => { callbacks.docChange = cb; return { dispose: () => {} }; },
      onDidSaveTextDocument: (cb) => { callbacks.docSave = cb; return { dispose: () => {} }; }
    },
    window: {
      onDidChangeWindowState: (cb) => { callbacks.windowState = cb; return { dispose: () => {} }; }
    },
    languages: {
      onDidChangeDiagnostics: (cb) => { callbacks.diagnostics = cb; return { dispose: () => {} }; },
      getDiagnostics: () => [
        ['file1', [{ severity: 0 }]], // 1 error
        ['file2', [{ severity: 1 }]]  // 1 warning
      ]
    },
    debug: {
      onDidStartDebugSession: (cb) => { callbacks.debugStart = cb; return { dispose: () => {} }; },
      onDidTerminateDebugSession: (cb) => { callbacks.debugEnd = cb; return { dispose: () => {} }; }
    }
  };

  const sensor = new DhyanVsCodeSensor({
    port: ingest.port,
    token,
    vscode: mockVsCode,
    windowSec: 10
  });

  sensor.start();

  // Wait for authentication
  await new Promise((resolve) => {
    const check = setInterval(() => {
      if (sensor.authenticated) {
        clearInterval(check);
        resolve();
      }
    }, 20);
  });

  assert.equal(sensor.authenticated, true);
  assert.equal(ingest.stats.authSuccess, 1);

  // 1. Resumption delay test
  // Focus lost, then regained
  callbacks.windowState({ focused: false });
  callbacks.windowState({ focused: true });

  // Simulate edit
  await new Promise(r => setTimeout(r, 50));
  callbacks.docChange({ document: {} }); // no text or URI provided!

  // 2. Edit burst test
  callbacks.docChange({ document: {} });
  callbacks.docChange({ document: {} });
  callbacks.docSave({ document: {} });
  callbacks.diagnostics();

  // Flush burst manually
  sensor.flushBurst();

  // Wait for network flush
  await new Promise(r => setTimeout(r, 100));

  // Verify events received at mock-ingest
  const events = ingest.stats.eventsReceived;
  assert.ok(events.length >= 3, `Expected at least 3 events, got ${events.length}`);

  const resumeEv = events.find(e => e.kind === 'resume_first_edit');
  assert.ok(resumeEv, 'Expected resume_first_edit event');
  assert.ok(resumeEv.delayMs >= 0 && resumeEv.delayMs <= 3600000);

  const burstEv = events.find(e => e.kind === 'edit_burst');
  assert.ok(burstEv, 'Expected edit_burst event');
  assert.equal(burstEv.saves, 1);
  assert.equal(burstEv.errors, 1);
  assert.equal(burstEv.warnings, 1);
  assert.ok(burstEv.editsPerMin > 0);

  const stateEv = events.find(e => e.kind === 'vscode_state');
  assert.ok(stateEv, 'Expected vscode_state event');
  assert.equal(stateEv.windowFocused, true);

  // 3. Strict schema and privacy validation on every received event
  for (const ev of events) {
    const validRes = validateIngest(ev);
    assert.ok(validRes.valid, `Event failed schema validation: ${validRes.errors.join(', ')}`);

    const privacyViolations = scanObjectForPrivacyViolations(ev);
    assert.equal(privacyViolations.length, 0, `Privacy violation detected: ${privacyViolations.join(', ')}`);
  }

  sensor.stop();
  await ingest.close();
});
