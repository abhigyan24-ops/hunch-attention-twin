import assert from 'node:assert/strict';
import { parseEngineMessage } from '../../src/link/protocol.ts';

describe('Engine Protocol Runtime Parser', () => {
  it('parses valid welcome message', () => {
    const raw = JSON.stringify({
      kind: 'welcome',
      ackedUpTo: 42,
      cfg: { heartbeatSec: 5 }
    });
    const parsed = parseEngineMessage(raw);
    assert.deepEqual(parsed, {
      kind: 'welcome',
      ackedUpTo: 42,
      cfg: { heartbeatSec: 5 }
    });
  });

  it('parses valid ack message', () => {
    const raw = JSON.stringify({
      kind: 'ack',
      batchId: 'batch-abc',
      upTo: 100
    });
    const parsed = parseEngineMessage(raw);
    assert.deepEqual(parsed, {
      kind: 'ack',
      batchId: 'batch-abc',
      upTo: 100
    });
  });

  it('parses valid state lease message', () => {
    const raw = JSON.stringify({
      kind: 'state',
      focus: 'deep',
      leaseMs: 300000,
      version: 2
    });
    const parsed = parseEngineMessage(raw);
    assert.deepEqual(parsed, {
      kind: 'state',
      focus: 'deep',
      leaseMs: 300000,
      version: 2
    });
  });

  it('rejects malformed json and invalid shapes without throwing', () => {
    assert.equal(parseEngineMessage(null), null);
    assert.equal(parseEngineMessage(undefined), null);
    assert.equal(parseEngineMessage('not json at all'), null);
    assert.equal(parseEngineMessage('{}'), null);
    assert.equal(parseEngineMessage('{"kind": "unknown_message"}'), null);
    assert.equal(parseEngineMessage('{"kind": "welcome", "ackedUpTo": "not-a-number"}'), null);
    assert.equal(parseEngineMessage('{"kind": "state", "focus": "invalid_mode"}'), null);
  });
});
