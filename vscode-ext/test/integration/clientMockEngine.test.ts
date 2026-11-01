import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';
import { FakeClock } from '../../src/clock.ts';
import { Outbox } from '../../src/link/outbox.ts';
import { EngineClient } from '../../src/link/client.ts';
import { MockEngineServer } from '../mock-engine/server.ts';
import { DhyanStatusBar } from '../../src/ui/statusBar.ts';

describe('Link Integration: Client <-> Mock Engine', () => {
  let server: MockEngineServer;
  let tempDir: string;
  let clock: FakeClock;
  let outbox: Outbox;
  let client: EngineClient;
  let token: string;
  let statusBar: DhyanStatusBar;

  beforeEach(async () => {
    token = 'mock-test-auth-token-xyz';
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'dhyan-link-test-'));
    clock = new FakeClock(1700000000000);
    outbox = new Outbox(clock, { storageDir: tempDir });

    const mockVscode = {
      window: {
        createStatusBarItem: () => ({
          text: '',
          tooltip: '',
          command: '',
          show: () => {},
          dispose: () => {}
        })
      }
    };
    statusBar = new DhyanStatusBar(mockVscode);

    server = new MockEngineServer({
      expectedToken: token,
      initialAckedUpTo: 0
    });
    const port = await server.start();

    client = new EngineClient(outbox, {
      instanceId: 'inst-1',
      windowId: 'win-1',
      capabilities: {
        windowStateActive: true,
        shellIntegration: true,
        tabGroups: true,
        gitApi: true,
        changeReason: true,
        remoteName: null
      },
      onConnectionChange: (connState) => {
        if (connState === 'authenticated') {
          statusBar.setFocused(25, 0);
        } else {
          statusBar.setWaiting();
        }
      }
    });

    client.setEndpoint({ v: 1, port, token });
  });

  afterEach(async () => {
    client.stop();
    await server.close();
    statusBar.dispose();
    try {
      fs.rmSync(tempDir, { recursive: true, force: true });
    } catch {}
  });

  it('connects, authenticates, sends events batch, and receives ack', async () => {
    client.start();

    // Enqueue 3 test events
    outbox.enqueue({ type: 'ed_summary', typingChars: 15 });
    outbox.enqueue({ type: 'ed_summary', typingChars: 25 });
    outbox.enqueue({ type: 'ed_summary', typingChars: 35 });

    // Wait for connection & authentication
    await new Promise((resolve) => {
      const check = setInterval(() => {
        if (client.getState() === 'authenticated') {
          clearInterval(check);
          resolve(true);
        }
      }, 20);
    });

    assert.equal(client.getState(), 'authenticated');
    assert.equal(statusBar.getMode(), 'focused');

    // Flush batch immediately
    client.flushImmediate();

    // Wait for mock engine to receive events
    await new Promise((resolve) => {
      const check = setInterval(() => {
        if (server.receivedEvents.length >= 3) {
          clearInterval(check);
          resolve(true);
        }
      }, 20);
    });

    assert.equal(server.receivedEvents.length, 3);
    assert.equal(server.receivedEvents[0].typingChars, 15);
    assert.equal(server.receivedEvents[2].typingChars, 35);

    // Wait for ack to be processed on client
    await new Promise(r => setTimeout(r, 100));
    assert.equal(server.ackedUpTo, 3);
  });

  it('reconnects after connection drop and resends unacked events in order without gaps', async () => {
    client.start();

    // Wait for initial authentication
    await new Promise((resolve) => {
      const check = setInterval(() => {
        if (client.getState() === 'authenticated') {
          clearInterval(check);
          resolve(true);
        }
      }, 20);
    });

    // Enqueue 2 events and flush them
    outbox.enqueue({ type: 'ed_summary', seqId: 'e1' });
    outbox.enqueue({ type: 'ed_summary', seqId: 'e2' });
    client.flushImmediate();

    await new Promise((resolve) => {
      const check = setInterval(() => {
        if (server.receivedEvents.length >= 2) {
          clearInterval(check);
          resolve(true);
        }
      }, 20);
    });

    // Now enqueue event 3, but terminate socket before flushing
    outbox.enqueue({ type: 'ed_summary', seqId: 'e3' });

    // Terminate all server sockets to force drop
    for (const ws of server.activeSockets) {
      ws.terminate();
    }

    // Client state changes to disconnected
    await new Promise(r => setTimeout(r, 50));
    assert.equal(client.getState(), 'disconnected');

    // Reconnect client
    client.resetBackoff();
    client.reconnect();

    // Wait for re-authentication and welcome
    await new Promise((resolve) => {
      const check = setInterval(() => {
        if (client.getState() === 'authenticated') {
          clearInterval(check);
          resolve(true);
        }
      }, 20);
    });

    // Flush unacked
    client.flushImmediate();

    await new Promise((resolve) => {
      const check = setInterval(() => {
        if (server.receivedEvents.length >= 3) {
          clearInterval(check);
          resolve(true);
        }
      }, 20);
    });

    const seqIds = server.receivedEvents.map(e => e.seqId);
    assert.ok(seqIds.includes('e3'), 'Expected event e3 to be delivered after reconnection');
  });

  it('stays completely silent when offline and status bar shows waiting state', async () => {
    // Stop primary connected client so it doesn't affect shared status bar
    client.stop();

    const offlineBar = new DhyanStatusBar({
      window: {
        createStatusBarItem: () => ({
          text: '',
          tooltip: '',
          command: '',
          show: () => {},
          dispose: () => {}
        })
      }
    });

    const offlineClient = new EngineClient(outbox, {
      instanceId: 'inst-offline',
      capabilities: {
        windowStateActive: true,
        shellIntegration: true,
        tabGroups: true,
        gitApi: true,
        changeReason: true,
        remoteName: null
      },
      onConnectionChange: (connState) => {
        if (connState !== 'authenticated') {
          offlineBar.setWaiting();
        }
      }
    });

    // Set non-existent port (e.g. 59999)
    offlineClient.setEndpoint({ v: 1, port: 59999, token: 'fake' });
    offlineClient.start();

    await new Promise(r => setTimeout(r, 100));

    // Must be disconnected and status bar in waiting mode
    assert.equal(offlineClient.getState(), 'disconnected');
    assert.equal(offlineBar.getMode(), 'waiting');
    assert.ok(offlineBar.getText().includes('waiting for desktop app'));

    offlineClient.stop();
    offlineBar.dispose();
  });
});
