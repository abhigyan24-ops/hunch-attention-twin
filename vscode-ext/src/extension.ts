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
import { Outbox } from './link/outbox.ts';
import { EndpointDiscovery } from './link/endpoint.ts';
import { EngineClient } from './link/client.ts';
import { DhyanStatusBar } from './ui/statusBar.ts';

// Re-export core classes for backward-compatibility with tests & external callers
export { PrivacyGate } from './privacy/gate.ts';
export { PrivacyFilter } from './privacy/exclude.ts';
export { probeCapabilities } from './capabilities.ts';
export { RealClock, FakeClock } from './clock.ts';
export { WindowAggregator } from './aggregate/window.ts';
export { classifyEdit, isTrackedScheme } from './aggregate/classify.ts';
export { RhythmTracker } from './aggregate/rhythm.ts';
export { EditorSensor } from './sensors/editor.ts';
export { Outbox } from './link/outbox.ts';
export { EndpointDiscovery } from './link/endpoint.ts';
export { EngineClient } from './link/client.ts';
export { DhyanStatusBar } from './ui/statusBar.ts';
export { DhyanVsCodeSensor } from './sensorLegacy.ts';

let privacyFilter: PrivacyFilter | null = null;
let diagOutputChannel: vscodeTypes.OutputChannel | null = null;
let currentCapabilities: ExtensionCapabilities | null = null;
let aggregator: WindowAggregator | null = null;
let tickTimer: ReturnType<typeof setInterval> | null = null;
let leaseTimer: ReturnType<typeof setTimeout> | null = null;

// Sensors
let editorSensor: EditorSensor | null = null;
let windowSensor: WindowSensor | null = null;
let diagSensor: DiagnosticsSensor | null = null;
let debugSensor: DebugSensor | null = null;

// Link & UI
let outbox: Outbox | null = null;
let endpointDiscovery: EndpointDiscovery | null = null;
let engineClient: EngineClient | null = null;
let statusBar: DhyanStatusBar | null = null;

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
  outbox: Outbox;
  engineClient: EngineClient;
  statusBar: DhyanStatusBar | null;
  inMemoryEvents: Record<string, unknown>[];
} {
  const clock = injectedClock ?? new RealClock();

  // 1. Initialize Privacy Gate & Exclusions Filter
  privacyFilter = new PrivacyFilter();

  // 2. Probe runtime VS Code engine capabilities
  // @ts-ignore
  const vscodeApi = typeof vscode !== 'undefined' ? vscode : (context as any).vscode;
  currentCapabilities = probeCapabilities(vscodeApi);

  // 3. Initialize Status Bar
  statusBar = new DhyanStatusBar(vscodeApi);

  // 4. Initialize Outbox with global storage directory
  const storageDir = context?.globalStorageUri?.fsPath || '';
  outbox = new Outbox(clock, { storageDir });

  // 5. Initialize Engine Client & Endpoint Discovery
  engineClient = new EngineClient(outbox, {
    capabilities: currentCapabilities,
    onConnectionChange: (connState) => {
      if (connState === 'authenticated') {
        if (statusBar?.getMode() === 'waiting') {
          statusBar.setFocused(0, 0);
        }
      } else {
        statusBar?.setWaiting();
      }
    },
    onStateMessage: (stateMsg) => {
      if (leaseTimer) {
        clearTimeout(leaseTimer);
        leaseTimer = null;
      }

      if (stateMsg.focus === 'deep') {
        statusBar?.setFocused(Math.round(stateMsg.leaseMs / 60000), 0);

        // Auto-revert when lease expires
        leaseTimer = setTimeout(() => {
          statusBar?.setFocused(0, 0);
        }, stateMsg.leaseMs);
      } else {
        statusBar?.setFocused(0, 0);
      }
    }
  });

  endpointDiscovery = new EndpointDiscovery();
  endpointDiscovery.startWatching((endpoint) => {
    engineClient?.setEndpoint(endpoint);
  });

  // Read initial endpoint if present
  const initialEndpoint = endpointDiscovery.readEndpoint();
  if (initialEndpoint) {
    engineClient.setEndpoint(initialEndpoint);
  }

  // Register URI handler for vscode://dhyan.dhyan-vscode/connect
  const uriSub = endpointDiscovery.registerUriHandler(vscodeApi, context?.secrets);
  if (context?.subscriptions) {
    context.subscriptions.push(uriSub);
  }

  engineClient.start();

  // 6. Initialize Aggregator
  aggregator = new WindowAggregator(clock);

  // 7. Initialize Sensors
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

  // 8. 10-Second Periodic Tick Timer
  tickTimer = setInterval(() => {
    if (aggregator && outbox) {
      const summary = aggregator.tick(clock);
      if (summary) {
        const sanitized = PrivacyGate.sanitize(summary);
        if (sanitized) {
          if (inMemoryEvents.length >= MAX_IN_MEMORY_EVENTS) {
            inMemoryEvents.shift();
          }
          inMemoryEvents.push(sanitized);

          // Enqueue sanitized event into Outbox
          outbox.enqueue(sanitized);

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

  // 9. Register Commands
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
      if (isPaused) {
        statusBar?.setPaused();
      } else {
        statusBar?.setWaiting();
      }
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
          activeChannel.appendLine(`Outbox Events: ${outbox?.size().memoryCount ?? 0}`);
          activeChannel.appendLine(`Connection State: ${engineClient?.getState() ?? 'disconnected'}`);
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
    'dhyan.today',
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
    outbox,
    engineClient,
    statusBar,
    inMemoryEvents
  };
}

export function deactivate(): void {
  if (tickTimer) {
    clearInterval(tickTimer);
    tickTimer = null;
  }
  if (leaseTimer) {
    clearTimeout(leaseTimer);
    leaseTimer = null;
  }
  if (engineClient) {
    engineClient.stop();
    engineClient = null;
  }
  if (endpointDiscovery) {
    endpointDiscovery.dispose();
    endpointDiscovery = null;
  }
  if (statusBar) {
    statusBar.dispose();
    statusBar = null;
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
  outbox = null;
  privacyFilter = null;
  aggregator = null;
  currentCapabilities = null;
}
