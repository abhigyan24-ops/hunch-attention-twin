import fs from 'node:fs'
import path from 'node:path'
import type { TelemetryStats } from '../../shared/types'

export class NudgeTelemetryStore {
  private stats: TelemetryStats = {
    nudgesShown: 0,
    nudgesAccepted: 0,
    nudgesDismissed: 0,
    thresholdMultiplier: 1.0,
    cooldownMs: 90_000
  }

  constructor(private filePath: string) {
    this.load()
  }

  recordShown() {
    this.stats.nudgesShown++
    this.recomputeThresholds()
    this.save()
  }

  recordAccepted() {
    this.stats.nudgesAccepted++
    this.recomputeThresholds()
    this.save()
  }

  recordDismissed() {
    this.stats.nudgesDismissed++
    this.recomputeThresholds()
    this.save()
  }

  recomputeThresholds() {
    const shown = Math.max(1, this.stats.nudgesShown)
    const dismissalRate = this.stats.nudgesDismissed / shown

    // Requirement #4 Auto-Backoff Rule:
    // When nudgesShown >= 5 and (dismissalRate > 0.60 or nudgesDismissed > nudgesAccepted),
    // automatically increase thresholdMultiplier (up to 3.0x) and cooldownMs.
    if (
      this.stats.nudgesShown >= 5 &&
      (dismissalRate > 0.60 || this.stats.nudgesDismissed > this.stats.nudgesAccepted)
    ) {
      const excess = Math.max(0.1, dismissalRate - 0.5)
      this.stats.thresholdMultiplier = Number(Math.min(3.0, 1.0 + excess * 2.5).toFixed(2))
      this.stats.cooldownMs = Math.round(90_000 * (1 + this.stats.nudgesDismissed * 0.5))
    } else {
      this.stats.thresholdMultiplier = 1.0
      this.stats.cooldownMs = 90_000
    }
  }

  getStats(): TelemetryStats {
    return { ...this.stats }
  }

  private load() {
    try {
      if (fs.existsSync(this.filePath)) {
        const parsed = JSON.parse(fs.readFileSync(this.filePath, 'utf8'))
        this.stats = { ...this.stats, ...parsed }
        this.recomputeThresholds()
      }
    } catch {
      // Keep defaults on read error
    }
  }

  private save() {
    try {
      fs.mkdirSync(path.dirname(this.filePath), { recursive: true })
      fs.writeFileSync(this.filePath, JSON.stringify(this.stats, null, 2), 'utf8')
    } catch {
      // Ignore write failures in restricted sandbox
    }
  }
}
