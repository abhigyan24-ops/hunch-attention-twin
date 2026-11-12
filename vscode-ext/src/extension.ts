import type * as vscodeTypes from 'vscode';
import { HUNCH_BRAND } from './brand.js';
import { randomBytes } from 'node:crypto';
import { probeCapabilities, type ExtensionCapabilities } from './capabilities.ts';
import { PrivacyFilter } from './privacy/exclude.ts';
import { PrivacyGate } from './privacy/gate.ts';
import { RealClock, type Clock } from './clock.ts';
import { WindowAggregator } from './aggregate/window.ts';
import { TaskContextTracker } from './aggregate/taskContext.ts';
import { BreakDetector } from './aggregate/breaks.ts';
import { EditorSensor } from './sensors/editor.ts';
import { WindowSensor } from './sensors/window.ts';
import { DiagnosticsSensor } from './sensors/diagnostics.ts';
import { DebugSensor } from './sensors/debug.ts';
import { GitSensor } from './sensors/git.ts';
import { TasksSensor } from './sensors/tasks.ts';
import { TerminalSensor } from './sensors/terminal.ts';
import { Outbox } from './link/outbox.ts';
import { EndpointDiscovery } from './link/endpoint.ts';
import { EngineClient } from './link/client.ts';
import { DhyanStatusBar } from './ui/statusBar.ts';
import { getOrCreateSalt } from './privacy/hash.ts';
import { ResumeTracker, type ResumeLoadContext } from './aggregate/resumeTracker.ts';
import { ResumeManager } from './features/resume.ts';
import { SessionManager } from './features/session.ts';
import { NudgeManager } from './features/nudge.ts';
import { DigestManager } from './features/digest.ts';
import { ResumeTreeDataProvider, TodayTreeDataProvider, DigestTreeDataProvider } from './ui/trees.ts';

// Re-export core classes for backward-compatibility with tests & external callers
export { PrivacyGate } from './privacy/gate.ts';
export { PrivacyFilter } from './privacy/exclude.ts';
export { probeCapabilities } from './capabilities.ts';
export { RealClock, FakeClock } from './clock.ts';
export { WindowAggregator } from './aggregate/window.ts';
export { TaskContextTracker } from './aggregate/taskContext.ts';
export { BreakDetector } from './aggregate/breaks.ts';
export { ResumeTracker } from './aggregate/resumeTracker.ts';
export { ResumeManager } from './features/resume.ts';
export { SessionManager } from './features/session.ts';
export { NudgeManager } from './features/nudge.ts';
export { DigestManager } from './features/digest.ts';
export { ResumeTreeDataProvider, TodayTreeDataProvider, DigestTreeDataProvider } from './ui/trees.ts';
export { classifyEdit, isTrackedScheme } from './aggregate/classify.ts';
export { RhythmTracker } from './aggregate/rhythm.ts';
export { EditorSensor } from './sensors/editor.ts';
export { GitSensor } from './sensors/git.ts';
export { TasksSensor } from './sensors/tasks.ts';
export { TerminalSensor } from './sensors/terminal.ts';
export { Outbox } from './link/outbox.ts';
export { EndpointDiscovery } from './link/endpoint.ts';
export { EngineClient } from './link/client.ts';
export { DhyanStatusBar } from './ui/statusBar.ts';
export { DhyanVsCodeSensor } from './sensorLegacy.ts';

let privacyFilter: PrivacyFilter | null = null;
let diagOutputChannel: vscodeTypes.OutputChannel | null = null;
let currentCapabilities: ExtensionCapabilities | null = null;
let aggregator: WindowAggregator | null = null;
let taskContextTracker: TaskContextTracker | null = null;
let breakDetector: BreakDetector | null = null;
let resumeTracker: ResumeTracker | null = null;
let resumeManager: ResumeManager | null = null;
let sessionManager: SessionManager | null = null;
let nudgeManager: NudgeManager | null = null;
let digestManager: DigestManager | null = null;
let resumeTreeProvider: ResumeTreeDataProvider | null = null;
let todayTreeProvider: TodayTreeDataProvider | null = null;
let digestTreeProvider: DigestTreeDataProvider | null = null;
let tickTimer: ReturnType<typeof setInterval> | null = null;
let breakTickTimer: ReturnType<typeof setInterval> | null = null;
let leaseTimer: ReturnType<typeof setTimeout> | null = null;
let focusDebounceTimer: ReturnType<typeof setTimeout> | null = null;
let currentWindowId: string = '';

