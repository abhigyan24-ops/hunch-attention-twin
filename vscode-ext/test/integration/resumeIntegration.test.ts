/**
 * resumeIntegration.test.ts — Integration tests for Phase 4 Resume Assist & Snapshotting.
 */

import assert from 'node:assert/strict';
import * as path from 'node:path';
import { describe, it, beforeEach, afterEach } from 'mocha';
import { FakeClock } from '../../src/clock.ts';
import { PrivacyFilter } from '../../src/privacy/exclude.ts';
import { ResumeTracker } from '../../src/aggregate/resumeTracker.ts';
import { ResumeManager, SNAPSHOT_STATE_KEY } from '../../src/features/resume.ts';
import { ResumeTreeDataProvider } from '../../src/ui/trees.ts';
import { activate, deactivate } from '../../src/extension.ts';

describe('Resume Integration: Snapshots, Assist, and TreeView', () => {
  let clock: FakeClock;
  let workspaceStateMap: Map<string, any>;
  let mockWorkspaceState: any;
  let privacyFilter: PrivacyFilter;
  let resumeTracker: ResumeTracker;
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
    privacyFilter = new PrivacyFilter();
    resumeTracker = new ResumeTracker(clock, () => 0.5);

    mockVscode = {
      window: {
        activeTextEditor: {
          document: {
            uri: { fsPath: 'C:\\project\\src\\service.ts', scheme: 'file' }
          },
          selection: {
            active: { line: 42, character: 10 }
          }
        },
        createStatusBarItem: () => ({
          text: '',
          tooltip: '',
          command: '',
          show: () => {},
          dispose: () => {}
        }),
        showTextDocument: async () => ({
          selection: null,
          revealRange: () => {}
        }),
        showInformationMessage: () => {},
        setStatusBarMessage: () => ({ dispose: () => {} })
      },
      workspace: {
        openTextDocument: async () => ({})
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

  it('captures editor snapshot into workspaceState and never includes document text', () => {
    const resumeManager = new ResumeManager({
      vscodeApi: mockVscode,
      workspaceState: mockWorkspaceState,
      privacyFilter,
      resumeTracker,
      statusBar: null
    });

    const snapshot = resumeManager.captureSnapshot('task-abc-123', 'My parked intention');
    assert.ok(snapshot !== null);
    assert.equal(path.basename(snapshot!.fileName.replace(/\\/g, '/')), 'service.ts');
    assert.equal(snapshot!.line, 42);
    assert.equal(snapshot!.character, 10);
    assert.equal(snapshot!.parkedNote, 'My parked intention');
    assert.equal(snapshot!.taskKey, 'task-abc-123');

    // Confirm it persisted in workspaceState
    const stored = mockWorkspaceState.get(SNAPSHOT_STATE_KEY);
    assert.ok(stored !== undefined);
    assert.equal(stored.fileName, 'service.ts');
    assert.equal(stored.line, 42);

    resumeManager.dispose();
  });

  it('refuses to snapshot excluded files (.env / secrets) per privacy rule P5', () => {
    privacyFilter.updateConfig({
      globs: ['**/.env*', '*.secret']
    });

    // Active editor is .env
    mockVscode.window.activeTextEditor.document.uri.fsPath = 'C:\\project\\.env';

    const resumeManager = new ResumeManager({
      vscodeApi: mockVscode,
      workspaceState: mockWorkspaceState,
      privacyFilter,
      resumeTracker,
      statusBar: null
    });

    const snapshot = resumeManager.captureSnapshot('task-secret');
    assert.equal(snapshot, null);
    assert.equal(mockWorkspaceState.get(SNAPSHOT_STATE_KEY), undefined);

    resumeManager.dispose();
  });

  it('executes resume by reopening document and positioning cursor', async () => {
    let openedPath = '';
    mockVscode.workspace.openTextDocument = async (p: string) => {
      openedPath = p;
      return { uri: { fsPath: p } };
    };

    let revealed = false;
    mockVscode.window.showTextDocument = async () => ({
      selection: null,
      revealRange: () => {
        revealed = true;
      }
    });

    const resumeManager = new ResumeManager({
      vscodeApi: mockVscode,
      workspaceState: mockWorkspaceState,
      privacyFilter,
      resumeTracker,
      statusBar: null
    });

    resumeManager.captureSnapshot('task-1');
    const success = await resumeManager.executeResume();

    assert.equal(success, true);
    assert.equal(openedPath, 'C:\\project\\src\\service.ts');
    assert.equal(revealed, true);

    resumeManager.dispose();
  });

  it('ResumeTreeDataProvider provides sidebar items for the snapshot', async () => {
    const resumeManager = new ResumeManager({
      vscodeApi: mockVscode,
      workspaceState: mockWorkspaceState,
      privacyFilter,
      resumeTracker,
      statusBar: null
    });

    const treeProvider = new ResumeTreeDataProvider(mockVscode, resumeManager);

    // Initial state: empty
    let items = await treeProvider.getChildren();
    assert.equal(items.length, 1);
    assert.equal(items[0].label, 'No interrupted task recorded');

    // Snapshot taken with parked note
    resumeManager.captureSnapshot('task-1', 'Refactor auth token');
    items = await treeProvider.getChildren();

    assert.equal(items.length, 3);
    const labelParts = items[0].label.split(':');
    const fileName = labelParts.slice(0, -1).join(':');
    const lineNum = labelParts[labelParts.length - 1];
    assert.equal(path.basename(fileName.replace(/\\/g, '/')), 'service.ts');
    assert.equal(lineNum, '43');
    assert.equal(items[0].command?.command, 'dhyan.resume');
    assert.equal(items[1].label, '"Refactor auth token"');
    assert.ok(items[2].label.startsWith('Saved at'));

    resumeManager.dispose();
  });

  it('activates extension and binds dhyan.resume and dhyan.park commands', async () => {
    const registeredCommands: Record<string, (...args: any[]) => any> = {};
    const contextMock: any = {
      subscriptions: [],
      workspaceState: mockWorkspaceState,
      globalStorageUri: { fsPath: 'C:\\storage' },
      secrets: {
        get: async () => '11112222333344445555666677778888',
        store: async () => {}
      },
      vscode: {
        ...mockVscode,
        commands: {
          registerCommand: (id: string, fn: any) => {
            registeredCommands[id] = fn;
            return { dispose: () => {} };
          }
        },
        window: {
          ...mockVscode.window,
          registerTreeDataProvider: () => ({ dispose: () => {} })
        }
      }
    };

    const ext = activate(contextMock, clock);
    assert.ok(ext.resumeTracker !== null);
    assert.ok(ext.resumeManager !== null);
    assert.ok(registeredCommands['dhyan.resume'] !== undefined);
    assert.ok(registeredCommands['dhyan.park'] !== undefined);
  });
});
