import { WebSocket } from 'ws';
import { DhyanCompanionViewProvider } from './companionProvider.js';
import { DhyanInsightsPanel } from './insightsPanel.js';

export class DhyanVsCodeSensor {
  constructor(options = {}) {
    this.port = options.port || 47821;
    this.token = options.token || '';
    this.vscode = options.vscode || null;
    this.clientVersion = options.clientVersion || '1.0.0';
    this.windowSec = options.windowSec || 30;

    this.stopped = false;
    this.ws = null;
    this.authenticated = false;
    this.heartbeatTimer = null;
    this.burstTimer = null;
    this.reconnectTimer = null;
    this.reconnectAttempts = 0;

    // Telemetry state (counts and metrics only)
    this.editsInWindow = 0;
    this.savesInWindow = 0;
    this.currentErrors = 0;
    this.currentWarnings = 0;
    this.windowFocused = true;
    this.isDebugging = false;

    // Resumption delay tracking
    this.focusRegainedTs = null;
    this.waitingForFirstEdit = false;

    // State machine & Pet Bridge
    this.lastEditTs = Date.now();
    this.currentState = 'flowing'; // 'flowing' | 'stuck' | 'idle_open'
    this.companionProvider = null;
    this.idleTimer = null;
    this.statusBarItem = null;

    // Subscriptions to dispose
    this.disposables = [];
  }

  setCompanionProvider(provider) {
    this.companionProvider = provider;
  }

  start() {
    this.connect();
    this.setupListeners();
    this.startBurstTimer();
    this.setupIdleTracker();
  }

  stop() {
    this.stopped = true;
    clearInterval(this.heartbeatTimer);
    clearInterval(this.burstTimer);
    clearInterval(this.idleTimer);
    clearTimeout(this.reconnectTimer);

    if (this.ws) {
      try {
        this.ws.removeAllListeners();
        this.ws.close();
      } catch (_) {}
    }

    for (const d of this.disposables) {
      if (typeof d.dispose === 'function') {
        d.dispose();
      }
    }
    this.disposables = [];
  }

  connect() {
    if (this.stopped) return;
    clearTimeout(this.reconnectTimer);
    const url = `ws://127.0.0.1:${this.port}/ingest`;

    try {
      this.ws = new WebSocket(url);
    } catch (err) {
      if (!this.stopped) this.scheduleReconnect();
      return;
    }

    this.ws.on('open', () => {
      this.reconnectAttempts = 0;
      // Step 1: Send auth within 3 seconds
      this.send({
        kind: 'auth',
        token: this.token,
        client: 'vscode',
        clientVersion: this.clientVersion
      });
    });

    this.ws.on('message', (data) => {
      let msg;
      try {
        msg = JSON.parse(data.toString('utf8'));
      } catch (_) {
        return;
      }

      if (msg.kind === 'auth_ok') {
        this.authenticated = true;
        this.startHeartbeat(msg.config && msg.config.heartbeatSec ? msg.config.heartbeatSec : 5);
        this.sendState();
      } else if (msg.kind === 'auth_fail') {
        this.authenticated = false;
        this.stop();
      }
    });

    this.ws.on('close', () => {
      this.authenticated = false;
      if (!this.stopped) this.scheduleReconnect();
    });

    this.ws.on('error', () => {
      // Handled by close listener
    });
  }

  scheduleReconnect() {
    if (this.stopped) return;
    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
    this.reconnectTimer = setTimeout(() => this.connect(), delay);
  }