// Sensors
let editorSensor: EditorSensor | null = null;
let windowSensor: WindowSensor | null = null;
let diagSensor: DiagnosticsSensor | null = null;
let debugSensor: DebugSensor | null = null;
let gitSensor: GitSensor | null = null;
let tasksSensor: TasksSensor | null = null;
let terminalSensor: TerminalSensor | null = null;

// Link & UI
let outbox: Outbox | null = null;
let endpointDiscovery: EndpointDiscovery | null = null;
let engineClient: EngineClient | null = null;
let statusBar: DhyanStatusBar | null = null;
let windDownBar: any = null;
let windDownInterval: ReturnType<typeof setInterval> | null = null;

// Bounded in-memory event list (capacity 100)
export const inMemoryEvents: Record<string, unknown>[] = [];
const MAX_IN_MEMORY_EVENTS = 100;

export function activate(
  context: vscodeTypes.ExtensionContext,
  injectedClock?: Clock,
  customVscode?: any
): {
  privacyFilter: PrivacyFilter;
  capabilities: ExtensionCapabilities;
  clock: Clock;
  aggregator: WindowAggregator;
  outbox: Outbox;
  engineClient: EngineClient;
  statusBar: DhyanStatusBar | null;
  inMemoryEvents: Record<string, unknown>[];
  taskContextTracker?: TaskContextTracker | null;
  breakDetector?: BreakDetector | null;
  resumeTracker?: ResumeTracker | null;
  resumeManager?: ResumeManager | null;
  sessionManager?: SessionManager | null;
  nudgeManager?: NudgeManager | null;
  digestManager?: DigestManager | null;
  resumeTreeProvider?: ResumeTreeDataProvider | null;
  todayTreeProvider?: TodayTreeDataProvider | null;
  digestTreeProvider?: DigestTreeDataProvider | null;
  windDownBar?: any;
} {
  const clock = injectedClock ?? new RealClock();

  // 1. Initialize Privacy Gate & Exclusions Filter
  privacyFilter = new PrivacyFilter();

  // 2. Probe runtime VS Code engine capabilities
  // @ts-ignore
  const vscodeApi = customVscode ?? (typeof vscode !== 'undefined' ? vscode : (context as any).vscode);
  currentCapabilities = probeCapabilities(vscodeApi);

  // 3. Initialize Status Bar
  statusBar = new DhyanStatusBar(vscodeApi);

  // 4. Initialize Outbox with global storage directory
  const storageDir = context?.globalStorageUri?.fsPath || '';
  outbox = new Outbox(clock, { storageDir });

  currentWindowId = randomBytes(6).toString('hex');
  const isRemote = Boolean(currentCapabilities.remoteName);

  // 5. Initialize Engine Client & Endpoint Discovery
  engineClient = new EngineClient(outbox, {
    windowId: currentWindowId,
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
        const heldCount = digestManager?.getItemCount() ?? 0;
        statusBar?.setFocused(Math.round(stateMsg.leaseMs / 60000), heldCount);

        // Auto-revert when lease expires
        leaseTimer = setTimeout(() => {
          statusBar?.setFocused(0, 0);
        }, stateMsg.leaseMs);
      } else {
        statusBar?.setFocused(0, 0);
      }
    },
    onMessage: (msg) => {
      if (msg.kind === 'digest') {
        digestManager?.updateItems(msg.items || []);
      } else if (msg.kind === 'nudge') {
        nudgeManager?.handleNudge(msg);
      } else if (msg.kind === 'cmd') {
        if (msg.action === 'pause') {
          privacyFilter?.setPaused(true);
          statusBar?.setPaused();
        } else if (msg.action === 'resume') {
          privacyFilter?.setPaused(false);
          statusBar?.setWaiting();
        } else if (msg.action === 'forget_range') {
          inMemoryEvents.length = 0;
          sessionManager?.clearHistory();
          resumeManager?.clearSnapshot();
          digestManager?.clearItems();
        }
      }
    }
  });

  if (isRemote) {
    statusBar?.setRemoteUnsupported();
    diagOutputChannel?.appendLine(`[Host] Remote window detected (${currentCapabilities.remoteName}). Local engine connection disabled per policy.`);
  } else {
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

    const initialFocused = vscodeApi?.window?.state ? Boolean(vscodeApi.window.state.focused) : true;
    if (initialFocused) {
      engineClient.start();
    } else {
      engineClient.stop();
      statusBar?.setWaiting();
    }
  }

  // 6. Initialize Aggregator
  aggregator = new WindowAggregator(clock);
  aggregator.setWindowId(currentWindowId);

  // 7a. Initialize TaskContextTracker + BreakDetector (salt from secrets, async)
  const placeholderSalt = '0'.repeat(64);
  taskContextTracker = new TaskContextTracker(clock, placeholderSalt);
  breakDetector = new BreakDetector(clock);

  // Phase 4: Resume Tracker & Feature Manager
  resumeTracker = new ResumeTracker(clock);
  resumeManager = new ResumeManager({
    vscodeApi,
    workspaceState: context?.workspaceState,
    privacyFilter,
    resumeTracker,
    statusBar,
    onSnapshotChanged: () => resumeTreeProvider?.refresh()
  });
  resumeTreeProvider = new ResumeTreeDataProvider(vscodeApi, resumeManager);

  // Phase 5: Session, Nudge, Digest Managers
  sessionManager = new SessionManager({
    clock,
    salt: placeholderSalt,
    outbox,
    statusBar,
    vscodeApi,
    onFlushImmediate: () => engineClient?.flushImmediate(),
    onSessionChange: () => todayTreeProvider?.refresh()
  });

  nudgeManager = new NudgeManager({
    clock,
    outbox,
    engineClient,
    vscodeApi
  });

  digestManager = new DigestManager({
    engineClient,
    outbox,
    vscodeApi,
    onDigestChange: () => digestTreeProvider?.refresh()
  });

  todayTreeProvider = new TodayTreeDataProvider(vscodeApi, sessionManager);
  digestTreeProvider = new DigestTreeDataProvider(vscodeApi, digestManager);

  if (vscodeApi?.window?.registerTreeDataProvider) {
    try {
      context.subscriptions.push(
        vscodeApi.window.registerTreeDataProvider('dhyan.today', todayTreeProvider),
        vscodeApi.window.registerTreeDataProvider('dhyan.resume', resumeTreeProvider),
        vscodeApi.window.registerTreeDataProvider('dhyan.digest', digestTreeProvider)
      );
    } catch {}
  }

  // Helpers to read current context
  const getCurrentTaskKey = (): string => {
    if (taskContextTracker && gitSensor) {
      const workspacePath = gitSensor.getWorkspacePath() ?? '';
      const branchName = gitSensor.getBranchName() ?? '';
      return taskContextTracker.computeTaskKey(workspacePath, branchName);
    }
    return '0000000000000000';
  };

  const getCurrentLoad = (): ResumeLoadContext => {
    return {
      dirtyDocs: 0,
      openTabs: 0,
      debugPaused: false,
      uncommitted: gitSensor?.getUncommittedCount() ?? 0,
      taskType: taskContextTracker?.computeTaskType() ?? 'idle'
    };
  };

  // Asynchronously upgrade to the real per-install salt
  if (context?.secrets) {
    getOrCreateSalt(context.secrets as any).then((salt) => {
      taskContextTracker = new TaskContextTracker(clock, salt);
      sessionManager?.setSalt(salt);
    }).catch(() => {
      // secrets unavailable — continue with placeholder salt
    });
  }

  // Helper: sanitize + enqueue + flush an ed_break event
  const emitBreak = (kind: string, confidence: number): void => {
    const raw = { type: 'ed_break', ts: clock.now(), mono: clock.mono(), kind, confidence };
    const sanitized = PrivacyGate.sanitize(raw);
    if (sanitized && outbox) {
      outbox.enqueue(sanitized);
      engineClient?.flushImmediate();
    }
    digestManager?.notifyAtBreak();
  };

  // 7b. Initialize Sensors
  if (vscodeApi) {
    editorSensor = new EditorSensor({
      aggregator,
      privacyFilter,
      vscode: vscodeApi,
      onEdit: (kind) => {
        resumeManager?.recordActivity();
        nudgeManager?.recordTyping();
        if (resumeTracker && outbox) {
          const taskKey = getCurrentTaskKey();
          const resumeEvent = resumeTracker.onEdit(kind, taskKey);
          if (resumeEvent) {
            const sanitized = PrivacyGate.sanitize(resumeEvent);
            if (sanitized) {
              outbox.enqueue(sanitized);
              engineClient?.flushImmediate();
            }
          }
        }
      },
      onActivity: () => {
        resumeManager?.recordActivity();
      }
    });
    windowSensor = new WindowSensor(vscodeApi, aggregator, clock, (focused) => {
      const taskKey = getCurrentTaskKey();
      if (!focused) {
        // Blur: capture snapshot and record blur timestamp
        resumeTracker?.onBlur();
        resumeManager?.captureSnapshot(taskKey);
      } else {
        // Focus regained: check resume assist candidate
        const config = vscodeApi?.workspace?.getConfiguration?.('dhyan');
        const minAwayMinutes = Number(config?.get('minAwayMinutes', 3)) || 3;
        const assistProbability = Number(config?.get('assistProbability', 0.8)) ?? 0.8;
        const load = getCurrentLoad();
        const candidate = resumeTracker?.onFocus({ taskKey, load }, minAwayMinutes, assistProbability);
        if (candidate && candidate.assist) {
          const snapshot = resumeManager?.getSnapshot();
          if (snapshot) {
            statusBar?.setResume(snapshot.fileName, snapshot.line + 1);
          }
        }
      }

      // Multi-window focus debounce (section 7.2)
      if (!isRemote && engineClient) {
        if (focusDebounceTimer) {
          clearTimeout(focusDebounceTimer);
          focusDebounceTimer = null;
        }
        focusDebounceTimer = setTimeout(() => {
          const currentFocused = vscodeApi?.window?.state ? Boolean(vscodeApi.window.state.focused) : focused;
          if (currentFocused) {
            if (engineClient && engineClient.getState() === 'disconnected') {
              engineClient.start();
            }
          } else {
            if (engineClient && engineClient.getState() !== 'disconnected') {
              engineClient.stop();
              if (statusBar?.getMode() === 'focused') {
                statusBar.setWaiting();
              }
            }
          }
        }, 2000);
      }
    });
    diagSensor = new DiagnosticsSensor(vscodeApi, aggregator, clock);
    debugSensor = new DebugSensor(vscodeApi, aggregator, (phase) => {
      nudgeManager?.setDebugPaused(phase === 'pause');
    });

    // Phase 3: Git, Tasks, Terminal sensors
    gitSensor = new GitSensor(vscodeApi, breakDetector, taskContextTracker);
    tasksSensor = new TasksSensor(
      vscodeApi,
      clock,
      breakDetector,
      taskContextTracker,
      outbox!,
      () => engineClient?.flushImmediate(),
      emitBreak
    );
    terminalSensor = new TerminalSensor(vscodeApi, clock, taskContextTracker, outbox!);
  }

  // Break-detector & Censoring coarse tick (every 2s)
  breakTickTimer = setInterval(() => {
    if (breakDetector) {
      const brk = breakDetector.tick(clock.now());
      if (brk) {
        emitBreak(brk.kind, brk.confidence);
      }
    }
    if (resumeTracker && outbox) {
      const censored = resumeTracker.tick();
      if (censored) {
        const sanitized = PrivacyGate.sanitize(censored);
        if (sanitized) {
          outbox.enqueue(sanitized);
          engineClient?.flushImmediate();
        }
      }
    }
  }, 2000);

  // 8. 10-Second Periodic Tick Timer
  tickTimer = setInterval(() => {
    if (aggregator && outbox) {
      // Update aggregator context from TaskContextTracker + GitSensor
      if (taskContextTracker && gitSensor) {
        const workspacePath = gitSensor.getWorkspacePath() ?? '';
        const branchName = gitSensor.getBranchName() ?? '';
        const taskKey = taskContextTracker.computeTaskKey(workspacePath, branchName);
        const taskType = taskContextTracker.computeTaskType();
        const uncommitted = gitSensor.getUncommittedCount();
        aggregator.setContext({ taskKey, taskType, uncommitted });
      }

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
          `Hunch attention tracking is now ${isPaused ? 'PAUSED' : 'RESUMED'}.`
        );
      }
    }
  });

  registerCmd('dhyan.diagnostics', () => {
    try {
      if (vscodeApi?.window?.createOutputChannel) {
        if (!diagOutputChannel) {
          const ch = vscodeApi.window.createOutputChannel(HUNCH_BRAND.diagnosticsChannelName);
          diagOutputChannel = ch;
          context.subscriptions.push(ch);
        }
        const activeChannel = diagOutputChannel;
        if (activeChannel) {
          activeChannel.clear();
          activeChannel.appendLine(HUNCH_BRAND.diagnosticsBanner);
          activeChannel.appendLine(`Timestamp: ${new Date(clock.now()).toISOString()}`);
          activeChannel.appendLine(`Window ID: ${currentWindowId}`);
          activeChannel.appendLine(`Remote Window: ${isRemote ? `YES (${currentCapabilities?.remoteName}) - connection disabled` : 'NO'}`);
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

  registerCmd('dhyan.resume', async () => {
    return await resumeManager?.executeResume();
  });

  registerCmd('dhyan.park', async () => {
    const taskKey = getCurrentTaskKey();
    await resumeManager?.executePark(taskKey);
  });

  registerCmd('dhyan.startSession', async () => {
    await sessionManager?.startSession();
  });

  registerCmd('dhyan.endSession', async () => {
    await sessionManager?.endSession();
  });

  registerCmd('dhyan.toggleTimer', async () => {
    await sessionManager?.toggleTimer();
  });

  registerCmd('dhyan.openDigest', async () => {
    await digestManager?.openDigest();
  });

  registerCmd('dhyan.today', () => {
    todayTreeProvider?.refresh();
  });

  registerCmd('dhyan.forgetToday', async () => {
    inMemoryEvents.length = 0;
    sessionManager?.clearHistory();
    resumeManager?.clearSnapshot();
    digestManager?.clearItems();
    if (vscodeApi?.window?.setStatusBarMessage) {
      vscodeApi.window.setStatusBarMessage("Hunch: Today's history forgotten.", 3000);
    }
  });

  const standardCommands = [
    'dhyan.connect',
    'dhyan.openInsights',
    'dhyan.reconnect',
    'dhyan.showStatus'
  ];

  for (const cmd of standardCommands) {
    registerCmd(cmd, () => {});
  }

  // Wind-down poller (Pre-bedtime circadian wellbeing)
  const alignment = vscodeApi?.StatusBarAlignment?.Right ?? 2;
  windDownBar = vscodeApi?.window?.createStatusBarItem ? vscodeApi.window.createStatusBarItem(alignment, 90) : null;
  if (windDownBar) {
    windDownBar.command = 'dhyan.park';
    if (context?.subscriptions) {
      context.subscriptions.push(windDownBar);
    }
  }

  const pollWindDown = async () => {
    try {
      const config = vscodeApi?.workspace?.getConfiguration ? vscodeApi.workspace.getConfiguration('dhyan') : null;
      const engineUrl = (config?.get ? config.get('engineUrl', 'http://127.0.0.1:8765') : null) || 'http://127.0.0.1:8765';
      const resp = await fetch(`${engineUrl}/v1/wellbeing/circadian`);
      if (resp.ok) {
        const data = await resp.json() as any;
        if (data && data.windDownActive) {
          if (windDownBar) {
            windDownBar.text = '$(moon) Bedtime in 30m — Park context?';
            windDownBar.tooltip = `Focus mode: ${data.mode}. Recommended session: ${data.recommendedFocusMinutes}m.`;
            windDownBar.show();
          }
        } else {
          windDownBar?.hide();
        }
      }
    } catch {
      windDownBar?.hide(); // Engine not running — hide silently
    }
  };

  void pollWindDown(); // immediate first check
  windDownInterval = setInterval(pollWindDown, 60_000);
  if (context?.subscriptions) {
    context.subscriptions.push({
      dispose: () => {
        if (windDownInterval) {
          clearInterval(windDownInterval);
          windDownInterval = null;
        }
      }
    });
  }

  return {
    privacyFilter,
    capabilities: currentCapabilities,
    clock,
    aggregator,
    outbox,
    engineClient,
    statusBar,
    inMemoryEvents,
    taskContextTracker,
    breakDetector,
    resumeTracker,
    resumeManager,
    sessionManager,
    nudgeManager,
    digestManager,
    resumeTreeProvider,
    todayTreeProvider,
    digestTreeProvider,
    windDownBar
  };
}

export function deactivate(): void {
  if (windDownInterval) {
    clearInterval(windDownInterval);
    windDownInterval = null;
  }
  if (windDownBar) {
    windDownBar.dispose();
    windDownBar = null;
  }
  if (tickTimer) {
    clearInterval(tickTimer);
    tickTimer = null;
  }
  if (breakTickTimer) {
    clearInterval(breakTickTimer);
    breakTickTimer = null;
  }
  if (leaseTimer) {
    clearTimeout(leaseTimer);
    leaseTimer = null;
  }
  if (focusDebounceTimer) {
    clearTimeout(focusDebounceTimer);
    focusDebounceTimer = null;
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
  if (resumeManager) {
    resumeManager.dispose();
    resumeManager = null;
  }
  if (nudgeManager) {
    nudgeManager.dispose();
    nudgeManager = null;
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
  if (gitSensor) {
    gitSensor.dispose();
    gitSensor = null;
  }
  if (tasksSensor) {
    tasksSensor.dispose();
    tasksSensor = null;
  }
  if (terminalSensor) {
    terminalSensor.dispose();
    terminalSensor = null;
  }
  if (diagOutputChannel) {
    diagOutputChannel.dispose();
    diagOutputChannel = null;
  }
  outbox = null;
  privacyFilter = null;
  aggregator = null;
  taskContextTracker = null;
  breakDetector = null;
  resumeTracker = null;
  resumeTreeProvider = null;
  sessionManager = null;
  digestManager = null;
  todayTreeProvider = null;
  digestTreeProvider = null;
  currentCapabilities = null;
}
