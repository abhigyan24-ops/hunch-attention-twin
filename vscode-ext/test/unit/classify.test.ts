import assert from 'node:assert/strict';
import { classifyEdit, isTrackedScheme } from '../../src/aggregate/classify.ts';

describe('Edit Classifier (SPEC DIGEST item 1)', () => {
  it('returns external when inactive document or inactive window', () => {
    assert.equal(
      classifyEdit({
        changes: [{ textLen: 1, rangeLen: 0 }],
        isActiveDoc: false,
        windowActive: true
      }),
      'external'
    );

    assert.equal(
      classifyEdit({
        changes: [{ textLen: 1, rangeLen: 0 }],
        isActiveDoc: true,
        windowActive: false
      }),
      'external'
    );
  });

  it('identifies undo and redo reasons', () => {
    assert.equal(
      classifyEdit({
        reason: 'undo',
        changes: [{ textLen: 5, rangeLen: 2 }],
        isActiveDoc: true,
        windowActive: true
      }),
      'undo'
    );

    assert.equal(
      classifyEdit({
        reason: 'redo',
        changes: [{ textLen: 5, rangeLen: 2 }],
        isActiveDoc: true,
        windowActive: true
      }),
      'redo'
    );
  });

  it('returns external when changes array is empty', () => {
    assert.equal(
      classifyEdit({
        changes: [],
        isActiveDoc: true,
        windowActive: true
      }),
      'external'
    );
  });

  it('classifies total > 2000 boundary (2000 vs 2001)', () => {
    // 2000 chars total -> typing (or paste if 1 change rangeLen 0, but here with rangeLen > 0)
    assert.equal(
      classifyEdit({
        changes: [{ textLen: 2000, rangeLen: 1 }],
        isActiveDoc: true,
        windowActive: true
      }),
      'typing'
    );

    // 2001 chars total -> bulk
    assert.equal(
      classifyEdit({
        changes: [{ textLen: 2001, rangeLen: 1 }],
        isActiveDoc: true,
        windowActive: true
      }),
      'bulk'
    );
  });

  it('classifies changes.length > 20 and maxLen > 3 boundary', () => {
    // 20 changes with maxLen 4 -> NOT bulk (must be > 20 changes)
    const twentyChanges = Array.from({ length: 20 }, () => ({ textLen: 4, rangeLen: 0 }));
    assert.equal(
      classifyEdit({
        changes: twentyChanges,
        isActiveDoc: true,
        windowActive: true
      }),
      'typing'
    );

    // 21 changes with maxLen 3 -> NOT bulk (must be maxLen > 3)
    const twentyOneSmall = Array.from({ length: 21 }, () => ({ textLen: 3, rangeLen: 0 }));
    assert.equal(
      classifyEdit({
        changes: twentyOneSmall,
        isActiveDoc: true,
        windowActive: true
      }),
      'typing'
    );

    // 21 changes with maxLen 4 -> bulk
    const twentyOneBulk = Array.from({ length: 21 }, () => ({ textLen: 4, rangeLen: 0 }));
    assert.equal(
      classifyEdit({
        changes: twentyOneBulk,
        isActiveDoc: true,
        windowActive: true
      }),
      'bulk'
    );
  });

  it('classifies paste boundary (textLen 40 vs 41, rangeLen 0 vs 1)', () => {
    // exactly 1 change, rangeLen === 0, textLen 40 -> typing
    assert.equal(
      classifyEdit({
        changes: [{ textLen: 40, rangeLen: 0 }],
        isActiveDoc: true,
        windowActive: true
      }),
      'typing'
    );

    // exactly 1 change, rangeLen === 0, textLen 41 -> paste
    assert.equal(
      classifyEdit({
        changes: [{ textLen: 41, rangeLen: 0 }],
        isActiveDoc: true,
        windowActive: true
      }),
      'paste'
    );

    // 1 change, textLen 45, but rangeLen === 1 (replacing a character) -> typing
    assert.equal(
      classifyEdit({
        changes: [{ textLen: 45, rangeLen: 1 }],
        isActiveDoc: true,
        windowActive: true
      }),
      'typing'
    );
  });

  it('classifies normal single-character and small typing edits', () => {
    assert.equal(
      classifyEdit({
        changes: [{ textLen: 1, rangeLen: 0 }],
        isActiveDoc: true,
        windowActive: true
      }),
      'typing'
    );

    // backspace
    assert.equal(
      classifyEdit({
        changes: [{ textLen: 0, rangeLen: 1 }],
        isActiveDoc: true,
        windowActive: true
      }),
      'typing'
    );
  });

  it('filters tracked URI schemes', () => {
    assert.equal(isTrackedScheme('file'), true);
    assert.equal(isTrackedScheme('untitled'), true);
    assert.equal(isTrackedScheme('FILE'), true);
    assert.equal(isTrackedScheme('output'), false);
    assert.equal(isTrackedScheme('git'), false);
    assert.equal(isTrackedScheme('vscode-userdata'), false);
    assert.equal(isTrackedScheme(undefined), false);
  });
});
