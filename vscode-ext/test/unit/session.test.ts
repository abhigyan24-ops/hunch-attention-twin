/**
 * session.test.ts — Unit tests for SessionManager.
 * Runs via: npm run test:unit
 */

import assert from 'node:assert/strict';
import { describe, it, beforeEach } from 'mocha';
import { FakeClock } from '../../src/clock.ts';
import { Outbox } from '../../src/link/outbox.ts';
import { SessionManager } from '../../src/features/session.ts';

describe('SessionManager (Phase 5 Sessions & Ratings)', () => {
  let clock: FakeClock;
  let outbox: Outbox;
  let flushedCount: number;
  let sessionManager: SessionManager;
  let mockVscode: any;

  beforeEach(() => {
    clock = new FakeClock(1_000_000);
    outbox = new Outbox(clock);
    flushedCount = 0;
    mockVscode = {
      window: {
        showInputBox: async () => 'Refactor auth',
        showQuickPick: async () => ({ value: 4 }),
        showInformationMessage: () => {},
        setStatusBarMessage: () => ({ dispose: () => {} })
      }
    };

    sessionManager = new SessionManager({
      clock,
      salt: 'test_salt_1234567890abcdef',
      outbox,
      statusBar: null,
      vscodeApi: mockVscode,
      onFlushImmediate: () => {
        flushedCount++;
      }
    });
  });

  it('starts a session, hashes intention, and enqueues start event', async () => {
    const started = await sessionManager.startSession('Fix bug in auth retry');
    assert.equal(started, true);
    assert.equal(sessionManager.isSessionActive(), true);

    const current = sessionManager.getCurrentSession();
    assert.ok(current !== null);
    assert.equal(current!.intentionLength, 'Fix bug in auth retry'.length);
    assert.equal(current!.intentionHash.length, 16);
    assert.equal(flushedCount, 1);

    // Verify outbox event
    const batch = outbox.getBatchAfter(0, 10);
    assert.equal(batch.length, 1);
    assert.equal(batch[0].type, 'ed_session');
    assert.equal(batch[0].action, 'start');
    assert.equal(batch[0].durationMs, 0);
    assert.equal(batch[0].intentionLength, 'Fix bug in auth retry'.length);
    // Hard rule P1: Intention text is NEVER stored in event!
    assert.equal((batch[0] as any).intention, undefined);
    assert.equal((batch[0] as any).text, undefined);
  });

  it('prevents starting a second session when one is already running', async () => {
    await sessionManager.startSession('First session');
    const second = await sessionManager.startSession('Second session');
    assert.equal(second, false);
    assert.equal(sessionManager.isSessionActive(), true);
  });

  it('ends a session, calculates duration, clamps 1-5 ratings, and enqueues end event', async () => {
    await sessionManager.startSession('Coding session');
    // Advance clock by 25 minutes
    clock.advance(25 * 60_000);

    const ended = await sessionManager.endSession({ focusRating: 5, energyRating: 4 });
    assert.equal(ended, true);
    assert.equal(sessionManager.isSessionActive(), false);
    assert.equal(flushedCount, 2);

    const batch = outbox.getBatchAfter(0, 10);
    assert.equal(batch.length, 2);
    const endEvent: any = batch[1];
    assert.equal(endEvent.type, 'ed_session');
    assert.equal(endEvent.action, 'end');
    assert.equal(endEvent.durationMs, 25 * 60_000);
    assert.equal(endEvent.focusRating, 5);
    assert.equal(endEvent.energyRating, 4);

    // Verify session history
    const history = sessionManager.getSessionHistory();
    assert.equal(history.length, 1);
    assert.equal(history[0].durationMs, 25 * 60_000);
    assert.equal(history[0].focusRating, 5);
  });

  it('clamps ratings below 1 to 1 and above 5 to 5', async () => {
    await sessionManager.startSession('Clamping test');
    clock.advance(10_000);

    await sessionManager.endSession({ focusRating: 10, energyRating: -2 });
    const batch = outbox.getBatchAfter(0, 10);
    const endEvent: any = batch[1];
    assert.equal(endEvent.focusRating, 5);
    assert.equal(endEvent.energyRating, 1);
  });

  it('cancels an active session cleanly without saving history', async () => {
    await sessionManager.startSession('To be cancelled');
    sessionManager.cancelSession();

    assert.equal(sessionManager.isSessionActive(), false);
    assert.equal(sessionManager.getSessionHistory().length, 0);

    const batch = outbox.getBatchAfter(0, 10);
    assert.equal(batch.length, 2);
    assert.equal(batch[1].type, 'ed_session');
    assert.equal(batch[1].action, 'cancel');
  });

  it('toggles session state with toggleTimer', async () => {
    assert.equal(sessionManager.isSessionActive(), false);
    await sessionManager.toggleTimer();
    assert.equal(sessionManager.isSessionActive(), true);
    await sessionManager.toggleTimer();
    assert.equal(sessionManager.isSessionActive(), false);
  });
});
