/**
 * buildShield.ts — Build-Time Compile Shield & Active Coding Reporter.
 *
 * Protects focus during observed builds/tests and releases only the build's
 * own shield when the process ends, cancels, or expires.
 * Also reports active coding state to the engine sidecar so the browser extension
 * can intelligently intercept rabbit holes only during active development.
 */

import { classifyTaskName } from '../sensors/tasks.ts';

export interface BuildShieldState {
  active: boolean;
  taskName: string;
  startTime: number;
  lastOutcome?: 'success' | 'failed';
  lastDurationMs?: number;
}

export class BuildShieldManager {
  private vscodeApi: any;
  private disposables: Array<{ dispose: () => void }> = [];
  private engineUrl: string = 'http://127.0.0.1:8765';
  private currentState: BuildShieldState = {
    active: false,
    taskName: '',
    startTime: 0,
  };
  private lastBuildSummary: string = '';
  private activeCodingInterval: ReturnType<typeof setInterval> | null = null;

  constructor(vscodeApi: any, engineUrl = 'http://127.0.0.1:8765') {
    this.vscodeApi = vscodeApi;
    this.engineUrl = engineUrl;

    this.registerListeners();
    this.startActiveCodingHeartbeat();
  }

  getLastBuildSummary(): string {
    return this.lastBuildSummary;
  }

  isShieldActive(): boolean {
    return this.currentState.active;
  }

  getCurrentTaskName(): string {
    return this.currentState.taskName;
  }

  private registerListeners(): void {
    try {
      // 1. Task Start Listener
      if (this.vscodeApi?.tasks?.onDidStartTaskProcess) {
        const startSub = this.vscodeApi.tasks.onDidStartTaskProcess((event: any) => {
          this.handleTaskStart(event);
        });
        this.disposables.push(startSub);
      }

      // 2. Task End Listener
      if (this.vscodeApi?.tasks?.onDidEndTaskProcess) {
        const endSub = this.vscodeApi.tasks.onDidEndTaskProcess((event: any) => {
          this.handleTaskEnd(event);
        });
        this.disposables.push(endSub);
      }
    } catch {
      // Silent no-op if tasks API is unavailable
    }
  }

  private async handleTaskStart(event: any): Promise<void> {
    try {
      const name: string = event?.execution?.task?.name ?? 'build';
      const taskType: string = event?.execution?.task?.type ?? '';
      const category = classifyTaskName(name, taskType);

      // Only shield during build or test execution
      if (category !== 'build' && category !== 'test') {
        return;
      }

      this.currentState = {
        active: true,
        taskName: name,
        startTime: Date.now(),
      };

      // Notify engine sidecar of active shield
      await fetch(`${this.engineUrl}/v1/flow/compile-shield`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          active: true,
          task_name: name,
        }),
      }).catch(() => {});
    } catch {}
  }

  private async handleTaskEnd(event: any): Promise<void> {
    try {
      const name: string = event?.execution?.task?.name ?? '';
      const exitCode: number = event?.exitCode ?? 0;
      const outcome = exitCode === 0 ? 'success' : 'failed';

      // Verify this end event corresponds to our active shield
      if (!this.currentState.active) {
        return;
      }

      const durationMs = Math.max(100, Date.now() - this.currentState.startTime);
      const durationSec = (durationMs / 1000).toFixed(1);

      this.currentState = {
        active: false,
        taskName: name,
        startTime: 0,
        lastOutcome: outcome,
        lastDurationMs: durationMs,
      };

      this.lastBuildSummary = `${name} ${outcome} in ${durationSec}s`;

      // Release shield in engine sidecar
      await fetch(`${this.engineUrl}/v1/flow/compile-shield`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          active: false,
          task_name: name,
          duration_ms: durationMs,
          last_outcome: outcome,
        }),
      }).catch(() => {});

      // Notify developer that build completed so they don't wander away
      if (this.vscodeApi?.window?.showInformationMessage) {
        const icon = outcome === 'success' ? '✓' : '✗';
        this.vscodeApi.window.showInformationMessage(
          `${icon} Build "${name}" finished (${durationSec}s) — ready to dive back in!`
        );
      }
    } catch {}
  }

  private startActiveCodingHeartbeat(): void {
    // Send active coding pulse every 60s
    this.reportActiveCoding();
    this.activeCodingInterval = setInterval(() => {
      this.reportActiveCoding();
    }, 60_000);
  }

  reportActiveCoding(): void {
    try {
      const workspaceFolder = this.vscodeApi?.workspace?.workspaceFolders?.[0]?.name ?? '';
      fetch(`${this.engineUrl}/v1/flow/active-coding`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          is_coding: true,
          active_workspace: workspaceFolder,
        }),
      }).catch(() => {});
    } catch {}
  }

  dispose(): void {
    if (this.activeCodingInterval) {
      clearInterval(this.activeCodingInterval);
      this.activeCodingInterval = null;
    }
    for (const d of this.disposables) {
      try { d.dispose(); } catch {}
    }
    this.disposables = [];
  }
}
