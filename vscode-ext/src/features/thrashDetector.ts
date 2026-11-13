/**
 * thrashDetector.ts — Cognitive Thrash & Rage-Scroll Pre-Distraction Shield.
 *
 * Monitors 3 signals of developer frustration:
 *   1. Repeated Build/Test Failures (>= 3 consecutive non-zero exit codes in 180s)
 *   2. Code Churn / Undo Burst (>= 12 rapid undo/delete edits in 60s)
 *   3. Frantic File Switching (>= 8 file switches across >= 4 files in 45s without saving)
 *
 * When triggered:
 *   - Activates engine Rage-Scroll Shield to hold notifications & browser drift.
 *   - Surfaces a calm rubber-duck micro-prompt in VS Code.
 */

export interface TaskFailureEvent {
  timestamp: number;
  taskName: string;
  exitCode: number;
}

export interface EditChurnEvent {
  timestamp: number;
  isUndoOrDelete: boolean;
}

export interface FileSwitchEvent {
  timestamp: number;
  filePath: string;
}

export interface ThrashEvaluation {
  isThrashing: boolean;
  trigger?: 'repeated_test_failures' | 'undo_churn_burst' | 'frantic_file_switching';
  reason?: string;
}

export function evaluateThrash(
  taskFailures: TaskFailureEvent[],
  editChurn: EditChurnEvent[],
  fileSwitches: FileSwitchEvent[],
  now: number = Date.now()
): ThrashEvaluation {
  // 1. Repeated build/test failures: >= 3 consecutive failures within 180s
  const recentFailures = taskFailures.filter(f => now - f.timestamp <= 180_000);
  if (recentFailures.length >= 3) {
    const last3 = recentFailures.slice(-3);
    if (last3.every(f => f.exitCode !== 0)) {
      return {
        isThrashing: true,
        trigger: 'repeated_test_failures',
        reason: `${last3.length} consecutive task/test runs failed in the last 3m`,
      };
    }
  }

  // 2. Rapid Undo/Delete Churn: >= 12 undo or delete-heavy edits within 60s
  const recentChurn = editChurn.filter(e => e.isUndoOrDelete && now - e.timestamp <= 60_000);
  if (recentChurn.length >= 12) {
    return {
      isThrashing: true,
      trigger: 'undo_churn_burst',
      reason: `${recentChurn.length} undo/delete edits in 60s (rapid code rollback)`,
    };
  }

  // 3. Frantic File Switching: >= 8 switches across >= 4 distinct files in 45s
  const recentSwitches = fileSwitches.filter(s => now - s.timestamp <= 45_000);
  if (recentSwitches.length >= 8) {
    const distinctFiles = new Set(recentSwitches.map(s => s.filePath));
    if (distinctFiles.size >= 4) {
      return {
        isThrashing: true,
        trigger: 'frantic_file_switching',
        reason: `${recentSwitches.length} tab switches across ${distinctFiles.size} files in 45s`,
      };
    }
  }

  return { isThrashing: false };
}

export class ThrashDetector {
  private vscodeApi: any;
  private engineUrl: string;
  private disposables: Array<{ dispose: () => void }> = [];

  private taskFailures: TaskFailureEvent[] = [];
  private editChurn: EditChurnEvent[] = [];
  private fileSwitches: FileSwitchEvent[] = [];

  private lastAlertTimestamp = 0;
  private alertCooldownMs = 10 * 60_000; // 10 minutes cooldown

  constructor(vscodeApi: any, engineUrl = 'http://127.0.0.1:8765') {
    this.vscodeApi = vscodeApi;
    this.engineUrl = engineUrl;

    this.registerListeners();
  }

  recordTaskEnd(taskName: string, exitCode: number): void {
    if (exitCode !== 0) {
      this.taskFailures.push({ timestamp: Date.now(), taskName, exitCode });
      this.checkAndTrigger();
    } else {
      // Clear failure streak on successful run
      this.taskFailures = [];
    }
  }

  recordEdit(isUndoOrDelete: boolean): void {
    if (isUndoOrDelete) {
      this.editChurn.push({ timestamp: Date.now(), isUndoOrDelete: true });
      this.checkAndTrigger();
    }
  }

