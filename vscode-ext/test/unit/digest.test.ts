/**
 * digest.test.ts — Unit tests for DigestManager.
 * Runs via: npm run test:unit
 */

import assert from 'node:assert/strict';
import { describe, it, beforeEach } from 'mocha';
import { FakeClock } from '../../src/clock.ts';
import { Outbox } from '../../src/link/outbox.ts';
import { DigestManager } from '../../src/features/digest.ts';

describe('DigestManager (Phase 5 Held Notifications)', () => {
  let clock: FakeClock;
  let outbox: Outbox;
  let digestManager: DigestManager;
  let lastInfoToast = '';

  beforeEach(() => {
    clock = new FakeClock(1_000_000);
    outbox = new Outbox(clock);
    lastInfoToast = '';

    const mockVscode = {
      workspace: {
        getConfiguration: () => ({
          get: (key: string, def: any) => {
            if (key === 'digest.toastAtBreak') return true;
            return def;
          }
        })
      },
      window: {
        showInformationMessage: async (msg: string) => {
          lastInfoToast = msg;
          return 'Dismiss';
        },
        showQuickPick: async () => undefined
      }
    };

    digestManager = new DigestManager({
      engineClient: null,
      outbox,
      vscodeApi: mockVscode
    });
  });

  it('updates held items and returns accurate count', () => {
    assert.equal(digestManager.getItemCount(), 0);

    digestManager.updateItems([
      { id: 'item-1', title: 'Slack from Bob', category: 'chat', ts: clock.now() },
      { id: 'item-2', title: 'GitHub PR comment', category: 'code', ts: clock.now() }
    ]);

    assert.equal(digestManager.getItemCount(), 2);
    assert.equal(digestManager.getItems()[0].title, 'Slack from Bob');
  });

  it('notifies at break when items are held and toastAtBreak is enabled', () => {
    digestManager.updateItems([
      { id: 'item-1', title: 'Email alert', category: 'mail' },
      { id: 'item-2', title: 'Calendar ping', category: 'calendar' }
    ]);

    digestManager.notifyAtBreak();
    assert.ok(lastInfoToast.includes('2 notifications were held during your focus'));
  });

  it('does not notify at break if held list is empty', () => {
    digestManager.notifyAtBreak();
    assert.equal(lastInfoToast, '');
  });

  it('clears items cleanly', () => {
    digestManager.updateItems([{ id: 'item-1' }]);
    assert.equal(digestManager.getItemCount(), 1);

    digestManager.clearItems();
    assert.equal(digestManager.getItemCount(), 0);
  });
});
