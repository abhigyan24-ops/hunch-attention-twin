/** [ASSUMED] Review proposal only; not imported by production and not a wire protocol. */
import type {PetInputs} from '../../src/pet/petInputBus';
export type PipLevel = 0 | 1 | 2 | 3 | 4;
export type PipCategory = 'coding'|'docs'|'learning'|'social'|'short_video'|'games'|'shopping'|'news'|'video'|'unknown';
export type PipSurface = 'feed'|'shorts'|'watch'|'chat'|'editor'|'other';
export type PipReason = 'no_data'|'stale'|'offline'|'no_recent_work'|'working'|'distraction'|'second_screen'|'quiet_hours'|'snoozed'|'break'|'dnd'|'budget'|'cooldown'|'dismissed';
export interface PipEvidenceFrame {
  nowMs:number;
  local:PetInputs['local'];
  collector:PetInputs['collector'];
  category:PipCategory|null;
  categoryTsMs:number|null;
  surface:PipSurface|null;
  surfaceTsMs:number|null;
  browserActive:boolean;
  declaredSession:PetInputs['declaredSession'];
  remote:null|{ts:number;connected:boolean;heldCount:number;secondScreenOverlap:boolean;
    petEvent?:{kind:'held'|'released'|'nudge'|'celebrate';ts:number}};
  storagePaused:boolean;
}
export interface PipRespectContext {
  localDayKey:number; localMinuteOfDay:number;
  breakUntilMs:number; snoozeUntilMs:number; dnd:boolean; calm:boolean;
  reducedMotion:boolean; reducedText:boolean; paused:boolean;
}
export interface PipAssessment {
  target:PipLevel; distractionMs:number; episodeId:number|null;
  reason:PipReason; recoveryCelebrate:boolean;
}
export interface PipDecision {
  level:PipLevel; motion:'none'|'edge_slide'; showBubble:boolean;
  retireAtMs:number|null; reason:PipReason;
}
export interface PipPosition {
  displayId:number;relX:number;relY:number;
  anchor:'free'|'top_left'|'top_right'|'bottom_left'|'bottom_right'|'left'|'right'|'top'|'bottom';
}
/** Counter values are numbers only; no rendered text or originating content is stored. */
export interface PipDailyCounter {dayKey:number;level:PipLevel;shown:number;accepted:number;dismissed:number}
export type PipAction='back_to_task'|'break_15'|'not_distracted'|'start_focus_25'|'pause'|'open_panel';
