export type PipBehavior =
  | 'deep_focus'
  | 'held'
  | 'drifting'
  | 'distracted'
  | 'recovering'
  | 'fatigued'
  | 'second_screen_overlap';

export interface PetState {
  behavior: PipBehavior;
  provenance: 'pending' | 'observed';
  lastBubbleText: string;
  lastBubbleTs: number;
  heldCount: number;
}

export const initialPetState: PetState = {
  behavior: 'deep_focus',
  provenance: 'observed',
  lastBubbleText: 'Pip is watching your focus flow quietly.',
  lastBubbleTs: 0,
  heldCount: 1
};

export const PIP_MESSAGES: Record<PipBehavior, string[]> = {
  deep_focus: [
    'Zzz... in deep flow. Not disturbing you.',
    'Flowing quietly in VS Code.',
    'Pip is resting while you build.'
  ],
  held: [
    '1 notification held for your next natural break.',
    'Batched a social ping. Stay in the zone!',
    'Held quietly: focus cost would have been 24 min.'
  ],
  drifting: [
    'Still working on this task block, or drifting?',
    'Window switch detected. Need a quick pause?',
    'Gentle nudge: ready to return to your editor?'
  ],
  distracted: [
    'Off-task activity detected. Tap to resume flow.',
    'Distraction hazard high: 14 min elapsed off-task.',
    'Pip is waiting for you back in the editor!'
  ],
  recovering: [
    'Welcome back into flow! Nicely resumed.',
    'First edit burst detected. Resumption complete!',
    'Flow re-established.'
  ],
  fatigued: [
    '90 min of continuous work. Time for water & a stretch?',
    'Attention fatigue rising. A 5 min break preserves tonight.',
    'Yawwwwn... even Pip suggests a quick break.'
  ],
  second_screen_overlap: [
    'Phone and laptop both busy? Pip noticed the dual-screen split.',
    'Dual-screen hazard: attention divided between two surfaces.',
    'Cross-device split detected.'
  ]
};

const COOLDOWN_MS = 10 * 60 * 1000; // 10 min cooldown per Spec 12.5

export function getPipMessage(
  behavior: PipBehavior,
  lastShownTs: number,
  now: number = Date.now(),
  force: boolean = false
): { text: string; updatedTs: number } | null {
  if (!force && lastShownTs > 0 && now - lastShownTs < COOLDOWN_MS) {
    return null;
  }
  const messages = PIP_MESSAGES[behavior];
  const text = messages[Math.floor(Math.random() * messages.length)];
  return { text, updatedTs: now };
}
