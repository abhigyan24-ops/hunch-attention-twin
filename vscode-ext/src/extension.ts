import type * as vscodeTypes from 'vscode';
import { probeCapabilities, type ExtensionCapabilities } from './capabilities.ts';
import { PrivacyFilter } from './privacy/exclude.ts';
import { RealClock } from './clock.ts';

// Re-export core classes for backward-compatibility with tests & external callers
export { PrivacyGate } from './privacy/gate.ts';
export { PrivacyFilter } from './privacy/exclude.ts';
export { probeCapabilities } from './capabilities.ts';
export { RealClock, FakeClock } from './clock.ts';
export { DhyanVsCodeSensor } from './sensorLegacy.ts';

let privacyFilter: PrivacyFilter | null = null;
let diagOutputChannel: vscodeTypes.OutputChannel | null = null;
let currentCapabilities: ExtensionCapabilities | null = null;

export function activate(context: vscodeTypes.ExtensionContext): {
  privacyFilter: PrivacyFilter;
  capabilities: ExtensionCapabilities;
  clock: RealClock;
} {
  const clock = new RealClock();

  // 1. Initialize Privacy Gate & Exclusions Filter
  privacyFilter = new PrivacyFilter();

  // 2. Probe runtime VS Code engine capabilities
  // @ts-ignore
  const vscodeApi = typeof vscode !== 'undefined' ? vscode : (context as any).vscode;
  currentCapabilities = probeCapabilities(vscodeApi);

  // 3. Register 'dhyan.pause' command
  // @ts-ignore
  const registerCmd = (cmdId: string, handler: (...args: any[]) => any) => {
    try {
      if (vscodeApi?.commands?.registerCommand) {
        context.subscriptions.push(vscodeApi.commands.registerCommand(cmdId, handler));
      }
    } catch {
      // Ignore registration conflicts
    }
  };

  registerCmd('dhyan.pause', () => {
    if (privacyFilter) {
      const isPaused = privacyFilter.togglePause();
      if (vscodeApi?.window?.showInformationMessage) {
        vscodeApi.window.showInformationMessage(
          `Dhyan attention tracking is now ${isPaused ? 'PAUSED' : 'RESUMED'}.`
        );
      }
    }
  });

  // 4. Register 'dhyan.diagnostics' command
  registerCmd('dhyan.diagnostics', () => {
    try {
      if (vscodeApi?.window?.createOutputChannel) {
        if (!diagOutputChannel) {
          const ch = vscodeApi.window.createOutputChannel('Dhyan Diagnostics');
          diagOutputChannel = ch;
          context.subscriptions.push(ch);
        }
        const activeChannel = diagOutputChannel;
        if (activeChannel) {
          activeChannel.clear();
          activeChannel.appendLine('=== Dhyan Extension Diagnostics ===');
          activeChannel.appendLine(`Timestamp: ${new Date(clock.now()).toISOString()}`);
          activeChannel.appendLine(`Tracking Paused: ${privacyFilter?.isPaused() ?? false}`);
          activeChannel.appendLine('Capabilities:');
          activeChannel.appendLine(JSON.stringify(currentCapabilities, null, 2));
          activeChannel.show(true);
        }
      }
    } catch {
      // Fallback silently if channel cannot be created
    }
  });

  // 5. Register placeholder command handlers for commands declared in package.json
  const standardCommands = [
    'dhyan.startSession',
    'dhyan.endSession',
    'dhyan.park',
    'dhyan.resume',
    'dhyan.openDigest',
    'dhyan.connect',
    'dhyan.forgetToday',
    'dhyan.openInsights',
    'dhyan.toggleTimer',
    'dhyan.reconnect',
    'dhyan.showStatus'
  ];

  for (const cmd of standardCommands) {
    registerCmd(cmd, () => {
      // Feature implementations wired in subsequent phases (1-5)
    });
  }

  return {
    privacyFilter,
    capabilities: currentCapabilities,
    clock
  };
}

export function deactivate(): void {
  if (diagOutputChannel) {
    diagOutputChannel.dispose();
    diagOutputChannel = null;
  }
  privacyFilter = null;
  currentCapabilities = null;
}
