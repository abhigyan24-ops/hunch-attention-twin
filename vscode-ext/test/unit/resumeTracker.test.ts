/**
 * resumeTracker.test.ts — Unit tests for ResumeTracker.
 * Runs via: npm run test:unit
 */

import assert from 'node:assert/strict';
import { describe, it, beforeEach } from 'mocha';
import { FakeClock } from '../../src/clock.ts';
import { ResumeTracker, CENSORING_CUTOFF_MS } from '../../src/aggregate/resumeTracker.ts';

describe('ResumeTracker (Phase 4 Resume Assist & Censoring)', () => {
  let clock: FakeClock;
  let tracker: ResumeTracker;
  let mockLoad: any;

  beforeEach(() => {
    clock = new FakeClock(1_000_000);
    // Deterministic RNG: returns 0.5 by default
    tracker = new ResumeTracker(clock, () => 0.5);
    mockLoad = {
      dirtyDocs: 1,
      openTabs: 3,
      debugPaused: false,
      uncommitted: 2,
      taskType: 'coding'
    };
  });

  // ── Away detection & minAwayMinutes ─────────────────────────────────────────

  describe('Away detection & thresholds', () => {
    it('returns null if onFocus is called without onBlur', () => {
      const res = tracker.onFocus({ taskKey: 'task-1', load: mockLoad }, 3, 0.8);
      assert.equal(res, null);
    });

    it('returns null if awayMs < minAwayMinutes (e.g. 2 min away < 3 min threshold)', () => {
      tracker.onBlur();
      clock.advance(120_000); // 2 minutes
      const res = tracker.onFocus({ taskKey: 'task-1', load: mockLoad }, 3, 0.8);
      assert.equal(res, null);
      assert.equal(tracker.getCurrentCandidate(), null);
    });

    it('creates candidate when awayMs >= minAwayMinutes (e.g. 3 min = 180,000ms)', () => {
      tracker.onBlur();
      clock.advance(180_000); // exactly 3 minutes
      const res = tracker.onFocus({ taskKey: 'task-1', load: mockLoad }, 3, 0.8);
      assert.ok(res !== null, 'Candidate should be created');
      assert.equal(res!.awayMs, 180_000);
      assert.equal(res!.taskKey, 'task-1');
      assert.ok(tracker.getCurrentCandidate() !== null);
    });
  });

  // ── Propensity clamping & assist randomization ──────────────────────────────

  describe('Propensity clamping & assist decision', () => {
    it('clamps assistProbability below 0.05 up to 0.05', () => {
      const customTracker = new ResumeTracker(clock, () => 0.04);
      customTracker.onBlur();
      clock.advance(200_000);
      customTracker.onFocus({ taskKey: 'task-1', load: mockLoad }, 3, 0.01);
      const cand = customTracker.getCurrentCandidate();
      assert.equal(cand?.propensity, 0.05);
      // rng returned 0.04 < 0.05 -> assist = true
      assert.equal(cand?.assist, true);
    });

    it('clamps assistProbability above 0.95 down to 0.95', () => {
      const customTracker = new ResumeTracker(clock, () => 0.96);
      customTracker.onBlur();
      clock.advance(200_000);
      customTracker.onFocus({ taskKey: 'task-1', load: mockLoad }, 3, 0.99);
      const cand = customTracker.getCurrentCandidate();
      assert.equal(cand?.propensity, 0.95);
      // rng returned 0.96 >= 0.95 -> assist = false
      assert.equal(cand?.assist, false);
    });

    it('decides assist based on rng < propensity', () => {
      // rng = 0.5, propensity = 0.8 -> assist = true
      tracker.onBlur();
      clock.advance(200_000);
      const res = tracker.onFocus({ taskKey: 'task-1', load: mockLoad }, 3, 0.8);
      assert.equal(res?.assist, true);
    });
  });

  // ── Resumption measurement (12 typing edits within 30s) ──────────────────────

  describe('Resumption measurement', () => {
    beforeEach(() => {
      tracker.onBlur();
      clock.advance(300_000); // 5 minutes away
      tracker.onFocus({ taskKey: 'task-target', load: mockLoad }, 3, 0.8);
    });

    it('ignores non-typing edits (paste, bulk, undo)', () => {
      assert.equal(tracker.onEdit('paste', 'task-target'), null);
      assert.equal(tracker.onEdit('bulk', 'task-target'), null);
      assert.equal(tracker.onEdit('undo', 'task-target'), null);
      assert.equal(tracker.onEdit('redo', 'task-target'), null);
      assert.equal(tracker.getCurrentCandidate()?.typingEditTimestamps.length, 0);
    });

    it('ignores typing edits in a different taskKey', () => {
      for (let i = 0; i < 15; i++) {
        assert.equal(tracker.onEdit('typing', 'task-different'), null);
      }
      assert.equal(tracker.getCurrentCandidate()?.typingEditTimestamps.length, 0);
    });

    it('does NOT trigger resumption with fewer than 12 typing edits', () => {
      for (let i = 0; i < 11; i++) {
        clock.advance(500);
        const event = tracker.onEdit('typing', 'task-target');
        assert.equal(event, null);
      }
      assert.equal(tracker.getCurrentCandidate()?.typingEditTimestamps.length, 11);
    });

    it('triggers resumption on the 12th typing edit within 30s', () => {
      // First edit occurs 5s after refocus
      clock.advance(5_000);

      let resumeEvent = null;
      for (let i = 0; i < 12; i++) {
        clock.advance(500);
        const event = tracker.onEdit('typing', 'task-target');
        if (event) {
          resumeEvent = event;
        }
      }

      assert.ok(resumeEvent !== null, 'Resumption event should be emitted');
      assert.equal(resumeEvent!.type, 'ed_resume');
      assert.equal(resumeEvent!.resumed, true);
      assert.equal(resumeEvent!.taskKey, 'task-target');
      assert.equal(resumeEvent!.awayMs, 300_000);
      assert.equal(resumeEvent!.experiment, true);
      assert.equal(resumeEvent!.used, false);
      // lagMs is time to first edit in cluster = 5000 + 500 = 5500 ms
      assert.equal(resumeEvent!.lagMs, 5500);
      assert.deepEqual(resumeEvent!.load, mockLoad);

      // Candidate should be cleared
      assert.equal(tracker.getCurrentCandidate(), null);
    });

    it('records used = true when onAssistUsed() is called before resumption', () => {
      tracker.onAssistUsed();

      clock.advance(2_000);
      let resumeEvent = null;
      for (let i = 0; i < 12; i++) {
        clock.advance(200);
        const event = tracker.onEdit('typing', 'task-target');
        if (event) resumeEvent = event;
      }

      assert.ok(resumeEvent !== null);
      assert.equal(resumeEvent!.used, true);
    });

    it('does not trigger if edits are spread over more than 30s', () => {
      // 6 edits at t=0
      for (let i = 0; i < 6; i++) {
        clock.advance(500);
        tracker.onEdit('typing', 'task-target');
      }

      // Advance 35s (first 6 edits fall out of the 30s window)
      clock.advance(35_000);

      // 6 more edits
      let resumeEvent = null;
      for (let i = 0; i < 6; i++) {
        clock.advance(500);
        const event = tracker.onEdit('typing', 'task-target');
        if (event) resumeEvent = event;
      }

      // Only 6 edits in the 30s window, so no resumption event
      assert.equal(resumeEvent, null);
      assert.ok(tracker.getCurrentCandidate() !== null);
    });
  });

  // ── Censoring cutoff (30 minutes) ──────────────────────────────────────────

  describe('Censoring cutoff (30 minutes)', () => {
    beforeEach(() => {
      tracker.onBlur();
      clock.advance(200_000);
      tracker.onFocus({ taskKey: 'task-censor', load: mockLoad }, 3, 0.8);
    });

    it('tick() emits censored ed_resume after 30 minutes without resumption', () => {
      // Advance by 30 minutes
      clock.advance(CENSORING_CUTOFF_MS);

      const event = tracker.tick();
      assert.ok(event !== null, 'Should emit censored event on tick');
      assert.equal(event!.type, 'ed_resume');
      assert.equal(event!.resumed, false);
      assert.equal(event!.lagMs, 1_800_000);
      assert.equal(event!.taskKey, 'task-censor');
      assert.equal(event!.awayMs, 200_000);

      // Candidate is cleared
      assert.equal(tracker.getCurrentCandidate(), null);
    });

    it('onEdit() emits censored ed_resume if 30 minutes have elapsed', () => {
      clock.advance(CENSORING_CUTOFF_MS + 10_000);

      const event = tracker.onEdit('typing', 'task-censor');
      assert.ok(event !== null);
      assert.equal(event!.resumed, false);
      assert.equal(event!.lagMs, 1_800_000);
    });

    it('does not emit censored event before 30 minutes', () => {
      clock.advance(CENSORING_CUTOFF_MS - 5_000);
      assert.equal(tracker.tick(), null);
      assert.ok(tracker.getCurrentCandidate() !== null);
    });
  });
});
