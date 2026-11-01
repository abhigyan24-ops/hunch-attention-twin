/**
 * resumeTracker.ts — Pure module (NO vscode import).
 *
 * Implements randomized resume assist decision logic and censored resumption
 * lag tracking.
 *
 * Invariants:
 *   - Clamped propensity: p = clamp(assistProbability, 0.05, 0.95)
 *   - Only triggers assist experiment when awayMs >= minAwayMinutes * 60,000.
 *   - Measures resumption: 12 typing edits within 30s in the same taskKey.
 *   - Censoring cutoff: 30 minutes (1,800,000 ms). If unresumed after 30 min,
 *     emits `resumed: false, lagMs: 1800000`.
 *   - Deterministic testing with injected Clock and RNG.
 */

import type { Clock } from '../clock.ts';

export interface ResumeLoadContext {
  dirtyDocs: number;
  openTabs: number;
  debugPaused: boolean;
  uncommitted: number;
  taskType: 'debugging' | 'testing' | 'coding' | 'reading' | 'idle';
}

export interface ResumeEvent {
  type: 'ed_resume';
  awayMs: number;
  assist: boolean;
  propensity: number;
  experiment: boolean;
  used: boolean;
  lagMs: number;
  resumed: boolean;
  taskKey: string;
  load: ResumeLoadContext;
}

export interface ResumeCandidate {
  refocusTs: number;
  awayMs: number;
  taskKey: string;
  assist: boolean;
  propensity: number;
  experiment: boolean;
  used: boolean;
  load: ResumeLoadContext;
  typingEditTimestamps: number[];
  firstEditTs: number | null;
}

export const CENSORING_CUTOFF_MS = 30 * 60_000; // 30 minutes (1,800,000 ms)
export const RESUMPTION_TYPING_COUNT = 12;      // 12 typing edits
export const RESUMPTION_WINDOW_MS = 30_000;     // within 30 seconds

export class ResumeTracker {
  private clock: Clock;
  private rng: () => number;

  private isAway = false;
  private blurTs = 0;
  private currentCandidate: ResumeCandidate | null = null;

  constructor(clock: Clock, rngOverride?: () => number) {
    this.clock = clock;
    this.rng = rngOverride ?? Math.random;
  }

  /**
   * Called when the editor loses focus (window blur or inactive).
   */
  onBlur(nowMs?: number): void {
    this.blurTs = nowMs ?? this.clock.now();
    this.isAway = true;
  }

  /**
   * Called when the editor regains focus.
   * If absence >= minAwayMinutes, sets up a randomized resume assist candidate.
   * Returns candidate information if an assist intervention should be presented.
   */
  onFocus(
    context: { taskKey: string; load: ResumeLoadContext },
    minAwayMinutes = 3,
    assistProbability = 0.8,
    nowMs?: number
  ): { assist: boolean; awayMs: number; taskKey: string } | null {
    if (!this.isAway) {
      return null;
    }

    const refocusTs = nowMs ?? this.clock.now();
    const awayMs = Math.max(0, refocusTs - this.blurTs);
    this.isAway = false;

    const minAwayMs = minAwayMinutes * 60_000;
    if (awayMs < minAwayMs) {
      // Absence too short — not an interruption requiring assist or experiment
      return null;
    }

    // If a previous candidate is still running, check if it should be censored
    if (this.currentCandidate) {
      if (refocusTs - this.currentCandidate.refocusTs >= CENSORING_CUTOFF_MS) {
        // Will be flushed on next tick or edit
      }
    }

    // Clamp propensity p = clamp(assistProbability, 0.05, 0.95)
    const propensity = Math.max(0.05, Math.min(0.95, assistProbability));
    const assist = this.rng() < propensity;

    this.currentCandidate = {
      refocusTs,
      awayMs,
      taskKey: context.taskKey,
      assist,
      propensity,
      experiment: true,
      used: false,
      load: { ...context.load },
      typingEditTimestamps: [],
      firstEditTs: null
    };

    return {
      assist,
      awayMs,
      taskKey: context.taskKey
    };
  }

  /**
   * Marks that the user actively used / clicked the resume assist mechanism.
   */
  onAssistUsed(): void {
    if (this.currentCandidate) {
      this.currentCandidate.used = true;
    }
  }

  /**
   * Called for each editor edit. Evaluates resumption criteria:
   * 12 typing edits within 30s in the same taskKey.
   * Returns an `ed_resume` event when resumption occurs or censoring is reached.
   */
  onEdit(kind: string, taskKey: string, nowMs?: number): ResumeEvent | null {
    if (!this.currentCandidate) {
      return null;
    }

    const now = nowMs ?? this.clock.now();

    // 1. Check censoring (30 minutes passed since refocus without resumption)
    if (now - this.currentCandidate.refocusTs >= CENSORING_CUTOFF_MS) {
      return this.emitCensored();
    }

    // 2. Only 'typing' edits count towards resumption
    if (kind !== 'typing') {
      return null;
    }

    // 3. Must be in the same taskKey
    if (taskKey !== this.currentCandidate.taskKey) {
      return null;
    }

    // Record the first edit timestamp
    if (this.currentCandidate.firstEditTs === null) {
      this.currentCandidate.firstEditTs = now;
    }

    // Append typing timestamp and prune to sliding 30s window
    this.currentCandidate.typingEditTimestamps.push(now);
    const windowStart = now - RESUMPTION_WINDOW_MS;
    this.currentCandidate.typingEditTimestamps = this.currentCandidate.typingEditTimestamps.filter(
      ts => ts >= windowStart
    );

    // 4. Check if cluster threshold is met (12 typing edits within 30s)
    if (this.currentCandidate.typingEditTimestamps.length >= RESUMPTION_TYPING_COUNT) {
      const firstEditInCluster = this.currentCandidate.typingEditTimestamps[0];
      const lagMs = Math.max(0, firstEditInCluster - this.currentCandidate.refocusTs);

      const event: ResumeEvent = {
        type: 'ed_resume',
        awayMs: this.currentCandidate.awayMs,
        assist: this.currentCandidate.assist,
        propensity: this.currentCandidate.propensity,
        experiment: this.currentCandidate.experiment,
        used: this.currentCandidate.used,
        lagMs,
        resumed: true,
        taskKey: this.currentCandidate.taskKey,
        load: this.currentCandidate.load
      };

      this.currentCandidate = null;
      return event;
    }

    return null;
  }

  /**
   * Periodic tick (e.g. called every few seconds). Checks for 30-min censoring cutoff.
   */
  tick(nowMs?: number): ResumeEvent | null {
    if (!this.currentCandidate) {
      return null;
    }

    const now = nowMs ?? this.clock.now();
    if (now - this.currentCandidate.refocusTs >= CENSORING_CUTOFF_MS) {
      return this.emitCensored();
    }

    return null;
  }

  /**
   * Returns current active candidate if any (for diagnostics or UI).
   */
  getCurrentCandidate(): Readonly<ResumeCandidate> | null {
    return this.currentCandidate;
  }

  /**
   * Emits a censored resumption event (resumed: false, lagMs: 1800000).
   */
  private emitCensored(): ResumeEvent {
    const cand = this.currentCandidate!;
    this.currentCandidate = null;

    return {
      type: 'ed_resume',
      awayMs: cand.awayMs,
      assist: cand.assist,
      propensity: cand.propensity,
      experiment: cand.experiment,
      used: cand.used,
      lagMs: CENSORING_CUTOFF_MS,
      resumed: false,
      taskKey: cand.taskKey,
      load: cand.load
    };
  }
}
