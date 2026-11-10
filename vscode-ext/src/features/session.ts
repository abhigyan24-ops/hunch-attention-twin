/**
 * session.ts — Focus Session Management and Rating Collector.
 *
 * Privacy rules (HARD):
 *   - Intention text is NEVER stored in events, logs, or emitted externally.
 *   - Only intentionLength and a salted 16-hex HMAC-SHA256 intentionHash are emitted.
 *   - Ratings are clamped to integers 1-5.
 */

import type { Clock } from '../clock.ts';
import type { Outbox } from '../link/outbox.ts';
import type { DhyanStatusBar } from '../ui/statusBar.ts';
import { hashWithSalt } from '../privacy/hash.ts';
import { PrivacyGate } from '../privacy/gate.ts';

export interface ActiveSession {
  startTime: number;
  intentionLength: number;
  intentionHash: string;
}

export interface CompletedSession {
  startTime: number;
  durationMs: number;
  intentionLength: number;
  focusRating: number;
  energyRating: number;
}

export class SessionManager {
  private clock: Clock;
  private salt: string;
  private outbox: Outbox;
  private statusBar: DhyanStatusBar | null;
  private vscodeApi: any;
  private onFlushImmediate: () => void;
  private onSessionChange?: () => void;

  private currentSession: ActiveSession | null = null;
  private sessionHistory: CompletedSession[] = [];

  constructor(options: {
    clock: Clock;
    salt: string;
    outbox: Outbox;
    statusBar: DhyanStatusBar | null;
    vscodeApi: any;
    onFlushImmediate: () => void;
    onSessionChange?: () => void;
  }) {
    this.clock = options.clock;
    this.salt = options.salt;
    this.outbox = options.outbox;
    this.statusBar = options.statusBar;
    this.vscodeApi = options.vscodeApi;
    this.onFlushImmediate = options.onFlushImmediate;
    this.onSessionChange = options.onSessionChange;
  }

  setSalt(salt: string): void {
    this.salt = salt;
  }

  isSessionActive(): boolean {
    return this.currentSession !== null;
  }

  getCurrentSession(): Readonly<ActiveSession> | null {
    return this.currentSession;
  }

  getSessionHistory(): ReadonlyArray<CompletedSession> {
    return this.sessionHistory;
  }

  clearHistory(): void {
    this.sessionHistory = [];
    this.onSessionChange?.();
  }

  /**
   * Starts a new focus session.
   * Prompts for optional intention if not provided.
   */
  async startSession(intentionInput?: string): Promise<boolean> {
    if (this.currentSession) {
      if (this.vscodeApi?.window?.showInformationMessage) {
        this.vscodeApi.window.showInformationMessage('Hunch: A focus session is already in progress.');
      }
      return false;
    }

    let rawIntention = intentionInput;
    if (rawIntention === undefined && this.vscodeApi?.window?.showInputBox) {
      rawIntention = await this.vscodeApi.window.showInputBox({
        title: 'Hunch: Start Focus Session',
        prompt: 'What is your single focus intention for this session? (Hashed locally, text is never stored):',
        placeHolder: 'e.g., Implement authentication retry logic'
      });

      if (rawIntention === undefined) {
        // User cancelled
        return false;
      }
    }

    const intention = (rawIntention || '').trim();
    const intentionLength = intention.length;
    const intentionHash = intention.length > 0 ? hashWithSalt(intention, this.salt) : '0000000000000000';
    const now = this.clock.now();

    this.currentSession = {
      startTime: now,
      intentionLength,
      intentionHash
    };

    // Emit ed_session start event
    const startEvent = {
      type: 'ed_session',
      action: 'start',
      durationMs: 0,
      intentionHash,
      intentionLength,
      focusRating: 0,
      energyRating: 0
    };

    const sanitized = PrivacyGate.sanitize(startEvent);
    if (sanitized) {
      this.outbox.enqueue(sanitized);
      this.onFlushImmediate();
    }

    // Update status bar
    if (this.statusBar) {
      this.statusBar.setFocused(0, 0);
    }

    if (this.vscodeApi?.window?.setStatusBarMessage) {
      this.vscodeApi.window.setStatusBarMessage('Hunch: Focus session started.', 3000);
    }

    this.onSessionChange?.();
    return true;
  }

