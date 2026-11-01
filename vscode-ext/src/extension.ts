import type * as vscodeTypes from 'vscode';
import { probeCapabilities, type ExtensionCapabilities } from './capabilities.ts';
import { PrivacyFilter } from './privacy/exclude.ts';
import { PrivacyGate } from './privacy/gate.ts';
import { RealClock, type Clock } from './clock.ts';
import { WindowAggregator } from './aggregate/window.ts';
import { EditorSensor } from './sensors/editor.ts';
import { WindowSensor } from './sensors/window.ts';
import { DiagnosticsSensor } from './sensors/diagnostics.ts';
import { DebugSensor } from './sensors/debug.ts';

// Re-export core classes for backward-compatibility with tests & external callers
export { PrivacyGate } from './privacy/gate.ts';
export { PrivacyFilter } from './privacy/exclude.ts';
export { probeCapabilities } from './capabilities.ts';
export { RealClock, FakeClock } from './clock.ts';
export { WindowAggregator } from './aggregate/window.ts';
export { classifyEdit, isTrackedScheme } from './aggregate/classify.ts';
export { RhythmTracker } from './aggregate/rhythm.ts';
export { EditorSensor } from './sensors/editor.ts';
export { DhyanVsCodeSensor } from './sensorLegacy.ts';

let privacyFilter: PrivacyFilter | null = null;
let diagOutputChannel: vscodeTypes.OutputChannel | null = null;
let currentCapabilities: ExtensionCapabilities | null = null;
let aggregator: WindowAggregator | null = null;
let tickTimer: ReturnType<typeof setInterval> | null = null;

// Sensors
let editorSensor: EditorSensor | null = null;
let windowSensor: WindowSensor | null = null;
let diagSensor: DiagnosticsSensor | null = null;
let debugSensor: DebugSensor | null = null;

// Bounded in-memory event list (capacity 100)
export const inMemoryEvents: Record<string, unknown>[] = [];
const MAX_IN_MEMORY_EVENTS = 100;

export function activate(
  context: vscodeTypes.ExtensionContext,
  injectedClock?: Clock
): {
  privacyFilter: PrivacyFilter;
  capabilities: ExtensionCapabilities;
  clock: Clock;
  aggregator: WindowAggregator;
  inMemoryEvents: Record<string, unknown>[];
} {
  const clock = injectedClock ?? new RealClock();

  // 1. Initialize Privacy Gate & Exclusions Filter
  privacyFilter = new PrivacyFilter();

  // 2. Probe runtime VS Code engine capabilities
  // @ts-ignore
  const vscodeApi = typeof vscode !== 'undefined' ? vscode : (context as any).vscode;
  currentCapabilities = probeCapabilities(vscodeApi);

  // 3. Initialize Aggregator
  aggregator = new WindowAggregator(clock);

  // 4. Initialize Sensors
  if (vscodeApi) {
    editorSensor = new EditorSensor({
      aggregator,
      privacyFilter,
      vscode: vscodeApi
    });
    windowSensor = new WindowSensor(vscodeApi, aggregator, clock);
    diagSensor = new DiagnosticsSensor(vscodeApi, aggregator, clock);
    debugSensor = new DebugSensor(vscodeApi, aggregator);
  }

  // 5. 10-Second Periodic Tick Timer
  tickTimer = setInterval(() => {
    if (aggregator) {
      const summary = aggregator.tick(clock);
      if (summary) {
        const sanitized = PrivacyGate.sanitize(summary);
        if (sanitized) {
          if (inMemoryEvents.length >= MAX_IN_MEMORY_EVENTS) {
            inMemoryEvents.shift();
          }
          inMemoryEvents.push(sanitized);

          // Verbose diagnostics logging (numeric only)
          const isVerbose = Boolean(
            vscodeApi?.workspace?.getConfiguration?.('dhyan')?.get('diagnostics.verbose', false)
          );
          if (isVerbose && diagOutputChannel) {
            diagOutputChannel.appendLine(
              `[Summary] dur=${sanitized.durMs}ms typingChars=${sanitized.typingChars} saves=${sanitized.saves} err=${sanitized.diagErr}`
            );
          }
        }
      }
    }
  }, 10000);

  // 6. Register Commands
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
          activeChannel.appendLine(`Events in Memory: ${inMemoryEvents.length}`);
          activeChannel.appendLine('Capabilities:');
          activeChannel.appendLine(JSON.stringify(currentCapabilities, null, 2));
          activeChannel.show(true);
        }
      }
    } catch {
      // Fallback silently
    }
  });

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
    registerCmd(cmd, () => {});
  }

  return {
    privacyFilter,
    capabilities: currentCapabilities,
    clock,
    aggregator,
    inMemoryEvents
  };
}

export function deactivate(): void {
  if (tickTimer) {
    clearInterval(tickTimer);
    tickTimer = null;
  }
  if (editorSensor) {
    editorSensor.dispose();
    editorSensor = null;
  }
  if (windowSensor) {
    windowSensor.dispose();
    windowSensor = null;
  }
  if (diagSensor) {
    diagSensor.dispose();
    diagSensor = null;
  }
  if (debugSensor) {
    debugSensor.dispose();
    debugSensor = null;
  }
  if (diagOutputChannel) {
    diagOutputChannel.dispose();
    diagOutputChannel = null;
  }
  privacyFilter = null;
  aggregator = null;
  currentCapabilities = null;
}
