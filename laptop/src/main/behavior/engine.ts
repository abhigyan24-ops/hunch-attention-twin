import { ipcMain } from 'electron'
import { classifyContext, type ScreenSignalInput } from '../../services/ai/classifier'
import { NudgeTelemetryStore } from './telemetry'
import { broadcastDirective } from '../windows/overlay'
import type { PetDirective, PetEmotion, PresenceMode } from '../../shared/types'

export class PetDistractionEngine {
  private telemetry: NudgeTelemetryStore
  private distractionStartedAt: number | null = null
  private lastNudgeTime = 0
  private celebrateUntil = 0
  private baseThresholdSec = 12
  private ipcWired = false

  private currentDirective: PetDirective = {
    emotion: 'FOCUSED',
    presence: 'GHOST_FLOW',
    alignment: 'PRODUCTIVE',
    telemetry: {
      nudgesShown: 0,
      nudgesAccepted: 0,
      nudgesDismissed: 0,
      thresholdMultiplier: 1.0,
      cooldownMs: 90000
    }
  }

  constructor(telemetryFilePath: string) {
    this.telemetry = new NudgeTelemetryStore(telemetryFilePath)
    this.wireIpc()
  }

  private wireIpc() {
    if (this.ipcWired) return
    this.ipcWired = true

    ipcMain.on('pet:nudge-accepted', () => {
      this.handleNudgeAccepted(Date.now())
    })

    ipcMain.on('pet:nudge-dismissed', (_e, reason: unknown) => {
      const r = typeof reason === 'string' ? reason : 'button'
      this.handleNudgeDismissed(r, Date.now())
    })

    ipcMain.on('pet:booped', () => {
      // Pet boop interaction
    })
  }

  handleNudgeAccepted(now: number) {
    this.telemetry.recordAccepted()
    this.celebrateUntil = now + 3500
    this.distractionStartedAt = null

    this.currentDirective = {
      emotion: 'HAPPY',
      presence: 'GHOST_FLOW',
      alignment: 'PRODUCTIVE',
      nudgeText: 'Back on track! 🌿',
      telemetry: this.telemetry.getStats()
    }
    broadcastDirective(this.currentDirective)
  }

  handleNudgeDismissed(_reason: string, _now: number) {
    this.telemetry.recordDismissed()
    this.distractionStartedAt = null

    this.currentDirective = {
      emotion: 'FOCUSED',
      presence: 'COMPANION_EDGE',
      alignment: 'NEUTRAL',
      telemetry: this.telemetry.getStats()
    }
    broadcastDirective(this.currentDirective)
  }

  async step(input: ScreenSignalInput, now: number): Promise<PetDirective> {
    const stats = this.telemetry.getStats()
    const effectiveThresholdSec = this.baseThresholdSec * stats.thresholdMultiplier

    // Handle celebration window
    if (now < this.celebrateUntil) {
      this.currentDirective = {
        emotion: 'HAPPY',
        presence: 'GHOST_FLOW',
        alignment: 'PRODUCTIVE',
        telemetry: stats
      }
      broadcastDirective(this.currentDirective)
      return this.currentDirective
    }

    // Measure distraction duration
    let distractionSec = 0
    if (this.distractionStartedAt !== null) {
      distractionSec = Math.max(0, (now - this.distractionStartedAt) / 1000)
    }

    const result = await classifyContext(input, distractionSec, effectiveThresholdSec)

    if (result.alignment === 'DISTRACTED') {
      if (this.distractionStartedAt === null) {
        this.distractionStartedAt = now
        distractionSec = 0
      }
    } else {
      // If user switched back to productive within 10s of an active nudge, count as accepted!
      if (
        result.alignment === 'PRODUCTIVE' &&
        this.currentDirective.presence === 'INTERVENTION_WALK_IN' &&
        now - this.lastNudgeTime <= 10000
      ) {
        this.handleNudgeAccepted(now)
        return this.currentDirective
      }
      this.distractionStartedAt = null
    }

    let emotion: PetEmotion = result.emotion
    let presence: PresenceMode = result.presence
    let isIntervention = false
    let nudgeText = result.nudgeText

    // Check cooldown before firing an intervention walk-in
    if (presence === 'INTERVENTION_WALK_IN') {
      const cooldownElapsed = now - this.lastNudgeTime >= stats.cooldownMs
      if (cooldownElapsed) {
        this.lastNudgeTime = now
        this.telemetry.recordShown()
        isIntervention = true
      } else {
        // Cooldown active -> stay at edge in SKEPTICAL mode instead of repeatedly walking in
        presence = 'COMPANION_EDGE'
        emotion = 'SKEPTICAL'
        nudgeText = undefined
      }
    }

    this.currentDirective = {
      emotion,
      presence,
      alignment: result.alignment,
      nudgeText,
      isIntervention,
      telemetry: this.telemetry.getStats()
    }

    broadcastDirective(this.currentDirective)
    return this.currentDirective
  }

  getTelemetryStore(): NudgeTelemetryStore {
    return this.telemetry
  }

  getCurrentDirective(): PetDirective {
    return this.currentDirective
  }
}
