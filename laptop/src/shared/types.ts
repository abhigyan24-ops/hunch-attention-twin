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
  | 'DIZZY';

export type PresenceMode =
  | 'HIDDEN'
  | 'GHOST_FLOW'
  | 'COMPANION_EDGE'
  | 'INTERVENTION_WALK_IN';

export type AlignmentKind = 'PRODUCTIVE' | 'NEUTRAL' | 'DISTRACTED' | 'ERROR_STUCK';

export type CascadeTier =
  | 'TIER_1_OLLAMA'
  | 'TIER_2_GEMINI'
  | 'TIER_3_GROQ'
  | 'TIER_4_REGEX_RULES';

export interface ScreenSignalInput {
  declaredGoal?: string;
  app?: string;
  title?: string;
  visibleText?: string;
  category?: string;
  surface?: string;
  screenshotBuffer?: Buffer | Uint8Array;
  typingRate?: number;
  idleSec?: number;
  dnd?: boolean;
}

export interface RawTierResult {
  alignment: AlignmentKind;
  nudgeText?: string;
  confidence?: number;
  reason?: string;
}

export interface ClassificationOutput {
  alignment: AlignmentKind;
  emotion: PetEmotion;
  presence: PresenceMode;
  tierUsed: CascadeTier;
  nudgeText?: string;
  confidence: number;
  reason: string;
}
