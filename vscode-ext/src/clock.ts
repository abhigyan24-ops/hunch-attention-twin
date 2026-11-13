/**
 * Clock interface for deterministic, mockable time measurement across Dhyan modules.
 * - `now()` provides current Unix epoch time in milliseconds (for event timestamps).
 * - `mono()` provides monotonic time in milliseconds (for interval and duration calculations).
 */
export interface Clock {
  now(): number;
  mono(): number;
}

export class RealClock implements Clock {
  now(): number {
    return Date.now();
  }

  mono(): number {
    return typeof performance !== 'undefined' ? performance.now() : Date.now();
  }
}

export class FakeClock implements Clock {
  private _now: number;
  private _mono: number;

  constructor(initialTimeMs: number = 1700000000000) {
    this._now = initialTimeMs;
    this._mono = 0;
  }

  now(): number {
    return this._now;
  }

  mono(): number {
    return this._mono;
  }

  advance(ms: number): void {
    if (ms < 0) throw new Error('Cannot move FakeClock backward');
    this._now += ms;
    this._mono += ms;
  }

  setNow(epochMs: number): void {
    this._now = epochMs;
  }
}

/**
 * Computes logical work day key with 4:00 AM circadian rollover.
 * E.g., 01:30 AM on Nov 13 maps to '2026-11-12'.
 */
export function getLogicalDayKey(epochMs: number = Date.now(), rolloverHour: number = 4): string {
  const d = new Date(epochMs);
  if (d.getHours() < rolloverHour) {
    d.setDate(d.getDate() - 1);
  }
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
