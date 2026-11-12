import assert from 'node:assert/strict';
import { activate, deactivate } from '../../src/extension.ts';

describe('Extension Activation & Wiring Integration', () => {
  let registeredCommands: Map<string, (...args: any[]) => any>;
  let subscriptions: any[];
  let outputLines: string[];
  let mockContext: any;

  beforeEach(() => {
    registeredCommands = new Map();
    subscriptions = [];
    outputLines = [];

    const mockVscode = {
      commands: {
        registerCommand: (id: string, handler: (...args: any[]) => any) => {
          registeredCommands.set(id, handler);
          return { dispose: () => registeredCommands.delete(id) };
        }
      },
      window: {
        state: { focused: true, active: true },
        createOutputChannel: (name: string) => ({
          name,
          clear: () => { outputLines.length = 0; },
          appendLine: (line: string) => { outputLines.push(line); },
          show: () => {},
          dispose: () => {}
        }),
        showInformationMessage: (_msg: string) => {}
      },
      env: {
        remoteName: undefined
      }
    };

    mockContext = {
      subscriptions,
      vscode: mockVscode
    };
  });

  afterEach(() => {
    deactivate();
  });

  it('activates without throwing and returns expected components', () => {
    const result = activate(mockContext);
    assert.ok(result, 'Activation must return a result object');
    assert.ok(result.privacyFilter, 'PrivacyFilter must be initialized');
    assert.ok(result.capabilities, 'Capabilities must be probed');
    assert.ok(result.clock, 'Clock must be initialized');
  });

  it('registers all required commands in subscriptions', () => {
    activate(mockContext);

    const expectedCommands = [
      'dhyan.pause',
      'dhyan.diagnostics',
      'dhyan.startSession',
      'dhyan.endSession',
      'dhyan.park',
      'dhyan.resume',
      'dhyan.openDigest',
      'dhyan.connect',
      'dhyan.forgetToday'
    ];

    for (const cmd of expectedCommands) {
      assert.ok(
        registeredCommands.has(cmd),
        `Expected command ${cmd} to be registered on activation`
      );
    }
  });

  it('toggles tracking pause state on dhyan.pause execution', () => {
    const result = activate(mockContext);
    assert.equal(result.privacyFilter.isPaused(), false);

    const pauseHandler = registeredCommands.get('dhyan.pause');
    assert.ok(pauseHandler);

    pauseHandler();
    assert.equal(result.privacyFilter.isPaused(), true);

    pauseHandler();
    assert.equal(result.privacyFilter.isPaused(), false);
  });

  it('prints JSON capabilities to output channel on dhyan.diagnostics execution', () => {
    activate(mockContext);

    const diagHandler = registeredCommands.get('dhyan.diagnostics');
    assert.ok(diagHandler);

    diagHandler();

    assert.ok(outputLines.length > 0);
    const joined = outputLines.join('\n');
    assert.ok(joined.includes('=== Hunch Extension Diagnostics ==='));
    assert.ok(joined.includes('Tracking Paused: false'));
    assert.ok(joined.includes('windowStateActive'));
  });

  it('creates wind-down status bar item with dhyan.park command', () => {
    let createdItem: any = null;
    mockContext.vscode.window.createStatusBarItem = (align: number, priority: number) => {
      createdItem = {
        align,
        priority,
        command: '',
        text: '',
        tooltip: '',
        shown: false,
        show: () => { createdItem.shown = true; },
        hide: () => { createdItem.shown = false; },
        dispose: () => {}
      };
      return createdItem;
    };

    const res = activate(mockContext);
    assert.ok(res.windDownBar);
    assert.equal(res.windDownBar.command, 'dhyan.park');
    assert.equal(createdItem.priority, 90);
  });
});
