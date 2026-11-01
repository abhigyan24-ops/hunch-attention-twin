import assert from 'node:assert/strict';
import { activate, deactivate } from '../../src/extension.ts';
import { FakeClock } from '../../src/clock.ts';

describe('Editor Sensor & Exclusions Integration', () => {
  let docChangeCb: any;
  let saveCb: any;
  let activeEditorCb: any;
  let mockContext: any;
  let activeDoc: any;
  let fakeClock: FakeClock;

  beforeEach(() => {
    fakeClock = new FakeClock(1700000000000);
    activeDoc = {
      uri: { scheme: 'file', fsPath: '/project/src/index.ts' },
      languageId: 'typescript'
    };

    const mockVscode = {
      commands: {
        registerCommand: () => ({ dispose: () => {} })
      },
      workspace: {
        onDidChangeTextDocument: (cb: any) => { docChangeCb = cb; return { dispose: () => {} }; },
        onDidSaveTextDocument: (cb: any) => { saveCb = cb; return { dispose: () => {} }; },
        getConfiguration: () => ({
          get: (key: string, def: any) => def
        })
      },
      window: {
        state: { focused: true, active: true },
        activeTextEditor: { document: activeDoc },
        onDidChangeWindowState: () => ({ dispose: () => {} }),
        onDidChangeTextEditorSelection: () => ({ dispose: () => {} }),
        onDidChangeTextEditorVisibleRanges: () => ({ dispose: () => {} }),
        onDidChangeActiveTextEditor: (cb: any) => { activeEditorCb = cb; return { dispose: () => {} }; },
        createOutputChannel: () => ({
          clear: () => {},
          appendLine: () => {},
          show: () => {},
          dispose: () => {}
        }),
        showInformationMessage: () => {}
      },
      languages: {
        onDidChangeDiagnostics: () => ({ dispose: () => {} }),
        getDiagnostics: () => []
      },
      debug: {
        onDidStartDebugSession: () => ({ dispose: () => {} }),
        onDidTerminateDebugSession: () => ({ dispose: () => {} }),
        onDidChangeActiveDebugSession: () => ({ dispose: () => {} })
      }
    };

    mockContext = {
      subscriptions: [],
      vscode: mockVscode
    };
  });

  afterEach(() => {
    deactivate();
  });

  it('collects edits, saves, and file switches into an ed_summary', () => {
    const { aggregator, clock } = activate(mockContext, fakeClock);

    // 1. Simulate 3 typing edits
    docChangeCb({
      document: activeDoc,
      contentChanges: [
        { text: 'let x = 1;', rangeLength: 0 }
      ]
    });

    docChangeCb({
      document: activeDoc,
      contentChanges: [
        { text: '2', rangeLength: 1 }
      ]
    });

    // 2. Simulate Save
    saveCb(activeDoc);

    // 3. Simulate File Switch to a new document
    const secondDoc = {
      uri: { scheme: 'file', fsPath: '/project/src/utils.ts' },
      languageId: 'typescript'
    };
    mockContext.vscode.window.activeTextEditor = { document: secondDoc };
    activeEditorCb({ document: secondDoc });

    // Tick aggregator
    clock.advance(10000);
    const summary = aggregator.tick(clock);

    assert.ok(summary, 'Expected summary event');
    assert.equal(summary.type, 'ed_summary');
    assert.equal(summary.typingChars, 11); // 'let x = 1;' (10) + '2' (1)
    assert.equal(summary.deletions, 1);
    assert.equal(summary.saves, 1);
    assert.equal(summary.fileSwitches, 1);
  });

  it('produces no counts when editing an excluded file (.env)', () => {
    const { aggregator, privacyFilter, clock } = activate(mockContext, fakeClock);

    // Configure exclusion for .env globs
    privacyFilter.updateConfig({
      globs: ['**/.env*', '*.secret']
    });

    const envDoc = {
      uri: { scheme: 'file', fsPath: '/project/.env' },
      languageId: 'properties'
    };
    mockContext.vscode.window.activeTextEditor = { document: envDoc };

    // Simulate typing in .env
    docChangeCb({
      document: envDoc,
      contentChanges: [
        { text: 'DATABASE_PASSWORD=secret', rangeLength: 0 }
      ]
    });
    saveCb(envDoc);

    clock.advance(10000);
    const summary = aggregator.tick(clock);

    // Edits and saves must NOT be counted!
    assert.ok(summary);
    assert.equal(summary.typingChars, 0, 'No typing chars should be recorded for excluded files');
    assert.equal(summary.deletions, 0, 'No deletions should be recorded for excluded files');
    assert.equal(summary.saves, 0, 'No saves should be recorded for excluded files');
  });
});