  send(msg) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
    try {
      this.ws.send(JSON.stringify(msg));
    } catch (_) {}
  }

  startHeartbeat(sec) {
    clearInterval(this.heartbeatTimer);
    this.heartbeatTimer = setInterval(() => {
      if (this.authenticated) {
        this.send({ kind: 'ping' });
      }
    }, sec * 1000);
  }

  startBurstTimer() {
    clearInterval(this.burstTimer);
    this.burstTimer = setInterval(() => {
      this.flushBurst();
      this.updateState();
    }, this.windowSec * 1000);
  }

  flushBurst() {
    if (!this.authenticated) return;

    // Calculate edits per minute scaled from the window
    const editsPerMin = Math.min(Math.round((this.editsInWindow / this.windowSec) * 60), 2000);

    this.send({
      kind: 'edit_burst',
      ts: Date.now(),
      editsPerMin,
      saves: this.savesInWindow,
      errors: this.currentErrors,
      warnings: this.currentWarnings,
      windowSec: this.windowSec
    });

    this.editsInWindow = 0;
    this.savesInWindow = 0;
  }

  setupIdleTracker() {
    clearInterval(this.idleTimer);
    this.idleTimer = setInterval(() => {
      const now = Date.now();
      const idleSec = (now - this.lastEditTs) / 1000;
      if (idleSec >= 20 && this.companionProvider) {
        this.companionProvider.updateEmotion('SLEEPY');
      }
    }, 5000);
  }

  onDocumentChange(e) {
    this.editsInWindow++;
    this.lastEditTs = Date.now();

    // Pet eye saccades toward typing
    if (this.companionProvider) {
      const eyeDx = (Math.random() - 0.5) * 1.5;
      const eyeDy = (Math.random() - 0.5) * 0.8;
      this.companionProvider.updateEmotion('FOCUSED', eyeDx, eyeDy);
    }

    // Check resumption delay
    if (this.waitingForFirstEdit && this.focusRegainedTs) {
      const delayMs = Date.now() - this.focusRegainedTs;
      this.waitingForFirstEdit = false;

      if (this.authenticated) {
        this.send({
          kind: 'resume_first_edit',
          ts: Date.now(),
          delayMs
        });
      }
    }

    this.updateState();
  }

  onDocumentSave() {
    this.savesInWindow++;
    this.updateState();
  }

  onWindowStateChange(focused) {
    const wasFocused = this.windowFocused;
    this.windowFocused = focused;

    if (!wasFocused && focused) {
      // Focus regained!
      this.focusRegainedTs = Date.now();
      this.waitingForFirstEdit = true;
      if (this.companionProvider) {
        this.companionProvider.updateEmotion('CURIOUS');
      }
    } else if (!focused) {
      // Focus lost
      this.waitingForFirstEdit = false;
      if (this.companionProvider) {
        this.companionProvider.updateEmotion('SLEEPY');
      }
    }

    this.updateState();
  }

  onDiagnosticsChange(errors, warnings) {
    this.currentErrors = errors;
    this.currentWarnings = warnings;
    this.updateState();
  }

  onDebugStateChange(isDebugging) {
    this.isDebugging = isDebugging;
    this.updateState();
  }

  updateState() {
    const now = Date.now();
    const idleMs = now - this.lastEditTs;

    let newState = 'flowing';

    if (!this.windowFocused || idleMs > 5 * 60 * 1000) {
      newState = 'idle_open';
    } else if (this.currentErrors >= 3 && this.editsInWindow < 5) {
      newState = 'stuck';
    } else {
      newState = 'flowing';
    }

    if (newState !== this.currentState) {
      this.currentState = newState;
      this.sendState();
    }
  }

  sendState() {
    if (!this.authenticated) return;
    this.send({
      kind: 'vscode_state',
      ts: Date.now(),
      state: this.currentState,
      windowFocused: this.windowFocused,
      debugging: this.isDebugging
    });
  }

  startSession(preset, minutes) {
    if (this.statusBarItem) {
      this.statusBarItem.text = `$(pulse) Dhyan: ${minutes}:00 • ${preset} (100%)`;
    }
    if (this.companionProvider) {
      this.companionProvider.updateEmotion('FOCUSED');
      this.companionProvider.updateFocusMinutes(minutes);
    }
  }

  setupListeners() {
    if (!this.vscode) return;

    // Text edit: COUNT ONLY. NEVER inspect document content or path!
    if (this.vscode.workspace && this.vscode.workspace.onDidChangeTextDocument) {
      this.disposables.push(
        this.vscode.workspace.onDidChangeTextDocument((e) => {
          this.onDocumentChange(e);
        })
      );
    }

    // Save: increment save count
    if (this.vscode.workspace && this.vscode.workspace.onDidSaveTextDocument) {
      this.disposables.push(
        this.vscode.workspace.onDidSaveTextDocument(() => {
          this.onDocumentSave();
        })
      );
    }

    // Window focus change
    if (this.vscode.window && this.vscode.window.onDidChangeWindowState) {
      this.disposables.push(
        this.vscode.window.onDidChangeWindowState((state) => {
          this.onWindowStateChange(Boolean(state && state.focused));
        })
      );
    }

    // Tasks / Build end event bridge
    if (this.vscode.tasks && this.vscode.tasks.onDidEndTaskProcess) {
      this.disposables.push(
        this.vscode.tasks.onDidEndTaskProcess((e) => {
          if (e && e.exitCode === 0 && this.companionProvider) {
            this.companionProvider.updateEmotion('HAPPY');
          }
        })
      );
    }

    // Diagnostics (errors / warnings count only)
    if (this.vscode.languages && this.vscode.languages.onDidChangeDiagnostics) {
      this.disposables.push(
        this.vscode.languages.onDidChangeDiagnostics(() => {
          let errs = 0;
          let warns = 0;
          if (this.vscode.languages.getDiagnostics) {
            const allDiag = this.vscode.languages.getDiagnostics();
            for (const [, fileDiags] of allDiag) {
              for (const d of fileDiags) {
                if (d.severity === 0 || d.severity === 'Error') errs++;
                else if (d.severity === 1 || d.severity === 'Warning') warns++;
              }
            }
          }
          this.onDiagnosticsChange(errs, warns);
        })
      );
    }

    // Debug session start/end
    if (this.vscode.debug) {
      if (this.vscode.debug.onDidStartDebugSession) {
        this.disposables.push(
          this.vscode.debug.onDidStartDebugSession(() => {
            this.onDebugStateChange(true);
          })
        );
      }
      if (this.vscode.debug.onDidTerminateDebugSession) {
        this.disposables.push(
          this.vscode.debug.onDidTerminateDebugSession(() => {
            this.onDebugStateChange(false);
          })
        );
      }
    }
  }
}

