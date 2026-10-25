import { WebSocket } from 'ws';

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

    // State machine
    this.lastEditTs = Date.now();
    this.currentState = 'flowing'; // 'flowing' | 'stuck' | 'idle_open'

    // Subscriptions to dispose
    this.disposables = [];
  }

  start() {
    this.connect();
    this.setupListeners();
    this.startBurstTimer();
  }

  stop() {
    this.stopped = true;
    clearInterval(this.heartbeatTimer);
    clearInterval(this.burstTimer);
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
      }
    });

    this.ws.on('close', () => {
      this.authenticated = false;
      clearInterval(this.heartbeatTimer);
      if (!this.stopped) {
        this.scheduleReconnect();
      }
    });

    this.ws.on('error', () => {
      // ws close event will trigger reconnect
    });
  }

  scheduleReconnect() {
    const delay = Math.min(1000 * Math.pow(1.5, this.reconnectAttempts++), 30000);
    this.reconnectTimer = setTimeout(() => {
      this.connect();
    }, delay);
  }

  send(msg) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg));
    }
  }

  startHeartbeat(heartbeatSec) {
    clearInterval(this.heartbeatTimer);
    this.heartbeatTimer = setInterval(() => {
      if (this.authenticated) {
        this.send({
          kind: 'ext_heartbeat',
          ts: Date.now()
        });
      }
    }, heartbeatSec * 1000);
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

    // Reset window counters
    this.editsInWindow = 0;
    this.savesInWindow = 0;
  }

  onDocumentChange() {
    this.editsInWindow++;
    this.lastEditTs = Date.now();

    // Check resumption delay
    if (this.waitingForFirstEdit && this.focusRegainedTs) {
      const delayMs = Math.min(Math.max(Date.now() - this.focusRegainedTs, 0), 3600000);
      this.waitingForFirstEdit = false;
      this.focusRegainedTs = null;

      if (this.authenticated) {
        this.send({
          kind: 'resume_first_edit',
          ts: Date.now(),
          delayMs
        });
      }
    }
  }

  onDocumentSave() {
    this.savesInWindow++;
  }

  onWindowStateChange(focused) {
    const wasFocused = this.windowFocused;
    this.windowFocused = focused;

    if (!wasFocused && focused) {
      // Focus regained!
      this.focusRegainedTs = Date.now();
      this.waitingForFirstEdit = true;
    } else if (!focused) {
      // Focus lost
      this.waitingForFirstEdit = false;
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

  setupListeners() {
    if (!this.vscode) return;

    // Text edit: COUNT ONLY. NEVER inspect document content or path!
    if (this.vscode.workspace && this.vscode.workspace.onDidChangeTextDocument) {
      this.disposables.push(
        this.vscode.workspace.onDidChangeTextDocument(() => {
          this.onDocumentChange();
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
  sensorInstance.start();
}

export function deactivate() {
  if (sensorInstance) {
    sensorInstance.stop();
    sensorInstance = null;
  }
}