  /**
   * Ends current session and collects 1-5 ratings.
   */
  async endSession(ratings?: { focusRating: number; energyRating: number }): Promise<boolean> {
    if (!this.currentSession) {
      if (this.vscodeApi?.window?.showInformationMessage) {
        this.vscodeApi.window.showInformationMessage('Hunch: No focus session currently active.');
      }
      return false;
    }

    const now = this.clock.now();
    const durationMs = Math.max(0, now - this.currentSession.startTime);
    let focusRating = ratings?.focusRating ?? 0;
    let energyRating = ratings?.energyRating ?? 0;

    // If ratings not provided, collect via QuickPick
    if ((focusRating === 0 || energyRating === 0) && this.vscodeApi?.window?.showQuickPick) {
      const focusChoice = await this.vscodeApi.window.showQuickPick(
        [
          { label: '5 - Deep Flow', description: 'Uninterrupted, fully immersed', value: 5 },
          { label: '4 - Good Focus', description: 'Minor distractions, steady progress', value: 4 },
          { label: '3 - Moderate', description: 'Normal focus with several interruptions', value: 3 },
          { label: '2 - Fragmented', description: 'Hard to maintain focus', value: 2 },
          { label: '1 - Distracted', description: 'Heavy disruption, barely focused', value: 1 }
        ],
        { title: 'Hunch: Rate your focus for this session' }
      );

      if (focusChoice) {
        focusRating = focusChoice.value;
      }

      const energyChoice = await this.vscodeApi.window.showQuickPick(
        [
          { label: '5 - High Energy', description: 'Feeling sharp and energized', value: 5 },
          { label: '4 - Alert', description: 'Good energy, clear-headed', value: 4 },
          { label: '3 - Normal', description: 'Average energy', value: 3 },
          { label: '2 - Tired', description: 'Starting to feel mental fatigue', value: 2 },
          { label: '1 - Exhausted', description: 'Depleted, need a break', value: 1 }
        ],
        { title: 'Hunch: Rate your current energy level' }
      );

      if (energyChoice) {
        energyRating = energyChoice.value;
      }
    }

    // Clamp non-zero ratings between 1 and 5 (0 means user skipped rating)
    if (focusRating !== 0) {
      focusRating = Math.max(1, Math.min(5, Math.round(focusRating)));
    }
    if (energyRating !== 0) {
      energyRating = Math.max(1, Math.min(5, Math.round(energyRating)));
    }

    const endEvent = {
      type: 'ed_session',
      action: 'end',
      durationMs,
      intentionHash: this.currentSession.intentionHash,
      intentionLength: this.currentSession.intentionLength,
      focusRating,
      energyRating
    };

    const sanitized = PrivacyGate.sanitize(endEvent);
    if (sanitized) {
      this.outbox.enqueue(sanitized);
      this.onFlushImmediate();
    }

    this.sessionHistory.push({
      startTime: this.currentSession.startTime,
      durationMs,
      intentionLength: this.currentSession.intentionLength,
      focusRating,
      energyRating
    });

    this.currentSession = null;

    if (this.statusBar && this.statusBar.getMode() === 'focused') {
      this.statusBar.setWaiting();
    }

    if (this.vscodeApi?.window?.setStatusBarMessage) {
      const mins = Math.round(durationMs / 60_000);
      this.vscodeApi.window.setStatusBarMessage(
        `Hunch: Session complete (${mins}m · Focus: ${focusRating}/5).`,
        4000
      );
    }

    this.onSessionChange?.();
    return true;
  }

  /**
   * Cancels the current session without saving duration.
   */
  cancelSession(): void {
    if (!this.currentSession) return;

    const cancelEvent = {
      type: 'ed_session',
      action: 'cancel',
      durationMs: 0,
      intentionHash: this.currentSession.intentionHash,
      intentionLength: this.currentSession.intentionLength,
      focusRating: 0,
      energyRating: 0
    };

    const sanitized = PrivacyGate.sanitize(cancelEvent);
    if (sanitized) {
      this.outbox.enqueue(sanitized);
      this.onFlushImmediate();
    }

    this.currentSession = null;
    this.onSessionChange?.();
  }

  /**
   * Toggles session state (starts if inactive, ends if active).
   */
  async toggleTimer(): Promise<boolean> {
    if (this.isSessionActive()) {
      return await this.endSession();
    } else {
      return await this.startSession();
    }
  }
}
