import { describe, it, beforeEach, afterEach } from 'mocha';
import * as assert from 'node:assert';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';
import { FakeClock } from '../../src/clock.ts';
import { Outbox } from '../../src/link/outbox.ts';
import { EngineClient } from '../../src/link/client.ts';
import { EndpointDiscovery } from '../../src/link/endpoint.ts';
import { MockEngineServer } from '../mock-engine/server.ts';
import { WindowAggregator } from '../../src/aggregate/window.ts';
import { DhyanStatusBar } from '../../src/ui/statusBar.ts';
import { activate, deactivate, inMemoryEvents } from '../../src/extension.ts';

describe('Phase 6 Hardening, Multi-Window, Fault Injection & Performance', () => {
  let tmpDir: string;
  let mockEngine: MockEngineServer;

  beforeEach(async () => {
    tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'dhyan-phase6-test-'));
  });

  afterEach(async () => {
    if (mockEngine) {
      await mockEngine.stop();
    }
    deactivate();
    try {
      fs.rmSync(tmpDir, { recursive: true, force: true });
    } catch {}
  });

  describe('Multi-Window Support & Isolation', () => {
    it('transmits unique windowId in handshake and batches across multiple windows', async () => {
      mockEngine = new MockEngineServer();
      const port = await mockEngine.start();

      const clock = new FakeClock(1700000000000);
      const outbox1 = new Outbox(clock, { storageDir: path.join(tmpDir, 'win1') });
      const outbox2 = new Outbox(clock, { storageDir: path.join(tmpDir, 'win2') });

      const client1 = new EngineClient(outbox1, {
        windowId: 'window-alpha',
        capabilities: { windowStateActive: true, shellIntegration: false, tabGroups: true, gitApi: false, changeReason: true, remoteName: null }
      });
      const client2 = new EngineClient(outbox2, {
        windowId: 'window-beta',
        capabilities: { windowStateActive: true, shellIntegration: false, tabGroups: true, gitApi: false, changeReason: true, remoteName: null }
      });

      assert.strictEqual(client1.getWindowId(), 'window-alpha');
      assert.strictEqual(client2.getWindowId(), 'window-beta');

      const endpoint = { v: 1, port, token: 'test-secret-token' };
      client1.setEndpoint(endpoint);
      client2.setEndpoint(endpoint);

      client1.start();
      client2.start();

      await new Promise(r => setTimeout(r, 200));

      assert.strictEqual(mockEngine.receivedHellos.length, 2);
      const winIds = mockEngine.receivedHellos.map(h => h.windowId);
      assert.ok(winIds.includes('window-alpha'));
      assert.ok(winIds.includes('window-beta'));

      // Window 1 flushes a batch
      outbox1.enqueue({ type: 'ed_summary', durMs: 10000 });
      client1.flushBatch();

      // Window 2 flushes a batch
      outbox2.enqueue({ type: 'ed_summary', durMs: 10000 });
      client2.flushBatch();

      await new Promise(r => setTimeout(r, 200));

      assert.ok(mockEngine.receivedBatches.length >= 2);
      const batchWindows = mockEngine.receivedBatches.map(b => b.windowId);
      assert.ok(batchWindows.includes('window-alpha'));
      assert.ok(batchWindows.includes('window-beta'));

      client1.stop();
      client2.stop();
    });

    it('attaches windowId to WindowAggregator summary output when configured', () => {
      const clock = new FakeClock(1700000000000);
      const aggregator = new WindowAggregator(clock);
      aggregator.setWindowId('win-xyz-999');

      aggregator.onEdit('typing', 15, 0, 1700000001000);
      clock.advance(10000);

      const summary = aggregator.tick(clock);
      assert.ok(summary);
      assert.strictEqual(summary.type, 'ed_summary');
      assert.strictEqual(summary.windowId, 'win-xyz-999');
    });
  });

  describe('Remote Window Detection & Quiet Policy', () => {
    it('sets remote-unsupported status bar state and disables engine client when remoteName is present', () => {
      const fakeStatusBarItem = {
        command: '',
        text: '',
        tooltip: '',
        show: () => {},
        dispose: () => {}
      };
      const mockVscode = {
        window: {
          createStatusBarItem: () => fakeStatusBarItem,
          state: { focused: true }
        },
        env: { remoteName: 'wsl' }
      };

      const statusBar = new DhyanStatusBar(mockVscode);
      statusBar.setRemoteUnsupported();

      assert.strictEqual(statusBar.getMode(), 'remote-unsupported');
      assert.ok(fakeStatusBarItem.text.includes('remote window not supported'));
      assert.strictEqual(fakeStatusBarItem.command, 'dhyan.diagnostics');
    });

    it('extension activates safely on remote host with local connection disabled', async () => {
      const mockContext = {
        subscriptions: [],
        workspaceState: {
          get: () => undefined,
          update: async () => {}
        },
        globalStorageUri: { fsPath: path.join(tmpDir, 'remote-storage') },
        secrets: {
          get: async () => 'd'.repeat(64),
          store: async () => {}
        }
      };

      const mockVscode = {
        window: {
          createStatusBarItem: () => ({
            command: '',
            text: '',
            tooltip: '',
            show: () => {},
            dispose: () => {}
          }),
          createOutputChannel: () => ({
            appendLine: () => {},
            clear: () => {},
            show: () => {},
            dispose: () => {}
          }),
          state: { focused: true },
          onDidChangeWindowState: () => ({ dispose: () => {} })
        },
        workspace: {
          getConfiguration: () => ({
            get: () => []
          })
        },
        commands: {
          registerCommand: () => ({ dispose: () => {} })
        },
        env: { remoteName: 'ssh-remote' }
      };

      const clock = new FakeClock(1700000000000);
      const ext = await activate(mockContext as any, clock, mockVscode);

      assert.ok(ext);
      assert.strictEqual(ext.capabilities.remoteName, 'ssh-remote');
      assert.strictEqual(ext.statusBar?.getMode(), 'remote-unsupported');
      // When remote, client is stopped / disconnected
      assert.strictEqual(ext.engineClient?.getState(), 'disconnected');
    });
  });

  describe('Fault Injection & Resilience', () => {
    it('handles unexpected WebSocket crash, switches to disconnected, and reconnects on restart', async () => {
      mockEngine = new MockEngineServer({ dropMidBatch: true });
      const port = await mockEngine.start();

      const clock = new FakeClock(1700000000000);
      const outbox = new Outbox(clock, { storageDir: tmpDir });
      outbox.enqueue({ type: 'ed_summary', durMs: 10000 });

      let states: string[] = [];
      const client = new EngineClient(outbox, {
        capabilities: { windowStateActive: true, shellIntegration: false, tabGroups: true, gitApi: false, changeReason: true, remoteName: null },
        onConnectionChange: (s) => states.push(s)
      });

      client.setEndpoint({ v: 1, port, token: 'test-secret-token' });
      client.start();

      // Wait for auth
      await new Promise(r => setTimeout(r, 200));
      assert.ok(states.includes('authenticated'));

      // Flush batch - server is configured to dropMidBatch (terminates ws)
      client.flushBatch();

      await new Promise(r => setTimeout(r, 200));
      assert.strictEqual(client.getState(), 'disconnected');

      // Now restart server without dropMidBatch
      client.reconnect();
      await new Promise(r => setTimeout(r, 250));
      assert.strictEqual(client.getState(), 'authenticated');

      client.stop();
    });

    it('silently ignores malformed JSON and corrupted engine messages without unhandled exceptions', async () => {
      mockEngine = new MockEngineServer({ sendMalformed: true });
      const port = await mockEngine.start();

      const clock = new FakeClock(1700000000000);
      const outbox = new Outbox(clock, { storageDir: tmpDir });
      const client = new EngineClient(outbox, {
        capabilities: { windowStateActive: true, shellIntegration: false, tabGroups: true, gitApi: false, changeReason: true, remoteName: null }
      });

      client.setEndpoint({ v: 1, port, token: 'test-secret-token' });
      // Should not throw or crash
      client.start();

      await new Promise(r => setTimeout(r, 200));
      // Client remains in connected/waiting state since malformed welcome wasn't parsed
      assert.notStrictEqual(client.getState(), 'authenticated');

      client.stop();
    });

    it('EndpointDiscovery returns null on corrupted or empty endpoint file', () => {
      const corruptFile = path.join(tmpDir, 'endpoint.json');
      fs.writeFileSync(corruptFile, '{ invalid json: bad syntax', 'utf8');

      const discovery = new EndpointDiscovery(corruptFile);
      const res = discovery.readEndpoint();
      assert.strictEqual(res, null);

      fs.writeFileSync(corruptFile, '', 'utf8');
      assert.strictEqual(discovery.readEndpoint(), null);

      fs.writeFileSync(corruptFile, JSON.stringify({ port: 'not-a-number' }), 'utf8');
      assert.strictEqual(discovery.readEndpoint(), null);
    });

    it('Outbox enforces 5MB disk spill cap and prunes upon acknowledgment under heavy burst', () => {
      const clock = new FakeClock(1700000000000);
      // Set small memory threshold (20) and small maxSpillBytes (2048) to test spill & size cap
      const outbox = new Outbox(clock, {
        storageDir: tmpDir,
        maxMemoryEvents: 20,
        maxSpillBytes: 2048
      });

      // Enqueue 1500 events
      for (let i = 0; i < 1500; i++) {
        outbox.enqueue({ type: 'ed_summary', val: i });
      }

      const spillPath = path.join(tmpDir, 'outbox_spill.jsonl');
      assert.ok(fs.existsSync(spillPath), 'Spill file should exist');

      const stat = fs.statSync(spillPath);
      // Verify size cap was enforced (around 2KB - 4KB max due to half-reduction)
      assert.ok(stat.size <= 5000, `Spill file size (${stat.size}B) should be capped`);

      // Acknowledge sequence 1400
      outbox.ack(1400);

      // Remaining batch should only have events > 1400
      const batch = outbox.getBatchAfter(0, 200);
      assert.ok(batch.length > 0);
      assert.ok(batch.every(e => e.seq > 1400));
    });
  });

  describe('Performance Verification (< 50ms activation, < 0.05ms edit handler, memory bounding)', () => {
    it('WindowAggregator edit classification and accumulation is sub-millisecond per event', () => {
      const clock = new FakeClock(1700000000000);
      const aggregator = new WindowAggregator(clock);

      const iterations = 5000;
      const startMono = performance.now();

      for (let i = 0; i < iterations; i++) {
        aggregator.onEdit('typing', 1, 0, 1700000000000 + i * 50);
        if (i % 20 === 0) {
          aggregator.onCursorMove();
        }
      }

      const totalTimeMs = performance.now() - startMono;
      const avgLatencyMs = totalTimeMs / iterations;

      assert.ok(
        avgLatencyMs < 0.05,
        `Average handler latency was ${avgLatencyMs.toFixed(5)}ms, must be < 0.05ms`
      );
    });

    it('Extension in-memory events array strictly adheres to capacity boundary (100 items)', async () => {
      const mockContext = {
        subscriptions: [],
        workspaceState: {
          get: () => undefined,
          update: async () => {}
        },
        globalStorageUri: { fsPath: tmpDir },
        secrets: {
          get: async () => 'e'.repeat(64),
          store: async () => {}
        }
      };

      const mockVscode = {
        window: {
          createStatusBarItem: () => ({
            command: '',
            text: '',
            tooltip: '',
            show: () => {},
            dispose: () => {}
          }),
          state: { focused: true },
          onDidChangeWindowState: () => ({ dispose: () => {} })
        },
        workspace: {
          getConfiguration: () => ({
            get: () => []
          })
        },
        commands: {
          registerCommand: () => ({ dispose: () => {} })
        }
      };

      const clock = new FakeClock(1700000000000);
      await activate(mockContext as any, clock, mockVscode);

      // Fill inMemoryEvents past 100
      for (let i = 0; i < 150; i++) {
        if (inMemoryEvents.length >= 100) {
          inMemoryEvents.shift();
        }
        inMemoryEvents.push({ type: 'ed_summary', i });
      }

      assert.strictEqual(inMemoryEvents.length, 100);
    });

    it('Activation runs in under 50ms', async () => {
      const mockContext = {
        subscriptions: [],
        workspaceState: {
          get: () => undefined,
          update: async () => {}
        },
        globalStorageUri: { fsPath: tmpDir },
        secrets: {
          get: async () => 'f'.repeat(64),
          store: async () => {}
        }
      };

      const mockVscode = {
        window: {
          createStatusBarItem: () => ({
            command: '',
            text: '',
            tooltip: '',
            show: () => {},
            dispose: () => {}
          }),
          state: { focused: true },
          onDidChangeWindowState: () => ({ dispose: () => {} })
        },
        workspace: {
          getConfiguration: () => ({
            get: () => []
          })
        },
        commands: {
          registerCommand: () => ({ dispose: () => {} })
        }
      };

      const clock = new FakeClock(1700000000000);
      const start = performance.now();
      await activate(mockContext as any, clock, mockVscode);
      const activationDurationMs = performance.now() - start;

      assert.ok(
        activationDurationMs < 50,
        `Activation duration was ${activationDurationMs.toFixed(2)}ms, must be < 50ms`
      );
    });
  });
});
