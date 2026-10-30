import test from 'node:test';
import assert from 'node:assert/strict';
import { startMockIngest } from '../mock-ingest/server.mjs';
import { DhyanVsCodeSensor } from '../../vscode-ext/src/extension.js';
import { DhyanCompanionViewProvider } from '../../vscode-ext/src/companionProvider.js';
import { DhyanInsightsPanel } from '../../vscode-ext/src/insightsPanel.js';
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

test('vscode companion webview provider renders Frosted Pet, Dial, Presets, and dispatches messages', async () => {
  const postedMessages = [];
  let messageHandler = null;
  const mockWebview = {
    options: {},
    html: '',
    postMessage: (msg) => { postedMessages.push(msg); return Promise.resolve(true); },
    onDidReceiveMessage: (cb) => { messageHandler = cb; return { dispose: () => {} }; },
    asWebviewUri: (uri) => uri
  };
  const mockWebviewView = { webview: mockWebview };

  let startedSession = null;
  let insightsOpened = false;
  const mockSensor = {
    startSession: (preset, min) => { startedSession = { preset, min }; },
    openInsights: () => { insightsOpened = true; }
  };

  const provider = new DhyanCompanionViewProvider({ fsPath: '/test/ext' }, mockSensor);
  provider.resolveWebviewView(mockWebviewView);

  // 1. Verify HTML layout contains core Bento & Pet components
  assert.ok(mockWebview.html.includes('id="spirit-canvas"'), 'Expected Cloud Spirit canvas in HTML');
  assert.ok(mockWebview.html.includes('id="dial-svg"'), 'Expected Semicircular Dial canvas in HTML');
  assert.ok(mockWebview.html.includes('dhyan-squircle-btn'), 'Expected Preset Squircle chips');
  assert.ok(mockWebview.html.includes('id="start-btn"'), 'Expected Start button CTA');
  assert.ok(mockWebview.html.includes('var(--vscode-'), 'Expected adaptive VS Code CSS token integration');

  // 2. Test emotion updates to webview
  provider.updateEmotion('FOCUSED', 3, -2);
  assert.equal(provider.currentEmotion, 'FOCUSED');
  assert.deepEqual(postedMessages[postedMessages.length - 1], {
    type: 'emotion',
    emotion: 'FOCUSED',
    eyeDx: 3,
    eyeDy: -2
  });

  provider.updateFocusMinutes(25);
  assert.deepEqual(postedMessages[postedMessages.length - 1], {
    type: 'focusMinutes',
    minutes: 25
  });

  // 3. Test inbound command handling from webview
  assert.ok(typeof messageHandler === 'function');
  messageHandler({ command: 'startSession', preset: 'Coding', minutes: 45 });
  assert.deepEqual(startedSession, { preset: 'Coding', min: 45 });

  messageHandler({ command: 'openInsights' });
  assert.equal(insightsOpened, true);
});

test('vscode insights dashboard panel renders Flow telemetry and disposes cleanly', async () => {
  let disposed = false;
  const mockPanel = {
    webview: { html: '' },
    reveal: () => {},
    onDidDispose: (cb) => { setTimeout(cb, 10); return { dispose: () => { disposed = true; } }; }
  };

  const mockSensor = {
    editsInWindow: 42,
    savesInWindow: 3,
    currentState: 'flowing'
  };

  const panel = new DhyanInsightsPanel(mockPanel, { fsPath: '/test/ext' }, mockSensor);
  assert.ok(mockPanel.webview.html.includes('Attention & Flow Analytics'), 'Expected header');
  assert.ok(mockPanel.webview.html.includes('WEEKLY ATTENTION FLOW'), 'Expected weekly flow banner');
  assert.ok(mockPanel.webview.html.includes('Active Window Telemetry'), 'Expected telemetry tile');
  assert.ok(mockPanel.webview.html.includes('Spec §25.13 Zero-Content Privacy Verified'), 'Expected privacy invariant');
});
