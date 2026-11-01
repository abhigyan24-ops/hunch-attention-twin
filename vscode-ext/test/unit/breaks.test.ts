/**
 * breaks.test.ts — Unit tests for BreakDetector.
 * Runs via: npm run test:unit
 */

import assert from 'node:assert/strict';
import { describe, it, beforeEach } from 'mocha';
import { FakeClock } from '../../src/clock.ts';
import { BreakDetector } from '../../src/aggregate/breaks.ts';

describe('BreakDetector', () => {
  let clock: FakeClock;
  let detector: BreakDetector;

  beforeEach(() => {
    clock = new FakeClock(1_000_000);
    detector = new BreakDetector(clock);
  });

  // ── tick returns null when no signals ──────────────────────────────────────

  it('returns null on tick with no signals', () => {
    assert.equal(detector.tick(clock.now()), null);
  });

  // ── save_green ─────────────────────────────────────────────────────────────

  describe('save_green', () => {
    it('emits save_green after save + 0 errors + 15s quiet', () => {
      detector.onDiagnosticsChange(0, 0);
      detector.onSave();
      // Before threshold — no break
      clock.advance(10_000);
      assert.equal(detector.tick(clock.now()), null);
      // After 15s — break emitted
      clock.advance(5_001);
      const brk = detector.tick(clock.now());
      assert.ok(brk !== null, 'expected save_green break');
      assert.equal(brk!.kind, 'save_green');
      assert.equal(brk!.confidence, 0.7);
    });

    it('does NOT emit save_green if there are errors at save time', () => {
      detector.onDiagnosticsChange(3, 0);
      detector.onSave();
      clock.advance(20_000);
      assert.equal(detector.tick(clock.now()), null);
    });

    it('does NOT emit save_green if new errors appear after save', () => {
      detector.onDiagnosticsChange(0, 0);
      detector.onSave();
      clock.advance(10_000);
      detector.onDiagnosticsChange(1, 0);
      clock.advance(10_000);
      assert.equal(detector.tick(clock.now()), null);
    });

    it('emits save_green only once (consumed on first tick)', () => {
      detector.onDiagnosticsChange(0, 0);
      detector.onSave();
      clock.advance(16_000);
      const first = detector.tick(clock.now());
      const second = detector.tick(clock.now());
      assert.ok(first !== null);
      assert.equal(second, null);
    });
  });

  // ── idle_after_save ────────────────────────────────────────────────────────

  describe('idle_after_save', () => {
    it('emits idle_after_save when saved WITH errors that cleared + no edits + 20s', () => {
      // Errors at save time — save_green won't fire
      detector.onDiagnosticsChange(2, 0);
      detector.onSave(); // errorsAtSave=2
      // Errors clear (user fixed them)
      detector.onDiagnosticsChange(0, 0);
      clock.advance(21_000);
      const brk = detector.tick(clock.now());
      assert.ok(brk !== null, 'expected idle_after_save break');
      assert.equal(brk!.kind, 'idle_after_save');
      assert.equal(brk!.confidence, 0.6);
    });

    it('save_green fires at 15s (not idle_after_save) when no errors at save + no edits', () => {
      // No errors — save_green fires first at 15s, consuming lastSaveTs
      // idle_after_save should NOT fire after
      detector.onDiagnosticsChange(0, 0);
      detector.onSave();
      clock.advance(21_000);
      const brk = detector.tick(clock.now());
      assert.ok(brk !== null);
      assert.equal(brk!.kind, 'save_green'); // save_green wins at 15s
      // idle_after_save should NOT fire after save_green consumed lastSaveTs
      assert.equal(detector.tick(clock.now()), null);
    });

    it('does NOT emit idle_after_save if user edits after save', () => {
      // Errors at save — so save_green won't fire, idle_after_save could
      detector.onDiagnosticsChange(2, 0);
      detector.onSave();
      detector.onDiagnosticsChange(0, 0);
      clock.advance(5_000);
      detector.onEdit();  // user typed something
      clock.advance(16_000);
      // lastEditTs > lastSaveTs — should not trigger idle_after_save
      assert.equal(detector.tick(clock.now()), null);
    });

    it('does NOT emit idle_after_save if current errors still present', () => {
      // Errors at save, still present — idle_after_save requires currentErrors === 0
      detector.onDiagnosticsChange(2, 0);
      detector.onSave();
      // Errors remain
      clock.advance(21_000);
      assert.equal(detector.tick(clock.now()), null);
    });
  });

  // ── test_ok / build_ok ─────────────────────────────────────────────────────

  describe('test_ok and build_ok', () => {
    it('emits test_ok on test task exit code 0', () => {
      detector.onTaskEnd('test', 0);
      const brk = detector.tick(clock.now());
      assert.ok(brk !== null);
      assert.equal(brk!.kind, 'test_ok');
      assert.equal(brk!.confidence, 0.8);
    });

    it('emits build_ok on build task exit code 0', () => {
      detector.onTaskEnd('build', 0);
      const brk = detector.tick(clock.now());
      assert.ok(brk !== null);
      assert.equal(brk!.kind, 'build_ok');
      assert.equal(brk!.confidence, 0.8);
    });

    it('emits build_ok on lint task exit code 0', () => {
      detector.onTaskEnd('lint', 0);
      const brk = detector.tick(clock.now());
      assert.ok(brk !== null);
      assert.equal(brk!.kind, 'build_ok');
    });

    it('does NOT emit on non-zero exit code', () => {
      detector.onTaskEnd('test', 1);
      assert.equal(detector.tick(clock.now()), null);
    });

    it('does NOT emit for run task exit code 0', () => {
      detector.onTaskEnd('run', 0);
      assert.equal(detector.tick(clock.now()), null);
    });

    it('emits test_ok only once (drained on first tick)', () => {
      detector.onTaskEnd('test', 0);
      const first = detector.tick(clock.now());
      const second = detector.tick(clock.now());
      assert.ok(first !== null);
      assert.equal(second, null);
    });
  });

  // ── commit / branch_switch ─────────────────────────────────────────────────

  describe('commit and branch_switch', () => {
    it('emits commit when git HEAD changes', () => {
      detector.onGitHeadChange('abc123');
      // First call initializes — no break
      assert.equal(detector.tick(clock.now()), null);
      // HEAD changes
      detector.onGitHeadChange('def456');
      const brk = detector.tick(clock.now());
      assert.ok(brk !== null);
      assert.equal(brk!.kind, 'commit');
      assert.equal(brk!.confidence, 0.9);
    });

    it('does NOT emit commit on first HEAD observation', () => {
      detector.onGitHeadChange('abc123');
      assert.equal(detector.tick(clock.now()), null);
    });

    it('emits branch_switch when branch changes', () => {
      detector.onBranchChange('main');
      // First observation — no break
      assert.equal(detector.tick(clock.now()), null);
      detector.onBranchChange('feature/x');
      const brk = detector.tick(clock.now());
      assert.ok(brk !== null);
      assert.equal(brk!.kind, 'branch_switch');
      assert.equal(brk!.confidence, 0.9);
    });

    it('does NOT emit branch_switch on first observation', () => {
      detector.onBranchChange('main');
      assert.equal(detector.tick(clock.now()), null);
    });

    it('does NOT emit branch_switch when branch is same', () => {
      detector.onBranchChange('main');
      detector.onBranchChange('main');
      assert.equal(detector.tick(clock.now()), null);
    });
  });

  // ── debug_end ──────────────────────────────────────────────────────────────

  describe('debug_end', () => {
    it('emits debug_end on debug session stop', () => {
      detector.onDebugStop();
      const brk = detector.tick(clock.now());
      assert.ok(brk !== null);
      assert.equal(brk!.kind, 'debug_end');
      assert.equal(brk!.confidence, 0.7);
    });
  });

  // ── file_closed_clean ──────────────────────────────────────────────────────

  describe('file_closed_clean', () => {
    it('emits file_closed_clean when file closes with 0 errors', () => {
      detector.onFileClose(false);
      const brk = detector.tick(clock.now());
      assert.ok(brk !== null);
      assert.equal(brk!.kind, 'file_closed_clean');
      assert.equal(brk!.confidence, 0.6);
    });

    it('does NOT emit file_closed_clean when file closes with errors', () => {
      detector.onFileClose(true);
      assert.equal(detector.tick(clock.now()), null);
    });
  });

  // ── drainPending ───────────────────────────────────────────────────────────

  describe('drainPending', () => {
    it('drains multiple pending events at once', () => {
      detector.onDebugStop();
      detector.onTaskEnd('test', 0);
      const events = detector.drainPending();
      assert.equal(events.length, 2);
      assert.equal(events[0].kind, 'debug_end');
      assert.equal(events[1].kind, 'test_ok');
    });

    it('returns empty array when no pending events', () => {
      assert.deepEqual(detector.drainPending(), []);
    });

    it('clears pending queue after drain', () => {
      detector.onDebugStop();
      detector.drainPending();
      assert.equal(detector.tick(clock.now()), null);
    });
  });

  describe('Priority ordering', () => {
    it('emits pending breaks before tick-based save_green', () => {
      // Set up save_green conditions (no errors at save)
      detector.onDiagnosticsChange(0, 0);
      detector.onSave();
      // Also queue a pending break
      detector.onDebugStop();
      // Advance past save_green threshold
      clock.advance(16_000);
      // Pending break (debug_end) should come first
      const first = detector.tick(clock.now());
      assert.ok(first !== null);
      assert.equal(first!.kind, 'debug_end');
      // Then save_green on next tick
      const second = detector.tick(clock.now());
      assert.ok(second !== null);
      assert.equal(second!.kind, 'save_green');
    });
  });
});
