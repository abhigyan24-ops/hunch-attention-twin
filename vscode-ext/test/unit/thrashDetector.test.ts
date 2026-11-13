/**
 * thrashDetector.test.ts — Unit tests for Cognitive Thrash & Rage-Scroll Shield.
 */

import assert from 'node:assert/strict';
import { describe, it } from 'mocha';
import { evaluateThrash } from '../../src/features/thrashDetector.ts';

describe('ThrashDetector', () => {
  const baseTime = 1_000_000;

  it('triggers on >= 3 consecutive failed tasks within 180s', () => {
    const taskFailures = [
      { timestamp: baseTime - 120_000, taskName: 'npm test', exitCode: 1 },
      { timestamp: baseTime - 60_000, taskName: 'npm test', exitCode: 1 },
      { timestamp: baseTime - 10_000, taskName: 'npm test', exitCode: 2 },
    ];

    const result = evaluateThrash(taskFailures, [], [], baseTime);
    assert.equal(result.isThrashing, true);
    assert.equal(result.trigger, 'repeated_test_failures');
  });

  it('does NOT trigger if task exits with code 0', () => {
    const taskFailures = [
      { timestamp: baseTime - 120_000, taskName: 'npm test', exitCode: 1 },
      { timestamp: baseTime - 60_000, taskName: 'npm test', exitCode: 0 },
      { timestamp: baseTime - 10_000, taskName: 'npm test', exitCode: 1 },
    ];

    const result = evaluateThrash(taskFailures, [], [], baseTime);
    assert.equal(result.isThrashing, false);
  });

  it('triggers on >= 12 undo or delete churn events within 60s', () => {
    const churnEvents = [];
    for (let i = 0; i < 12; i++) {
      churnEvents.push({ timestamp: baseTime - i * 2000, isUndoOrDelete: true });
    }

    const result = evaluateThrash([], churnEvents, [], baseTime);
    assert.equal(result.isThrashing, true);
    assert.equal(result.trigger, 'undo_churn_burst');
  });

  it('triggers on >= 8 file switches across >= 4 files within 45s', () => {
    const fileSwitches = [
      { timestamp: baseTime - 40_000, filePath: '/app/a.ts' },
      { timestamp: baseTime - 35_000, filePath: '/app/b.ts' },
      { timestamp: baseTime - 30_000, filePath: '/app/c.ts' },
      { timestamp: baseTime - 25_000, filePath: '/app/d.ts' },
      { timestamp: baseTime - 20_000, filePath: '/app/a.ts' },
      { timestamp: baseTime - 15_000, filePath: '/app/b.ts' },
      { timestamp: baseTime - 10_000, filePath: '/app/c.ts' },
      { timestamp: baseTime - 5_000, filePath: '/app/d.ts' },
    ];

    const result = evaluateThrash([], [], fileSwitches, baseTime);
    assert.equal(result.isThrashing, true);
    assert.equal(result.trigger, 'frantic_file_switching');
  });

  it('does NOT trigger if switching between only 2 files (normal reference check)', () => {
    const fileSwitches = [
      { timestamp: baseTime - 35_000, filePath: '/app/a.ts' },
      { timestamp: baseTime - 30_000, filePath: '/app/b.ts' },
      { timestamp: baseTime - 25_000, filePath: '/app/a.ts' },
      { timestamp: baseTime - 20_000, filePath: '/app/b.ts' },
      { timestamp: baseTime - 15_000, filePath: '/app/a.ts' },
      { timestamp: baseTime - 10_000, filePath: '/app/b.ts' },
      { timestamp: baseTime - 5_000, filePath: '/app/a.ts' },
      { timestamp: baseTime - 1_000, filePath: '/app/b.ts' },
    ];

    const result = evaluateThrash([], [], fileSwitches, baseTime);
    assert.equal(result.isThrashing, false);
  });
});