// VS Code Extension Lifecycle Entry Points
let sensorInstance = null;

export function activate(context) {
  const config = (context && context.workspace && context.workspace.getConfiguration)
    ? context.workspace.getConfiguration('dhyan')
    : null;

  const port = config ? config.get('ingestPort', 47821) : 47821;
  const token = config ? config.get('token', '') : '';

  sensorInstance = new DhyanVsCodeSensor({
    port,
    token,
    vscode: context
  });

  // 1. Surface 1: Status Bar Focus Pill
  if (context && context.window && context.window.createStatusBarItem) {
    const statusBar = context.window.createStatusBarItem(1, 100);
    statusBar.text = '$(pulse) Dhyan: 24:15 • Focused (92%)';
    statusBar.tooltip = 'Dhyan Attention Companion — Click to view sidebar or toggle session';
    statusBar.command = 'dhyan.openInsights';
    statusBar.show();
    sensorInstance.statusBarItem = statusBar;
    if (context.subscriptions) context.subscriptions.push(statusBar);
  }

  // 2. Surface 2: Sidebar Webview Panel
  if (context && context.window && context.window.registerWebviewViewProvider) {
    const provider = new DhyanCompanionViewProvider(context.extensionUri, sensorInstance);
    sensorInstance.setCompanionProvider(provider);
    if (context.subscriptions) {
      context.subscriptions.push(
        context.window.registerWebviewViewProvider('dhyan.companion', provider)
      );
    }
  }

  // 3. Surface 3: Full-Tab Insights Dashboard Command
  if (context && context.commands && context.commands.registerCommand) {
    context.subscriptions.push(
      context.commands.registerCommand('dhyan.openInsights', () => {
        DhyanInsightsPanel.createOrShow(context.extensionUri, sensorInstance);
      })
    );
    context.subscriptions.push(
      context.commands.registerCommand('dhyan.toggleTimer', () => {
        sensorInstance.startSession('coding', 25);
      })
    );
  }

  sensorInstance.start();
}

export function deactivate() {
  if (sensorInstance) {
    sensorInstance.stop();
    sensorInstance = null;
  }
}
