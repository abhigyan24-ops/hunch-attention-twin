/**
 * breaks.ts — Pure module (NO vscode import).
 *
 * Detects natural breakpoints in coding activity and emits BreakEvent objects
 * with a kind and confidence score.
 *
 * Break kinds and confidence:
 *   save_green        - save + no new errors + 15s quiet → 0.7
 *   test_ok           - test task exit code 0              → 0.8
 *   build_ok          - build task exit code 0             → 0.8
 *   commit            - git HEAD SHA changed               → 0.9
 *   branch_switch     - git branch changed                 → 0.9
 *   debug_end         - debug session stopped              → 0.7
 *   file_closed_clean - file closed with diagErr 0         → 0.6
 *   idle_after_save   - 20s no edits after save + err=0    → 0.6
 */

import type { Clock } from '../clock.ts';

export type BreakKind =
  | 'save_green'
  | 'test_ok'
  | 'build_ok'
  | 'commit'
  | 'branch_switch'
  | 'debug_end'
  | 'file_closed_clean'
  | 'idle_after_save';

export interface BreakEvent {
  kind: BreakKind;
  confidence: number;
}

// Idle thresholds
const SAVE_GREEN_QUIET_MS = 15_000;   // 15s quiet after save
const IDLE_AFTER_SAVE_MS  = 20_000;   // 20s no edits after save

export class BreakDetector {
  private clock: Clock;

  // State
  private lastSaveTs = 0;
  private errorsAtSave = 0;
  private currentErrors = 0;
  private lastEditTs = 0;
  private lastGitHead: string | null = null;
  private lastBranch: string | null = null;

  // Pending break signals (emitted on the next tick or immediately)
  private pendingBreaks: BreakEvent[] = [];

  constructor(clock: Clock) {
    this.clock = clock;
    this.lastEditTs = clock.now();
  }

  // ── Event handlers ─────────────────────────────────────────────────────────

  onSave(): void {
    this.lastSaveTs = this.clock.now();
    this.errorsAtSave = this.currentErrors;
  }

  onEdit(): void {
    this.lastEditTs = this.clock.now();
  }

  onDiagnosticsChange(err: number, _warn: number): void {
    this.currentErrors = err;
  }

  onTaskEnd(category: string, exitCode: number): void {
    if (exitCode !== 0) return;

    if (category === 'test') {
      this.pendingBreaks.push({ kind: 'test_ok', confidence: 0.8 });
    } else if (category === 'build' || category === 'lint') {
      this.pendingBreaks.push({ kind: 'build_ok', confidence: 0.8 });
    }
  }

  onGitHeadChange(newHead: string): void {
    if (this.lastGitHead !== null && this.lastGitHead !== newHead) {
      this.pendingBreaks.push({ kind: 'commit', confidence: 0.9 });
    }
    this.lastGitHead = newHead;
  }

  onBranchChange(newBranch: string): void {
    if (this.lastBranch !== null && this.lastBranch !== newBranch) {
      this.pendingBreaks.push({ kind: 'branch_switch', confidence: 0.9 });
    }
    this.lastBranch = newBranch;
  }

  onDebugStop(): void {
    this.pendingBreaks.push({ kind: 'debug_end', confidence: 0.7 });
  }

  onFileClose(hasErrors: boolean): void {
    if (!hasErrors) {
      this.pendingBreaks.push({ kind: 'file_closed_clean', confidence: 0.6 });
    }
  }

  // ── Tick-based detection ──────────────────────────────────────────────────

  /**
   * Called periodically (e.g., every second or on a coarse timer).
   * Returns the first detected BreakEvent, or null if none.
   * Each detected event is returned exactly once.
   */
  tick(nowMs: number): BreakEvent | null {
    // 1. Drain pending breaks (git commits, task ends, debug stops, file closes)
    if (this.pendingBreaks.length > 0) {
      return this.pendingBreaks.shift()!;
    }

    // 2. save_green: saved with 0 errors, 15s quiet (no edits AND no new errors since save)
    //    Requires: errorsAtSave=0, currentErrors=0, 15s elapsed, AND no edits since save.
    //    save_green takes priority. idle_after_save only fires when save_green did not.
    if (
      this.lastSaveTs > 0 &&
      this.errorsAtSave === 0 &&
      this.currentErrors === 0 &&
      nowMs - this.lastSaveTs >= SAVE_GREEN_QUIET_MS &&
      this.lastEditTs <= this.lastSaveTs
    ) {
      // Mark as consumed by resetting lastSaveTs to avoid re-triggering
      this.lastSaveTs = 0;
      return { kind: 'save_green', confidence: 0.7 };
    }

    // 3. idle_after_save: 20s no edits after a save with errors now resolved.
    //    Fires when save_green did NOT trigger (e.g., errorsAtSave > 0 blocked it),
    //    currentErrors are now 0, no edits since save, and 20s have elapsed.
    if (
      this.lastSaveTs > 0 &&
      this.currentErrors === 0 &&
      this.lastEditTs <= this.lastSaveTs &&
      nowMs - this.lastSaveTs >= IDLE_AFTER_SAVE_MS
    ) {
      this.lastSaveTs = 0;
      return { kind: 'idle_after_save', confidence: 0.6 };
    }

    return null;
  }

  /**
   * Returns all pending break events and clears the queue.
   * Used for immediate (non-tick) emission.
   */
  drainPending(): BreakEvent[] {
    const events = this.pendingBreaks.slice();
    this.pendingBreaks = [];
    return events;
  }
}
