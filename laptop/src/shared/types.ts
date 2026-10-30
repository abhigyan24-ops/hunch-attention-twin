export type PetEmotion =
  | 'FOCUSED'
  | 'HAPPY'
  | 'PROUD'
  | 'CURIOUS'
  | 'SKEPTICAL'
  | 'POUTING'
  | 'SAD'
  | 'ANGRY'
  | 'SLEEPY'
  | 'DIZZY'

export type PresenceMode =
  | 'HIDDEN'
  | 'GHOST_FLOW'
  | 'COMPANION_EDGE'
  | 'INTERVENTION_WALK_IN'

export type AlignmentKind = 'PRODUCTIVE' | 'NEUTRAL' | 'DISTRACTED' | 'ERROR_STUCK'

export interface TelemetryStats {
  nudgesShown: number
  nudgesAccepted: number
  nudgesDismissed: number
  thresholdMultiplier: number
  cooldownMs: number
}

export interface PetDirective {
  emotion: PetEmotion
  presence: PresenceMode
  alignment: AlignmentKind
  nudgeText?: string
  isIntervention?: boolean
  telemetry: TelemetryStats
}

export interface PetBridgeApi {
  onDirective: (cb: (directive: PetDirective) => void) => () => void
  setIgnoreMouse: (ignore: boolean) => void
  nudgeAccepted: () => void
  nudgeDismissed: (reason: 'button' | 'flung' | 'timeout') => void
  petBooped: () => void
}
