export type PipBehavior='deep_focus'|'held'|'drifting'|'distracted'|'recovering'|'fatigued'|'second_screen_overlap';
// TODO 12.5: all seven animations, cooldowns, paired state, panel interactions.
export interface PetState {behavior:PipBehavior|null;provenance:'pending'|'observed';}
export const initialPetState:PetState={behavior:null,provenance:'pending'};
