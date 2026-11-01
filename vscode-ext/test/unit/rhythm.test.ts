import assert from 'node:assert/strict';
import { RhythmTracker } from '../../src/aggregate/rhythm.ts';

describe('Typing Rhythm Tracker', () => {
  it('returns zeros when buffer is empty or only 1 typing event exists', () => {
    const tracker = new RhythmTracker();
    assert.deepEqual(tracker.getStats(), {
      gapP50Ms: 0,
      gapP90Ms: 0,
      longPauseRatio: 0
    });

    tracker.recordTyping(1000);
    assert.deepEqual(tracker.getStats(), {
      gapP50Ms: 0,
      gapP90Ms: 0,
      longPauseRatio: 0
    });
  });

  it('records consecutive gaps and calculates p50, p90, longPauseRatio', () => {
    const tracker = new RhythmTracker(200);

    // Record typing at t = 1000, 1200, 1500, 2000, 8000
    // Gaps:
    // 1200 - 1000 = 200
    // 1500 - 1200 = 300
    // 2000 - 1500 = 500
    // 8000 - 2000 = 6000 (> 5000)
    tracker.recordTyping(1000);
    tracker.recordTyping(1200);
    tracker.recordTyping(1500);
    tracker.recordTyping(2000);
    tracker.recordTyping(8000);

    const stats = tracker.getStats();
    // Sorted gaps: [200, 300, 500, 6000], length = 4
    // p50Index = floor(4 * 0.5) = 2 -> 500
    // p90Index = floor(4 * 0.9) = 3 -> 6000
    // longPauseRatio = 1 / 4 = 0.25
    assert.equal(stats.gapP50Ms, 500);
    assert.equal(stats.gapP90Ms, 6000);
    assert.equal(stats.longPauseRatio, 0.25);
  });

  it('ignores pauses greater than 60000ms as idle intervals', () => {
    const tracker = new RhythmTracker();
    tracker.recordTyping(1000);
    tracker.recordTyping(1200); // gap 200

    // User steps away for 2 minutes (120,000 ms)
    tracker.recordTyping(1200 + 120000); // 121200 -> gap 120000 ignored!

    tracker.recordTyping(121200 + 300); // gap 300

    assert.equal(tracker.count(), 2); // only 200 and 300 recorded
    const stats = tracker.getStats();
    assert.equal(stats.gapP50Ms, 300);
  });

  it('bounds buffer to capacity (200 items)', () => {
    const tracker = new RhythmTracker(200);
    let t = 1000;
    tracker.recordTyping(t);

    for (let i = 0; i < 250; i++) {
      t += 150;
      tracker.recordTyping(t);
    }

    assert.equal(tracker.count(), 200);
  });
});
