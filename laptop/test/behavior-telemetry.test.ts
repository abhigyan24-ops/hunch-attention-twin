import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { NudgeTelemetryStore } from '../src/main/behavior/telemetry'
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'

describe('NudgeTelemetryStore & Requirement #4 Auto-Backoff Rule', () => {
  let tmpPath: string

  beforeEach(() => {
    tmpPath = path.join(os.tmpdir(), `dhyan-telemetry-test-${Date.now()}-${Math.random().toString(36).slice(2)}.json`)
  })

  afterEach(() => {
    try {
      if (fs.existsSync(tmpPath)) fs.unlinkSync(tmpPath)
    } catch {}
  })

  it('increments nudgesShown, nudgesAccepted, and nudgesDismissed cleanly', () => {
    const store = new NudgeTelemetryStore(tmpPath)
    expect(store.getStats()).toEqual({
      nudgesShown: 0,
      nudgesAccepted: 0,
      nudgesDismissed: 0,
      thresholdMultiplier: 1.0,
      cooldownMs: 90000
    })

    store.recordShown()
    expect(store.getStats().nudgesShown).toBe(1)

    store.recordAccepted()
    expect(store.getStats().nudgesAccepted).toBe(1)

    store.recordDismissed()
    expect(store.getStats().nudgesDismissed).toBe(1)
  })

  it('keeps thresholdMultiplier at 1.0x while nudgesShown < 5 regardless of dismissals', () => {
    const store = new NudgeTelemetryStore(tmpPath)
    // 4 nudges shown, all 4 dismissed (100% dismissal rate)
    for (let i = 0; i < 4; i++) {
      store.recordShown()
      store.recordDismissed()
    }
    expect(store.getStats().nudgesShown).toBe(4)
    expect(store.getStats().nudgesDismissed).toBe(4)
    expect(store.getStats().thresholdMultiplier).toBe(1.0)
    expect(store.getStats().cooldownMs).toBe(90000)
  })

  it('automatically increases thresholdMultiplier (up to 3.0x) and cooldownMs when dismissals dominate at >= 5 nudges', () => {
    const store = new NudgeTelemetryStore(tmpPath)

    // Set up 5 nudges: 4 dismissed, 1 accepted -> dismissalRate = 80% (> 60%)
    for (let i = 0; i < 5; i++) {
      store.recordShown()
      if (i < 4) {
        store.recordDismissed()
      } else {
        store.recordAccepted()
      }
    }

    const stats = store.getStats()
    expect(stats.nudgesShown).toBe(5)
    expect(stats.nudgesDismissed).toBe(4)
    expect(stats.nudgesAccepted).toBe(1)

    // Multiplier must increase above 1.0x
    expect(stats.thresholdMultiplier).toBeGreaterThan(1.0)
    expect(stats.thresholdMultiplier).toBeLessThanOrEqual(3.0)
    expect(stats.cooldownMs).toBeGreaterThan(90000)
  })

  it('resets thresholdMultiplier to 1.0x when acceptance dominates', () => {
    const store = new NudgeTelemetryStore(tmpPath)

    // 5 nudges: 4 accepted, 1 dismissed -> dismissalRate = 20% (< 60%)
    for (let i = 0; i < 5; i++) {
      store.recordShown()
      if (i === 0) {
        store.recordDismissed()
      } else {
        store.recordAccepted()
      }
    }

    const stats = store.getStats()
    expect(stats.thresholdMultiplier).toBe(1.0)
    expect(stats.cooldownMs).toBe(90000)
  })

  it('persists telemetry counters and thresholds cleanly across disk reload', () => {
    const store = new NudgeTelemetryStore(tmpPath)
    for (let i = 0; i < 5; i++) {
      store.recordShown()
      store.recordDismissed()
    }
    const initialStats = store.getStats()

    // Reload from file
    const loadedStore = new NudgeTelemetryStore(tmpPath)
    expect(loadedStore.getStats()).toEqual(initialStats)
  })
})
