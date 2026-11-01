export interface RhythmStats {
  gapP50Ms: number;
  gapP90Ms: number;
  longPauseRatio: number;
}

export class RhythmTracker {
  private readonly capacity: number = 200;
  private buffer: number[] = [];
  private lastTypingTs: number | null = null;

  constructor(capacity: number = 200) {
    this.capacity = capacity;
  }

  /**
   * Records a typing event at timestamp `nowMs`.
   * Gaps are intervals between consecutive typing edits.
   * Gaps > 60000 ms (1 minute) are ignored as idle pauses.
   */
  recordTyping(nowMs: number): void {
    if (this.lastTypingTs !== null) {
      const gap = nowMs - this.lastTypingTs;
      if (gap >= 0 && gap <= 60000) {
        if (this.buffer.length >= this.capacity) {
          this.buffer.shift();
        }
        this.buffer.push(gap);
      }
    }
    this.lastTypingTs = nowMs;
  }

  getStats(): RhythmStats {
    if (this.buffer.length === 0) {
      return {
        gapP50Ms: 0,
        gapP90Ms: 0,
        longPauseRatio: 0
      };
    }

    const sorted = [...this.buffer].sort((a, b) => a - b);
    const n = sorted.length;

    // Percentile computation (nearest rank)
    const p50Index = Math.min(Math.floor(n * 0.5), n - 1);
    const p90Index = Math.min(Math.floor(n * 0.9), n - 1);

    const gapP50Ms = sorted[p50Index];
    const gapP90Ms = sorted[p90Index];

    const longPauses = sorted.filter(g => g > 5000).length;
    const longPauseRatio = Number((longPauses / n).toFixed(4));

    return {
      gapP50Ms,
      gapP90Ms,
      longPauseRatio
    };
  }

  reset(): void {
    this.buffer = [];
    this.lastTypingTs = null;
  }

  count(): number {
    return this.buffer.length;
  }
}
