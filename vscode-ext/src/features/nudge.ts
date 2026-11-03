/**
 * nudge.ts — Nudge Manager with Rate Limiting and Flow Protection.
 *
 * UX & Safety Rules (HARD):
 *   - Rule U1 / U3: Nudges are quiet, rate-limited, and auto-dismiss.
 *   - Suppressed if user typed within the last 10 seconds.
 *   - Suppressed if debugger is currently paused.
 *   - Rate limited to maxPerHour (default 4/hr) via a sliding 1-hour window.
 */

import type { Clock } from '../clock.ts';
import type { Outbox } from '../link/outbox.ts';
import type { EngineClient } from '../link/client.ts';
import { PrivacyGate } from '../privacy/gate.ts';

export interface NudgePayload {
  id: string;
  kindName?: string;
  text?: string;
}

export class NudgeManager {
  private clock: Clock;
  private outbox: Outbox;
  private engineClient: EngineClient | null;
  private vscodeApi: any;

  // Rate limiting timestamps (within the last 1 hour)
  private nudgeTimestamps: number[] = [];
  private lastTypingTs: number = 0;
  private debugPaused: boolean = false;

  private activeNudgeTimeout: ReturnType<typeof setTimeout> | null = null;

  constructor(options: {
    clock: Clock;
    outbox: Outbox;
    engineClient: EngineClient | null;
    vscodeApi: any;
  }) {
    this.clock = options.clock;
    this.outbox = options.outbox;
    this.engineClient = options.engineClient;
    this.vscodeApi = options.vscodeApi;
  }

  setEngineClient(client: EngineClient | null): void {
    this.engineClient = client;
  }

  recordTyping(nowMs?: number): void {
    this.lastTypingTs = nowMs ?? this.clock.now();
  }

  setDebugPaused(paused: boolean): void {
    this.debugPaused = paused;
  }

  /**
   * Evaluates and presents a nudge received from the engine.
   * Returns true if displayed, false if suppressed by rate limit or typing.
   */
  handleNudge(nudge: NudgePayload): boolean {
    const now = this.clock.now();

    // 1. Suppression rule: Typing in last 10 seconds
    if (now - this.lastTypingTs < 10_000) {
      this.sendFeedback(nudge.id, 'dismiss');
      return false;
    }

    // 2. Suppression rule: Debugger paused
    if (this.debugPaused) {
      this.sendFeedback(nudge.id, 'dismiss');
      return false;
    }

    // 3. Rate limiting: Sliding 1-hour window
    const oneHourAgo = now - 3_600_000;
    this.nudgeTimestamps = this.nudgeTimestamps.filter(t => t >= oneHourAgo);

    const config = this.vscodeApi?.workspace?.getConfiguration?.('dhyan');
    const maxPerHour = Number(config?.get('nudges.maxPerHour', 4)) || 4;

    if (this.nudgeTimestamps.length >= maxPerHour) {
      this.sendFeedback(nudge.id, 'dismiss');
      return false;
    }

    // Accept and record nudge
    this.nudgeTimestamps.push(now);
    this.presentNudge(nudge);
    return true;
  }

  private presentNudge(nudge: NudgePayload): void {
    const text = nudge.text || 'Take a quick natural breath or break.';
    const config = this.vscodeApi?.workspace?.getConfiguration?.('dhyan');
    const allowToast = Boolean(config?.get('nudges.toast', false));

    if (allowToast && this.vscodeApi?.window?.showInformationMessage) {
      // Toast presentation with action buttons
      this.vscodeApi.window
        .showInformationMessage(`Dhyan: ${text}`, 'Take Break', 'Dismiss')
        .then((choice: string) => {
          if (choice === 'Take Break') {
            this.sendFeedback(nudge.id, 'click');
          } else {
            this.sendFeedback(nudge.id, 'dismiss');
          }
        });
    } else if (this.vscodeApi?.window?.setStatusBarMessage) {
      // Default quiet status bar presentation with auto-dismiss after 10s
      if (this.activeNudgeTimeout) {
        clearTimeout(this.activeNudgeTimeout);
      }
      const messageDisposable = this.vscodeApi.window.setStatusBarMessage(
        `$(bell) Dhyan: ${text}`,
        10_000
      );

      this.activeNudgeTimeout = setTimeout(() => {
        messageDisposable?.dispose?.();
        this.activeNudgeTimeout = null;
      }, 10_000);
    }
  }

  private sendFeedback(id: string, action: 'useful' | 'not_useful' | 'dismiss' | 'click'): void {
    // 1. Send via EngineClient WebSocket protocol if connected
    this.engineClient?.sendFeedback(id, 'nudge', action);

    // 2. Enqueue ed_feedback event into Outbox
    const feedbackEvent = {
      type: 'ed_feedback',
      id: id.slice(0, 64),
      target: 'nudge',
      action
    };

    const sanitized = PrivacyGate.sanitize(feedbackEvent);
    if (sanitized) {
      this.outbox.enqueue(sanitized);
    }
  }

  dispose(): void {
    if (this.activeNudgeTimeout) {
      clearTimeout(this.activeNudgeTimeout);
      this.activeNudgeTimeout = null;
    }
    this.nudgeTimestamps = [];
  }
}
