import { WebSocket } from 'ws';
// @ts-ignore
import { DhyanCompanionViewProvider } from './companionProvider.js';
// @ts-ignore
import { DhyanInsightsPanel } from './insightsPanel.js';

export class DhyanVsCodeSensor {
  port: number;
  token: string;
  vscode: any;
  clientVersion: string;
  windowSec: number;
  stopped: boolean;
  ws: WebSocket | null;
  authenticated: boolean;
  heartbeatTimer: any;
  burstTimer: any;
  reconnectTimer: any;
  reconnectAttempts: number;
  editsInWindow: number;
  savesInWindow: number;
  currentErrors: number;
  currentWarnings: number;
  windowFocused: boolean;
  isDebugging: boolean;
  focusRegainedTs: number | null;
  waitingForFirstEdit: boolean;
  lastEditTs: number;
  currentState: string;
  companionProvider: any;
  idleTimer: any;
  statusBarItem: any;
  disposables: any[];

  constructor(options: any = {}) {
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

    this.editsInWindow = 0;
    this.savesInWindow = 0;
    this.currentErrors = 0;
    this.currentWarnings = 0;
    this.windowFocused = true;
    this.isDebugging = false;

    this.focusRegainedTs = null;
    this.waitingForFirstEdit = false;

    this.lastEditTs = Date.now();
    this.currentState = 'flowing';
    this.companionProvider = null;
    this.idleTimer = null;
    this.statusBarItem = null;

    this.disposables = [];
  }

  setCompanionProvider(provider: any) {
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
      if (typeof d?.dispose === 'function') {
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
    } catch (_) {
      if (!this.stopped) this.scheduleReconnect();
      return;
    }

    this.ws.on('open', () => {
      this.reconnectAttempts = 0;
      this.send({
        kind: 'auth',
        token: this.token,
        client: 'vscode',
        clientVersion: this.clientVersion
      });
    });

    this.ws.on('message', (data: any) => {
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

    this.ws.on('error', () => {});
  }

  scheduleReconnect() {
    if (this.stopped) return;
    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
    this.reconnectTimer = setTimeout(() => this.connect(), delay);
  }

  send(msg: any) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
    try {
      this.ws.send(JSON.stringify(msg));
    } catch (_) {}
  }

  startHeartbeat(sec: number) {
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
        this.companionProvider.updateEmotion('BORED');
      }
    }, 5000);
  }

  sendState() {
    if (!this.authenticated) return;
    this.send({
      kind: 'vscode_state',
      ts: Date.now(),
      state: this.currentState,
      windowFocused: this.windowFocused,
      isDebugging: this.isDebugging
    });
  }

  updateState() {
    const prev = this.currentState;
    if (!this.windowFocused) {
      this.currentState = 'idle_open';
    } else if (this.currentErrors >= 3 && this.editsInWindow < 5) {
      this.currentState = 'stuck';
    } else {
      this.currentState = 'flowing';
    }

    if (prev !== this.currentState) {
      this.sendState();
      this.updatePetAndStatusBar();
    }
  }

  updatePetAndStatusBar() {
    if (!this.companionProvider) return;
    if (this.currentState === 'flowing') {
      this.companionProvider.updateEmotion('FOCUSED', 2, -1);
    } else if (this.currentState === 'stuck') {
      this.companionProvider.updateEmotion('CONFUSED', -2, 2);
    } else {
      this.companionProvider.updateEmotion('RESTING', 0, 0);
    }
  }

  onDocumentChange(e: any) {
    if (!e || !e.document) return;
    this.editsInWindow++;
    this.lastEditTs = Date.now();

    if (this.waitingForFirstEdit && this.focusRegainedTs) {
      const delayMs = Math.min(Math.max(Date.now() - this.focusRegainedTs, 0), 3600000);
      this.send({
        kind: 'resume_first_edit',
        ts: Date.now(),
        delayMs
      });
      this.waitingForFirstEdit = false;
      this.focusRegainedTs = null;
    }

    if (this.companionProvider) {
      this.companionProvider.updateEmotion('EXCITED', 0, -3);
    }
  }

  onDocumentSave() {
    this.savesInWindow++;
    if (this.companionProvider) {
      this.companionProvider.updateEmotion('HAPPY');
    }
  }

  onWindowStateChange(focused: boolean) {
    const wasFocused = this.windowFocused;
    this.windowFocused = focused;

    if (!wasFocused && focused) {
      this.focusRegainedTs = Date.now();
      this.waitingForFirstEdit = true;
    }

    this.sendState();
    this.updateState();
  }

  onDiagnosticsChange(errors: number, warnings: number) {
    this.currentErrors = errors;
    this.currentWarnings = warnings;
    this.updateState();
  }

  onDebugStateChange(active: boolean) {
    this.isDebugging = active;
    this.sendState();
  }

  openInsights() {
    if (this.vscode && this.vscode.commands && this.vscode.commands.executeCommand) {
      this.vscode.commands.executeCommand('dhyan.openInsights');
    }
  }

  startSession(_preset: string, minutes: number) {
    if (this.statusBarItem) {
      this.statusBarItem.text = `$(pulse) Dhyan: ${minutes}:00 • Deep Focus (100%)`;
    }
    if (this.companionProvider) {
      this.companionProvider.updateEmotion('FOCUSED');
      this.companionProvider.updateFocusMinutes(minutes);
    }
  }

  setupListeners() {
    if (!this.vscode) return;

    if (this.vscode.workspace?.onDidChangeTextDocument) {
      this.disposables.push(
        this.vscode.workspace.onDidChangeTextDocument((e: any) => {
          this.onDocumentChange(e);
        })
      );
    }

    if (this.vscode.workspace?.onDidSaveTextDocument) {
      this.disposables.push(
        this.vscode.workspace.onDidSaveTextDocument(() => {
          this.onDocumentSave();
        })
      );
    }

    if (this.vscode.window?.onDidChangeWindowState) {
      this.disposables.push(
        this.vscode.window.onDidChangeWindowState((state: any) => {
          this.onWindowStateChange(Boolean(state?.focused));
        })
      );
    }

    if (this.vscode.tasks?.onDidEndTaskProcess) {
      this.disposables.push(
        this.vscode.tasks.onDidEndTaskProcess((e: any) => {
          if (e?.exitCode === 0 && this.companionProvider) {
            this.companionProvider.updateEmotion('HAPPY');
          }
        })
      );
    }

    if (this.vscode.languages?.onDidChangeDiagnostics) {
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
