/**
 * taskContext.ts — Pure module (NO vscode import).
 *
 * Tracks the 60-second sliding window of user activity signals to infer:
 *   - taskKey: HMAC-SHA256(workspacePath | branchName, salt).slice(0,16)
 *   - taskType: 'debugging' | 'testing' | 'coding' | 'reading' | 'idle'
 *
 * Privacy rules (HARD):
 *   - workspace paths and branch names are NEVER stored or emitted
 *   - taskKey is the sole output; it is a 16-hex-character opaque token
 */

import type { Clock } from '../clock.ts';
import { hashWithSalt } from '../privacy/hash.ts';

export type TaskType = 'debugging' | 'testing' | 'coding' | 'reading' | 'idle';

// A timestamped activity sample for the sliding window
interface Sample {
  ts: number;
  type: 'edit' | 'deletion' | 'scroll' | 'cursor' | 'taskEnd' | 'termCmd';
  category?: string;
}

const WINDOW_MS = 60_000;     // 60-second sliding window for taskType inference
const CODING_EDIT_THRESHOLD = 5;   // min edits in window to count as 'coding'
const READING_SCROLL_THRESHOLD = 3; // min scrolls in window (with few edits) to count as 'reading'

export class TaskContextTracker {
  private clock: Clock;
  private salt: string;

  // Sliding-window samples
  private samples: Sample[] = [];

  // Debug state tracking
  private debugActive = false;
  private debugPaused = false;
  private lastDebugChangeTs = 0;

  // Accumulated debug time within the window (updated on each state change)
  private debugActiveAccMs = 0;
  private debugPausedAccMs = 0;

  constructor(clock: Clock, salt: string) {
    this.clock = clock;
    this.salt = salt;
    this.lastDebugChangeTs = clock.now();
  }

  // ── Record activity signals ────────────────────────────────────────────────

  /**
   * Called for each editor edit event.
   * kind: 'typing' | 'paste' | 'bulk' | 'undo' | 'redo' | 'external'
   */
  recordEdit(kind: string, ts: number): void {
    if (kind === 'typing' || kind === 'paste' || kind === 'bulk') {
      this.samples.push({ ts, type: 'edit' });
    } else if (kind === 'deletion') {
      this.samples.push({ ts, type: 'deletion' });
    }
    this.prune(ts);
  }

  recordScroll(): void {
    const ts = this.clock.now();
    this.samples.push({ ts, type: 'scroll' });
    this.prune(ts);
  }

  recordCursorMove(): void {
    const ts = this.clock.now();
    this.samples.push({ ts, type: 'cursor' });
    this.prune(ts);
  }

  /**
   * Called when a VS Code task (build/test/lint/run) ends.
   * category: 'build' | 'test' | 'lint' | 'run' | 'other'
   */
  recordTaskEnd(category: string): void {
    const ts = this.clock.now();
    this.samples.push({ ts, type: 'taskEnd', category });
    this.prune(ts);
  }

  /**
   * Called when a terminal command is classified.
   * category: 'git' | 'build' | 'test' | 'lint' | 'run' | 'nav' | 'edit' | 'pkg' | 'other'
   */
  recordTerminalCmd(category: string): void {
    const ts = this.clock.now();
    this.samples.push({ ts, type: 'termCmd', category });
    this.prune(ts);
  }

  recordDebugActive(active: boolean): void {
    const now = this.clock.now();
    this.flushDebugTime(now);
    this.debugActive = active;
    if (!active) {
      this.debugPaused = false;
    }
    this.lastDebugChangeTs = now;
  }

  recordDebugPaused(paused: boolean): void {
    const now = this.clock.now();
    this.flushDebugTime(now);
    this.debugPaused = paused;
    this.lastDebugChangeTs = now;
  }

  // ── TaskKey computation ────────────────────────────────────────────────────

  /**
   * Computes a privacy-safe, opaque 16-hex task key from workspace path + branch name.
   * The path and branch are NEVER stored or emitted — only the hash.
   */
  computeTaskKey(workspacePath: string, branchName: string): string {
    if (!workspacePath && !branchName) {
      return '0000000000000000';
    }
    const input = `${workspacePath}|${branchName}`;
    return hashWithSalt(input, this.salt).slice(0, 16);
  }

  // ── TaskType inference ─────────────────────────────────────────────────────

  /**
   * Evaluates the sliding window and returns the current task type.
   * Priority: debugging > testing > coding > reading > idle
   */
  computeTaskType(windowMs: number = WINDOW_MS): TaskType {
    const now = this.clock.now();
    const cutoff = now - windowMs;

    // Flush current debug time before computing
    this.flushDebugTime(now);

    // Aggregate debug time — check if debug has been active for >50% of window
    if (this.debugActiveAccMs > windowMs * 0.5) {
      return 'debugging';
    }

    // Count task/terminal categories in window
    const windowSamples = this.samples.filter(s => s.ts >= cutoff);
    const taskEndCats = windowSamples
      .filter(s => s.type === 'taskEnd')
      .map(s => s.category ?? 'other');
    const termCats = windowSamples
      .filter(s => s.type === 'termCmd')
      .map(s => s.category ?? 'other');

    const testingSignals = [...taskEndCats, ...termCats].filter(
      c => c === 'test'
    ).length;
    const buildSignals = [...taskEndCats, ...termCats].filter(
      c => c === 'build' || c === 'lint'
    ).length;

    if (testingSignals >= 1) {
      return 'testing';
    }

    const editCount = windowSamples.filter(s => s.type === 'edit').length;
    const scrollCount = windowSamples.filter(s => s.type === 'scroll').length;

    if (editCount >= CODING_EDIT_THRESHOLD || buildSignals >= 1) {
      return 'coding';
    }

    if (scrollCount >= READING_SCROLL_THRESHOLD && editCount < CODING_EDIT_THRESHOLD) {
      return 'reading';
    }

    return 'idle';
  }

  // ── Internal helpers ───────────────────────────────────────────────────────

  /**
   * Flush elapsed debug time into accumulators.
   * Called before any state change and before computeTaskType.
   */
  private flushDebugTime(now: number): void {
    const elapsed = Math.max(0, now - this.lastDebugChangeTs);
    if (this.debugActive) {
      if (this.debugPaused) {
        this.debugPausedAccMs += elapsed;
      } else {
        this.debugActiveAccMs += elapsed;
      }
    }
    this.lastDebugChangeTs = now;
    // Decay: remove time that's older than the window
    this.decayDebugTime(now);
  }

  /**
   * Decay debug accumulators — only keep time within the last windowMs.
   */
  private decayDebugTime(_now: number): void {
    // Simple approach: cap at WINDOW_MS
    if (this.debugActiveAccMs > WINDOW_MS) {
      this.debugActiveAccMs = WINDOW_MS;
    }
    if (this.debugPausedAccMs > WINDOW_MS) {
      this.debugPausedAccMs = WINDOW_MS;
    }
  }

  /**
   * Remove samples older than the sliding window.
   */
  private prune(now: number): void {
    const cutoff = now - WINDOW_MS;
    let i = 0;
    while (i < this.samples.length && this.samples[i].ts < cutoff) {
      i++;
    }
    if (i > 0) {
      this.samples.splice(0, i);
    }
  }
}
