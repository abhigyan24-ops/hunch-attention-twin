import assert from 'node:assert/strict';
import { FakeClock } from '../../src/clock.ts';
import { WindowAggregator } from '../../src/aggregate/window.ts';

describe('Window Aggregator (10s windowed ed_summary)', () => {
  it('accumulates edit counts, navigation events, and resets after tick', () => {
    const clock = new FakeClock(1700000000000);
    const agg = new WindowAggregator(clock);

    // Initial state: window focused and active
    agg.onWindow(true, true, clock.mono());

    // Edits
    agg.onEdit('typing', 10, 2, clock.now());
    clock.advance(250);
    agg.onEdit('typing', 15, 0, clock.now());
    agg.onEdit('paste', 50, 0);
    agg.onEdit('undo', 0, 5);
    agg.onEdit('redo', 5, 0);
    agg.onEdit('bulk', 300, 10);
    agg.onEdit('external', 20, 0);

    // Navigations & Saves
    agg.onCursorMove();
    agg.onCursorMove();
    agg.onScroll();
    agg.onFileSwitch('typescript');
    agg.onSave();

    // Diagnostics & Debug
    agg.onDiagnostics(2, 4);
    agg.onDebug('start');

    // Advance clock by 10 seconds
    clock.advance(9750);

    const summary = agg.tick(clock);
    assert.ok(summary, 'Expected non-null summary');
    assert.equal(summary.type, 'ed_summary');
    assert.equal(summary.typingChars, 25);
    assert.equal(summary.deletions, 17);
    assert.equal(summary.undo, 1);
    assert.equal(summary.redo, 1);
    assert.equal(summary.pasteLike, 1);
    assert.equal(summary.bulk, 1);
    assert.equal(summary.external, 1);
    assert.equal(summary.cursorMoves, 2);
    assert.equal(summary.scrolls, 1);
    assert.equal(summary.fileSwitches, 1);
    assert.equal(summary.saves, 1);
    assert.equal(summary.diagErr, 2);
    assert.equal(summary.diagWarn, 4);
    assert.equal(summary.debugActive, true);
    assert.equal(summary.lang, 'typescript');
    assert.ok(typeof summary.gapP50Ms === 'number');

    // Second tick with no activity produces a summary only if active/focused
    // Let's set window to blurred and inactive
    agg.onWindow(false, false, clock.mono());
    clock.advance(10000);
    const emptySummary = agg.tick(clock);
    assert.equal(emptySummary, null, 'Must return null when window is inactive and has no events');
  });

  it('accurately accounts for focusedMs and activeMs within the window', () => {
    const clock = new FakeClock(1700000000000);
    const agg = new WindowAggregator(clock);

    // Focus starts active
    agg.onWindow(true, true, clock.mono());

    // Active for 4 seconds
    clock.advance(4000);

    // User switches to another application (unfocused & inactive) for 6 seconds
    agg.onWindow(false, false, clock.mono());
    clock.advance(6000);

    const summary = agg.tick(clock);
    assert.ok(summary);
    // Focused for ~4000ms out of 10000ms
    assert.equal(summary.focusedMs, 4000);
    assert.equal(summary.activeMs, 4000);
    assert.equal(summary.durMs, 10000);
  });
});
