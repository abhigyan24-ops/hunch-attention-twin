import * as fs from 'node:fs';
import * as path from 'node:path';
import { randomBytes } from 'node:crypto';
import type { Clock } from '../clock.ts';

export interface OutboxEvent {
  seq: number;
  ts: number;
  mono: number;
  [key: string]: unknown;
}

export interface OutboxOptions {
  storageDir?: string;
  maxMemoryEvents?: number;
  maxSpillBytes?: number;
}

export class Outbox {
  private readonly storageDir: string;
  private readonly spillFilePath: string;
  private readonly maxMemoryEvents: number;
  private readonly maxSpillBytes: number;
  private readonly clock: Clock;

  public readonly epoch: string;
  private nextSeq: number = 1;
  private memoryEvents: OutboxEvent[] = [];

  constructor(clock: Clock, options?: OutboxOptions) {
    this.clock = clock;
    this.maxMemoryEvents = options?.maxMemoryEvents ?? 5000;
    this.maxSpillBytes = options?.maxSpillBytes ?? 5 * 1024 * 1024; // 5 MB
    this.storageDir = options?.storageDir || '';
    this.spillFilePath = this.storageDir ? path.join(this.storageDir, 'outbox_spill.jsonl') : '';
    this.epoch = randomBytes(8).toString('hex');

    // Ensure storage directory exists if configured
    if (this.storageDir) {
      try {
        if (!fs.existsSync(this.storageDir)) {
          fs.mkdirSync(this.storageDir, { recursive: true });
        }
      } catch {
        // Silently handle filesystem failures
      }
    }
  }

  /**
   * Enqueues an event into the outbox with strictly monotonic seq, ts, and mono.
   * If in-memory capacity exceeds 5000, spills oldest events to disk.
   */
  enqueue(payload: Record<string, unknown>): OutboxEvent {
    const event: OutboxEvent = {
      seq: this.nextSeq++,
      ts: this.clock.now(),
      mono: this.clock.mono(),
      ...payload
    };

    this.memoryEvents.push(event);

    if (this.memoryEvents.length > this.maxMemoryEvents) {
      this.spillOldestToDisk();
    }

    return event;
  }

  /**
   * Spills oldest memory events to the JSONL spill file.
   */
  private spillOldestToDisk(): void {
    if (!this.spillFilePath) {
      // If no disk storage configured, keep only the most recent memory events
      this.memoryEvents.shift();
      return;
    }

    // Spill the oldest 500 events to disk
    const toSpill = this.memoryEvents.splice(0, 500);
    try {
      const lines = toSpill.map(e => JSON.stringify(e)).join('\n') + '\n';
      fs.appendFileSync(this.spillFilePath, lines, 'utf8');

      // Check spill size cap
      this.enforceSpillSizeCap();
    } catch {
      // Ignore disk errors
    }
  }

  /**
   * Ensures the spill file does not exceed maxSpillBytes (5 MB), dropping oldest lines if needed.
   */
  private enforceSpillSizeCap(): void {
    if (!this.spillFilePath || !fs.existsSync(this.spillFilePath)) return;

    try {
      const stat = fs.statSync(this.spillFilePath);
      if (stat.size > this.maxSpillBytes) {
        const content = fs.readFileSync(this.spillFilePath, 'utf8');
        const lines = content.split('\n').filter(Boolean);
        // Retain newer half of lines
        const half = Math.floor(lines.length / 2);
        const retained = lines.slice(half).join('\n') + '\n';
        fs.writeFileSync(this.spillFilePath, retained, 'utf8');
      }
    } catch {
      // Ignore disk error
    }
  }

  /**
   * Acknowledges events up to `upToSeq`, pruning them from memory and the spill file.
   */
  ack(upToSeq: number): void {
    // 1. Prune memory events
    this.memoryEvents = this.memoryEvents.filter(e => e.seq > upToSeq);

    // 2. Prune disk spill events
    if (this.spillFilePath && fs.existsSync(this.spillFilePath)) {
      try {
        const content = fs.readFileSync(this.spillFilePath, 'utf8');
        const lines = content.split('\n').filter(Boolean);
        const remainingLines: string[] = [];

        for (const line of lines) {
          try {
            const ev = JSON.parse(line);
            if (ev && typeof ev.seq === 'number' && ev.seq > upToSeq) {
              remainingLines.push(line);
            }
          } catch {
            // Discard corrupt line
          }
        }

        if (remainingLines.length === 0) {
          try {
            fs.unlinkSync(this.spillFilePath);
          } catch {}
        } else {
          fs.writeFileSync(this.spillFilePath, remainingLines.join('\n') + '\n', 'utf8');
        }
      } catch {
        // Ignore disk errors
      }
    }
  }

  /**
   * Returns a batch of up to `maxBatchSize` events strictly with `seq > upToSeq`.
   */
  getBatchAfter(upToSeq: number, maxBatchSize: number = 200): OutboxEvent[] {
    const result: OutboxEvent[] = [];

    // 1. Read unacked events from spill file first
    if (this.spillFilePath && fs.existsSync(this.spillFilePath)) {
      try {
        const content = fs.readFileSync(this.spillFilePath, 'utf8');
        const lines = content.split('\n').filter(Boolean);
        for (const line of lines) {
          if (result.length >= maxBatchSize) break;
          try {
            const ev = JSON.parse(line);
            if (ev && typeof ev.seq === 'number' && ev.seq > upToSeq) {
              result.push(ev);
            }
          } catch {
            // Safely skip corrupt line
          }
        }
      } catch {
        // Ignore disk error
      }
    }

    // 2. Read unacked events from memory
    for (const ev of this.memoryEvents) {
      if (result.length >= maxBatchSize) break;
      if (ev.seq > upToSeq) {
        result.push(ev);
      }
    }

    return result;
  }

  size(): { memoryCount: number; hasSpill: boolean } {
    const hasSpill = Boolean(
      this.spillFilePath &&
      fs.existsSync(this.spillFilePath) &&
      fs.statSync(this.spillFilePath).size > 0
    );
    return {
      memoryCount: this.memoryEvents.length,
      hasSpill
    };
  }

  getMemoryEvents(): OutboxEvent[] {
    return [...this.memoryEvents];
  }
}
