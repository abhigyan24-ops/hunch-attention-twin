/**
 * nudge.test.ts — Unit tests for NudgeManager.
 * Runs via: npm run test:unit
 */

import assert from 'node:assert/strict';
import { describe, it, beforeEach } from 'mocha';
import { FakeClock } from '../../src/clock.ts';
import { Outbox } from '../../src/link/outbox.ts';
import { NudgeManager } from '../../src/features/nudge.ts';

describe('NudgeManager (Phase 5 Flow Protection & Rate Limiting)', () => {
  let clock: FakeClock;
  let outbox: Outbox;
  let nudgeManager: NudgeManager;
  let mockVscode: any;
  let lastStatusMsg = '';

  beforeEach(() => {
    clock = new FakeClock(1_000_000);
    outbox = new Outbox(clock);
    lastStatusMsg = '';

    mockVscode = {
      workspace: {
        getConfiguration: () => ({
          get: (key: string, def: any) => {
            if (key === 'nudges.maxPerHour') return 4;
            if (key === 'nudges.toast') return false;
            return def;
          }
        })
      },
      window: {
        setStatusBarMessage: (msg: string) => {
          lastStatusMsg = msg;
          return { dispose: () => {} };
        },
        showInformationMessage: async () => 'Dismiss'
      }
    };

    nudgeManager = new NudgeManager({
      clock,
      outbox,
      engineClient: null,
      vscodeApi: mockVscode
    });
  });

  it('displays a nudge when conditions are clear', () => {
    const shown = nudgeManager.handleNudge({ id: 'nudge-1', text: 'Time for a break' });
    assert.equal(shown, true);
    assert.ok(lastStatusMsg.includes('Time for a break'));
  });

  it('suppresses nudges if user typed within the last 10 seconds (Rule U3)', () => {
    // Record typing 5s ago
    nudgeManager.recordTyping(clock.now());
    clock.advance(5_000);

    const shown = nudgeManager.handleNudge({ id: 'nudge-suppressed', text: 'Break time' });
    assert.equal(shown, false);

    // After 10s passes, nudge is allowed
    clock.advance(5_001);
    const shownAfter = nudgeManager.handleNudge({ id: 'nudge-ok', text: 'Break time' });
    assert.equal(shownAfter, true);
  });

  it('suppresses nudges if debugger is paused (Rule U3)', () => {
    nudgeManager.setDebugPaused(true);

    const shown = nudgeManager.handleNudge({ id: 'nudge-debug', text: 'Break time' });
    assert.equal(shown, false);

    nudgeManager.setDebugPaused(false);
    const shownAfter = nudgeManager.handleNudge({ id: 'nudge-debug-cleared', text: 'Break time' });
    assert.equal(shownAfter, true);
  });

  it('enforces maxPerHour rate limit (default 4/hr)', () => {
    // First 4 should be allowed
    for (let i = 1; i <= 4; i++) {
      clock.advance(60_000); // 1 min apart
      const shown = nudgeManager.handleNudge({ id: `nudge-${i}`, text: `Nudge ${i}` });
      assert.equal(shown, true, `Nudge ${i} should be allowed`);
    }

    // 5th nudge within the same hour should be rate-limited
    clock.advance(60_000);
    const fifth = nudgeManager.handleNudge({ id: 'nudge-5', text: 'Nudge 5' });
    assert.equal(fifth, false, '5th nudge in 1 hour must be suppressed');

    // Advance 60 minutes so the earliest nudges fall out of the window
    clock.advance(60 * 60_000);
    const afterHour = nudgeManager.handleNudge({ id: 'nudge-6', text: 'Nudge 6' });
    assert.equal(afterHour, true, 'Nudge after 1hr window should be allowed');
  });

  it('enqueues ed_feedback event when nudge is dismissed', () => {
    nudgeManager.recordTyping(clock.now()); // forces dismissal
    nudgeManager.handleNudge({ id: 'nudge-fb-1', text: 'Break' });

    const batch = outbox.getBatchAfter(0, 10);
    assert.equal(batch.length, 1);
    assert.equal(batch[0].type, 'ed_feedback');
    assert.equal((batch[0] as any).id, 'nudge-fb-1');
    assert.equal((batch[0] as any).target, 'nudge');
    assert.equal((batch[0] as any).action, 'dismiss');
  });
});