  recordFileSwitch(filePath: string): void {
    if (filePath) {
      this.fileSwitches.push({ timestamp: Date.now(), filePath });
      this.checkAndTrigger();
    }
  }

  private checkAndTrigger(): void {
    const now = Date.now();
    // Prune events older than 180s
    this.taskFailures = this.taskFailures.filter(f => now - f.timestamp <= 180_000);
    this.editChurn = this.editChurn.filter(e => now - e.timestamp <= 60_000);
    this.fileSwitches = this.fileSwitches.filter(s => now - s.timestamp <= 45_000);

    const evaluation = evaluateThrash(this.taskFailures, this.editChurn, this.fileSwitches, now);
    if (evaluation.isThrashing && now - this.lastAlertTimestamp >= this.alertCooldownMs) {
      this.lastAlertTimestamp = now;
      this.triggerShield(evaluation);
    }
  }

  private async triggerShield(evaluation: ThrashEvaluation): Promise<void> {
    try {
      // 1. Post to engine sidecar
      await fetch(`${this.engineUrl}/v1/flow/thrash-shield`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          active: true,
          trigger: evaluation.trigger,
          reason: evaluation.reason,
          expires_in_seconds: 600,
        }),
      }).catch(() => {});

      // 2. Surface calm rubber-duck prompt
      if (this.vscodeApi?.window?.showInformationMessage) {
        const action = await this.vscodeApi.window.showInformationMessage(
          'Hunch: Tricky bug detected — Rage-Scroll Shield activated for 10m. Want to jot a 1-line hypothesis?',
          'Jot Hypothesis',
          'Take 2m Breather'
        );

        if (action === 'Jot Hypothesis' && this.vscodeApi?.window?.showInputBox) {
          const note = await this.vscodeApi.window.showInputBox({
            title: 'Hunch Rubber-Duck Hypothesis',
            prompt: 'What assumption might be failing?',
            placeHolder: 'e.g., token expiry header format might be Unix seconds vs ms',
          });

          if (note && note.trim()) {
            // Park intention snapshot
            await fetch(`${this.engineUrl}/v1/insights/context-snapshot`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                snapshot_id: `thrash_${Date.now()}`,
                user_note: `Hypothesis: ${note.trim()}`,
                created_at_ts: Date.now(),
              }),
            }).catch(() => {});

            if (this.vscodeApi?.window?.setStatusBarMessage) {
              this.vscodeApi.window.setStatusBarMessage('✓ Hypothesis parked for debugging.', 3000);
            }
          }
        }
      }
    } catch {}
  }

  private registerListeners(): void {
    try {
      if (this.vscodeApi?.tasks?.onDidEndTaskProcess) {
        const sub = this.vscodeApi.tasks.onDidEndTaskProcess((event: any) => {
          const name = event?.execution?.task?.name || 'build';
          const exitCode = event?.exitCode ?? 0;
          this.recordTaskEnd(name, exitCode);
        });
        this.disposables.push(sub);
      }

      if (this.vscodeApi?.window?.onDidChangeActiveTextEditor) {
        const sub = this.vscodeApi.window.onDidChangeActiveTextEditor((editor: any) => {
          const path = editor?.document?.uri?.fsPath || editor?.document?.uri?.path || '';
          if (path) {
            this.recordFileSwitch(path);
          }
        });
        this.disposables.push(sub);
      }

      if (this.vscodeApi?.workspace?.onDidChangeTextDocument) {
        const sub = this.vscodeApi.workspace.onDidChangeTextDocument((event: any) => {
          const changes = event?.contentChanges || [];
          for (const c of changes) {
            // Check for delete / backspace (replaced text with empty) or rapid undo
            const isDelete = (c.rangeLength > 0 && c.text.length === 0);
            if (isDelete) {
              this.recordEdit(true);
            }
          }
        });
        this.disposables.push(sub);
      }
    } catch {}
  }

  dispose(): void {
    for (const d of this.disposables) {
      try { d.dispose(); } catch {}
    }
    this.disposables = [];
  }
}
