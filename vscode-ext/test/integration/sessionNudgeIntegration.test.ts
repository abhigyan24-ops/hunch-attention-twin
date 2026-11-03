/**
 * sessionNudgeIntegration.test.ts — Integration tests for Phase 5 Sessions, Nudges, Views, and Leases.
 */

import assert from 'node:assert/strict';
import { describe, it, beforeEach, afterEach } from 'mocha';
import { FakeClock } from '../../src/clock.ts';
import { activate, deactivate } from '../../src/extension.ts';

describe('Phase 5 Integration: Sessions, Nudges, Digest, and Views', () => {
  let clock: FakeClock;
  let workspaceStateMap: Map<string, any>;
  let mockWorkspaceState: any;
  let registeredCommands: Record<string, (...args: any[]) => any>;
  let mockVscode: any;

  beforeEach(() => {
    clock = new FakeClock(1_700_000_000_000);
    workspaceStateMap = new Map();
    mockWorkspaceState = {
      get: (k: string) => workspaceStateMap.get(k),
      update: (k: string, v: any) => {
        if (v === undefined) {
          workspaceStateMap.delete(k);
        } else {
          workspaceStateMap.set(k, v);
        }
        return Promise.resolve();
      }
    };
    registeredCommands = {};

    mockVscode = {
      window: {
        activeTextEditor: {
          document: {
            uri: { fsPath: 'C:\\project\\src\\app.ts', scheme: 'file' }
          },
          selection: {
            active: { line: 10, character: 5 }
          }
        },
        createStatusBarItem: () => ({
          text: '',
          tooltip: '',
          command: '',
          show: () => {},
          dispose: () => {}
        }),
        showInputBox: async () => 'Refactor state machine',
        showQuickPick: async () => ({ value: 5 }),
        showInformationMessage: async () => 'Dismiss',
        setStatusBarMessage: () => ({ dispose: () => {} }),
        registerTreeDataProvider: () => ({ dispose: () => {} }),
        createOutputChannel: () => ({
          clear: () => {},
          appendLine: () => {},
          show: () => {},
          dispose: () => {}
        })
      },
      workspace: {
        getConfiguration: () => ({
          get: (key: string, def: any) => def
        }),
        openTextDocument: async () => ({})
      },
      commands: {
        registerCommand: (id: string, fn: any) => {
          registeredCommands[id] = fn;
          return { dispose: () => {} };
        }
      },
      Position: class {
        line: number;
        character: number;
        constructor(line: number, character: number) {
          this.line = line;
          this.character = character;
        }
      },
      Selection: class {
        anchor: any;
        active: any;
        constructor(anchor: any, active: any) {
          this.anchor = anchor;
          this.active = active;
        }
      },
      Range: class {
        start: any;
        end: any;
        constructor(start: any, end: any) {
          this.start = start;
          this.end = end;
        }
      }
    };
  });

  afterEach(() => {
    deactivate();
  });

  it('activates and exposes all Phase 5 managers and tree providers', () => {
    const contextMock: any = {
      subscriptions: [],
      workspaceState: mockWorkspaceState,
      globalStorageUri: { fsPath: 'C:\\storage' },
      secrets: {
        get: async () => '11112222333344445555666677778888',
        store: async () => {}
      },
      vscode: mockVscode
    };

    const ext = activate(contextMock, clock);

    assert.ok(ext.sessionManager !== null);
    assert.ok(ext.nudgeManager !== null);
    assert.ok(ext.digestManager !== null);
    assert.ok(ext.todayTreeProvider !== null);
    assert.ok(ext.digestTreeProvider !== null);

    // Verify commands
    assert.ok(registeredCommands['dhyan.startSession'] !== undefined);
    assert.ok(registeredCommands['dhyan.endSession'] !== undefined);
    assert.ok(registeredCommands['dhyan.toggleTimer'] !== undefined);
    assert.ok(registeredCommands['dhyan.openDigest'] !== undefined);
    assert.ok(registeredCommands['dhyan.forgetToday'] !== undefined);
    assert.ok(registeredCommands['dhyan.today'] !== undefined);
  });

  it('runs startSession and endSession workflow via commands and emits ed_session', async () => {
    const contextMock: any = {
      subscriptions: [],
      workspaceState: mockWorkspaceState,
      globalStorageUri: { fsPath: 'C:\\storage' },
      secrets: {
        get: async () => '11112222333344445555666677778888',
        store: async () => {}
      },
      vscode: mockVscode
    };

    const ext = activate(contextMock, clock);

    // 1. Start Session
    await registeredCommands['dhyan.startSession']();
    assert.equal(ext.sessionManager?.isSessionActive(), true);

    // 2. Advance time 20 min
    clock.advance(20 * 60_000);

    // 3. End Session with rating
    await registeredCommands['dhyan.endSession']();
    assert.equal(ext.sessionManager?.isSessionActive(), false);

    // Verify TodayTreeDataProvider shows history
    const treeItems = await ext.todayTreeProvider!.getChildren();
    assert.ok(treeItems.length >= 2);
    assert.ok(treeItems[1].label.includes("Today's Focus: 20m"));

    // Verify Outbox events
    const batch = ext.outbox.getBatchAfter(0, 10);
    const sessionEvents = batch.filter(e => e.type === 'ed_session');
    assert.equal(sessionEvents.length, 2);
    assert.equal(sessionEvents[0].action, 'start');
    assert.equal(sessionEvents[1].action, 'end');
    assert.equal(sessionEvents[1].durationMs, 20 * 60_000);
  });

  it('dhyan.forgetToday resets local session history and clears events', async () => {
    const contextMock: any = {
      subscriptions: [],
      workspaceState: mockWorkspaceState,
      globalStorageUri: { fsPath: 'C:\\storage' },
      secrets: {
        get: async () => '11112222333344445555666677778888',
        store: async () => {}
      },
      vscode: mockVscode
    };

    const ext = activate(contextMock, clock);

    // Start & end session
    await registeredCommands['dhyan.startSession']();
    clock.advance(10_000);
    await registeredCommands['dhyan.endSession']();
    assert.equal(ext.sessionManager?.getSessionHistory().length, 1);

    // Forget today
    await registeredCommands['dhyan.forgetToday']();
    assert.equal(ext.sessionManager?.getSessionHistory().length, 0);
    assert.equal(ext.inMemoryEvents.length, 0);

    const items = await ext.todayTreeProvider!.getChildren();
    assert.ok(items[1].label.includes("Today's Focus: 0m"));
  });

  it('handles state leases with automatic timeout rollback', async () => {
    const contextMock: any = {
      subscriptions: [],
      workspaceState: mockWorkspaceState,
      globalStorageUri: { fsPath: 'C:\\storage' },
      secrets: {
        get: async () => '11112222333344445555666677778888',
        store: async () => {}
      },
      vscode: mockVscode
    };

    const ext = activate(contextMock, clock);

    // Simulate state message from engine
    const stateMsg: any = {
      kind: 'state',
      focus: 'deep',
      leaseMs: 50,
      version: 1
    };

    ext.engineClient['options'].onStateMessage?.(stateMsg);
    assert.equal(ext.statusBar?.getMode(), 'focused');

    // Wait for lease to expire
    await new Promise(r => setTimeout(r, 70));
    assert.equal(ext.statusBar?.getMode(), 'focused'); // reverted to 0,0 focus
  });
});
