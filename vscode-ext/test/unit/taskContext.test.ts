/**
 * taskContext.test.ts — Unit tests for TaskContextTracker.
 * Runs via: npm run test:unit
 */

import assert from 'node:assert/strict';
import { describe, it, beforeEach } from 'mocha';
import { FakeClock } from '../../src/clock.ts';
import { TaskContextTracker } from '../../src/aggregate/taskContext.ts';

const FIXED_SALT = 'a'.repeat(64); // deterministic 64-hex salt

describe('TaskContextTracker', () => {
  let clock: FakeClock;
  let tracker: TaskContextTracker;

  beforeEach(() => {
    clock = new FakeClock(1_000_000);
    tracker = new TaskContextTracker(clock, FIXED_SALT);
  });

  // ── computeTaskKey ──────────────────────────────────────────────────────────

  describe('computeTaskKey', () => {
    it('returns a 16-hex-character string', () => {
      const key = tracker.computeTaskKey('/home/user/project', 'main');
      assert.ok(typeof key === 'string', 'key must be a string');
      assert.equal(key.length, 16, 'key must be exactly 16 characters');
      assert.ok(/^[0-9a-f]{16}$/.test(key), 'key must be lowercase hex');
    });

    it('produces the same key for the same inputs', () => {
      const k1 = tracker.computeTaskKey('/home/user/project', 'main');
      const k2 = tracker.computeTaskKey('/home/user/project', 'main');
      assert.equal(k1, k2);
    });

    it('produces different keys for different workspace paths', () => {
      const k1 = tracker.computeTaskKey('/home/user/alpha', 'main');
      const k2 = tracker.computeTaskKey('/home/user/beta', 'main');
      assert.notEqual(k1, k2);
    });

    it('produces different keys for different branches', () => {
      const k1 = tracker.computeTaskKey('/home/user/project', 'main');
      const k2 = tracker.computeTaskKey('/home/user/project', 'feature/new');
      assert.notEqual(k1, k2);
    });

    it('produces different keys for different salts', () => {
      const tracker2 = new TaskContextTracker(clock, 'b'.repeat(64));
      const k1 = tracker.computeTaskKey('/home/user/project', 'main');
      const k2 = tracker2.computeTaskKey('/home/user/project', 'main');
      assert.notEqual(k1, k2);
    });

    it('returns 0000000000000000 for empty inputs', () => {
      const key = tracker.computeTaskKey('', '');
      assert.equal(key, '0000000000000000');
    });
  });

  // ── computeTaskType: idle ───────────────────────────────────────────────────

  describe('computeTaskType — idle', () => {
    it('returns idle with no signals', () => {
      assert.equal(tracker.computeTaskType(), 'idle');
    });

    it('returns idle with only cursor moves (below scroll threshold)', () => {
      tracker.recordCursorMove();
      tracker.recordCursorMove();
      assert.equal(tracker.computeTaskType(), 'idle');
    });
  });

  // ── computeTaskType: coding ─────────────────────────────────────────────────

  describe('computeTaskType — coding', () => {
    it('returns coding when 5+ edit signals are in the window', () => {
      const now = clock.now();
      for (let i = 0; i < 5; i++) {
        tracker.recordEdit('typing', now + i * 100);
      }
      assert.equal(tracker.computeTaskType(), 'coding');
    });

    it('does NOT return coding with only 4 edits', () => {
      const now = clock.now();
      for (let i = 0; i < 4; i++) {
        tracker.recordEdit('typing', now + i * 100);
      }
      // Should be idle (not enough edits)
      assert.equal(tracker.computeTaskType(), 'idle');
    });

    it('counts paste and bulk as edit signals', () => {
      const now = clock.now();
      tracker.recordEdit('typing', now);
      tracker.recordEdit('typing', now + 100);
      tracker.recordEdit('paste', now + 200);
      tracker.recordEdit('bulk', now + 300);
      tracker.recordEdit('typing', now + 400);
      assert.equal(tracker.computeTaskType(), 'coding');
    });

    it('does not count undo/redo as coding edits', () => {
      const now = clock.now();
      for (let i = 0; i < 10; i++) {
        tracker.recordEdit('undo', now + i * 100);
      }
      assert.equal(tracker.computeTaskType(), 'idle');
    });
  });

  // ── computeTaskType: reading ────────────────────────────────────────────────

  describe('computeTaskType — reading', () => {
    it('returns reading with 3+ scrolls and fewer than 5 edits', () => {
      tracker.recordScroll();
      tracker.recordScroll();
      tracker.recordScroll();
      assert.equal(tracker.computeTaskType(), 'reading');
    });

    it('returns coding (not reading) when edits >= 5 even if scrolls >= 3', () => {
      const now = clock.now();
      tracker.recordScroll();
      tracker.recordScroll();
      tracker.recordScroll();
      for (let i = 0; i < 5; i++) {
        tracker.recordEdit('typing', now + i * 100);
      }
      assert.equal(tracker.computeTaskType(), 'coding');
    });
  });

  // ── computeTaskType: testing ────────────────────────────────────────────────

  describe('computeTaskType — testing', () => {
    it('returns testing when a test task ends', () => {
      tracker.recordTaskEnd('test');
      assert.equal(tracker.computeTaskType(), 'testing');
    });

    it('returns testing when a test terminal command is recorded', () => {
      tracker.recordTerminalCmd('test');
      assert.equal(tracker.computeTaskType(), 'testing');
    });

    it('testing overrides coding signals', () => {
      const now = clock.now();
      for (let i = 0; i < 5; i++) {
        tracker.recordEdit('typing', now + i * 100);
      }
      tracker.recordTaskEnd('test');
      assert.equal(tracker.computeTaskType(), 'testing');
    });
  });

  // ── computeTaskType: debugging ──────────────────────────────────────────────

  describe('computeTaskType — debugging', () => {
    it('returns debugging when debug is active for >50% of window', () => {
      // Mark debug active
      tracker.recordDebugActive(true);
      // Advance clock by 31 seconds (>50% of 60s window)
      clock.advance(31_000);
      assert.equal(tracker.computeTaskType(), 'debugging');
    });

    it('does not return debugging if debug is active for <50% of window', () => {
      tracker.recordDebugActive(true);
      // Advance only 20 seconds (<50% of 60s window)
      clock.advance(20_000);
      // Then stop debug
      tracker.recordDebugActive(false);
      // Now 20s of debug < 30s threshold — should not be 'debugging'
      const type = tracker.computeTaskType();
      assert.notEqual(type, 'debugging');
    });

    it('debugging overrides testing', () => {
      tracker.recordTaskEnd('test');
      tracker.recordDebugActive(true);
      clock.advance(31_000);
      assert.equal(tracker.computeTaskType(), 'debugging');
    });
  });

  // ── Sliding window pruning ──────────────────────────────────────────────────

  describe('Sliding window pruning', () => {
    it('discards signals older than 60 seconds', () => {
      // Record 5 edits at t=0 (makes it 'coding')
      const now = clock.now();
      for (let i = 0; i < 5; i++) {
        tracker.recordEdit('typing', now + i * 100);
      }
      assert.equal(tracker.computeTaskType(), 'coding');

      // Advance clock 61 seconds — old signals fall out of window
      clock.advance(61_000);

      // Trigger prune by recording a new (non-edit) signal
      tracker.recordScroll();

      // Now edits should be gone — should be 'reading' (1 scroll < 3 threshold)
      assert.equal(tracker.computeTaskType(), 'idle');
    });
  });
});
