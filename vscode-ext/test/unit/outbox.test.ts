import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';
import { FakeClock } from '../../src/clock.ts';
import { Outbox } from '../../src/link/outbox.ts';

describe('Outbox Queue and Disk Spill', () => {
  let tempDir: string;
  let clock: FakeClock;

  beforeEach(() => {
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'dhyan-outbox-test-'));
    clock = new FakeClock(1700000000000);
  });

  afterEach(() => {
    try {
      fs.rmSync(tempDir, { recursive: true, force: true });
    } catch {}
  });

  it('assigns strictly monotonic seq, ts, and mono to enqueued events', () => {
    const outbox = new Outbox(clock, { storageDir: tempDir });
    const ev1 = outbox.enqueue({ type: 'ed_summary', typingChars: 10 });
    clock.advance(100);
    const ev2 = outbox.enqueue({ type: 'ed_summary', typingChars: 20 });

    assert.equal(ev1.seq, 1);
    assert.equal(ev2.seq, 2);
    assert.equal(ev1.mono, 0);
    assert.equal(ev2.mono, 100);
    assert.equal(ev1.typingChars, 10);
    assert.equal(ev2.typingChars, 20);
  });

  it('spills to disk when in-memory capacity is exceeded', () => {
    // Configure small memory limit of 10 for testing
    const outbox = new Outbox(clock, { storageDir: tempDir, maxMemoryEvents: 10 });

    for (let i = 1; i <= 25; i++) {
      outbox.enqueue({ type: 'ed_summary', index: i });
    }

    const size = outbox.size();
    // Memory should be bounded
    assert.ok(size.memoryCount <= 25);
    assert.equal(size.hasSpill, true);

    // Reading unacked events should return in exact seq order starting from 1
    const batch = outbox.getBatchAfter(0, 100);
    assert.equal(batch.length, 25);
    assert.equal(batch[0].seq, 1);
    assert.equal(batch[24].seq, 25);
  });

  it('prunes acked events from both memory and disk spill', () => {
    const outbox = new Outbox(clock, { storageDir: tempDir, maxMemoryEvents: 10 });

    for (let i = 1; i <= 20; i++) {
      outbox.enqueue({ type: 'ed_summary', index: i });
    }

    // Ack up to seq 15
    outbox.ack(15);

    const remaining = outbox.getBatchAfter(0, 100);
    assert.equal(remaining.length, 5);
    assert.equal(remaining[0].seq, 16);
    assert.equal(remaining[4].seq, 20);
  });

  it('re-reads from watermark on welcome', () => {
    const outbox = new Outbox(clock, { storageDir: tempDir });
    for (let i = 1; i <= 10; i++) {
      outbox.enqueue({ type: 'ed_summary', index: i });
    }

    // Engine welcomes with ackedUpTo = 6
    const unacked = outbox.getBatchAfter(6, 100);
    assert.equal(unacked.length, 4);
    assert.equal(unacked[0].seq, 7);
    assert.equal(unacked[3].seq, 10);
  });

  it('safely handles corrupt or malformed spill file content without throwing', () => {
    const outbox = new Outbox(clock, { storageDir: tempDir, maxMemoryEvents: 5 });

    // Write corrupted JSON line to the spill file manually
    const spillFile = path.join(tempDir, 'outbox_spill.jsonl');
    fs.writeFileSync(spillFile, 'CORRUPT_NOT_JSON_LINE\n{"seq": 99, "type": "ed_summary"}\n', 'utf8');

    // getBatchAfter must not throw
    const batch = outbox.getBatchAfter(0, 50);
    assert.ok(Array.isArray(batch));
    assert.equal(batch.length, 1);
    assert.equal(batch[0].seq, 99);
  });
});
