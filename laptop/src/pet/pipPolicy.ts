/** Pure presentation policy. Explicit clocks and seeded copy selection; no collector or Electron access. */
export const pipConfig = {
  work: ['coding', 'docs', 'learning'], distraction: ['social', 'short_video', 'games', 'shopping', 'news'],
  recentWorkMs: 900000, gapMs: 20000, freshMs: 15000, remoteFreshMs: 30000,
  thresholdsMs: [0, 60000, 180000, 480000, 1200000], watchMultiplier: 2,
  recoveryMs: 60000, overlapBubbleMs: 120000, cooldownMs: [0, 0, 600000, 1200000, 0],
  hourMs: 3600000, hourCap: 4, dayCap: 20, suppressMs: 3600000,
  dismissMultiplier: 1.5, dismissalLimit: 3, quietStart: 1320, quietEnd: 420,
  bubbleMs: 6000, cardMs: 12000, maximumLevel: 4,
} as const;
export type Level = 0 | 1 | 2 | 3 | 4;
export type Category = 'coding'|'docs'|'learning'|'social'|'short_video'|'games'|'shopping'|'news'|'video'|'unknown';
export type Surface = 'feed'|'shorts'|'watch'|'chat'|'editor'|'other';
export interface Evidence {
  nowMs: number; ts: number; healthy: boolean; state: 'flowing'|'stuck'|'idle_open'|'browsing'|'gaming'|'away'|'offline';
  category: Category; surface: Surface; declaredFocus: boolean; storagePaused: boolean;
  remote?: {ts: number; connected: boolean; overlap: boolean};
}
export interface Assessment {level: Level; dwellMs: number; episode: number; celebrate: boolean; reason: string; working: boolean}
export interface Respect {
  minuteOfDay: number; dayKey: number; breakUntilMs: number; snoozeUntilMs: number;
  dnd: boolean; paused: boolean; calm: boolean; reducedMotion: boolean;
}
const includes = (list: readonly string[], value: string) => list.includes(value);
export class DistractionAssessor {
  private last: number|null = null;
  private lastWork: number|null = null;
  private previous: 'work'|'distraction'|'gap'|'other' = 'other';
  private gapStart: number|null = null;
  private dwell = 0;
  private recovery = 0;
  private episode = 0;
  private activeEpisode = false;
  step(input: Evidence, multiplier = 1): Assessment {
    const now = input.nowMs;
    const result = (level: Level, reason: string, celebrate = false, working = false): Assessment =>
      ({level, reason, dwellMs: this.dwell, episode: this.episode, celebrate, working});
    if (!Number.isFinite(now) || now < 0 || this.last !== null && now < this.last) {
      this.reset(); return result(0, 'invalid_clock');
    }
    const delta = this.last === null ? 0 : now - this.last;
    this.last = now;
    const fresh = Number.isFinite(input.ts) && input.ts <= now && now - input.ts < pipConfig.freshMs;
    if (!fresh || !input.healthy || input.state === 'offline' || input.storagePaused) {
      this.gapStart ??= now; this.previous = 'gap'; this.recovery = 0;
      if (now - this.gapStart > pipConfig.gapMs) this.clearEpisode();
      return result(0, 'unavailable');
    }
    const discontinuity = delta > pipConfig.gapMs || this.gapStart !== null && now - this.gapStart > pipConfig.gapMs;
    if (discontinuity) this.clearEpisode();
    this.gapStart = null;
    const work = input.declaredFocus || includes(pipConfig.work, input.category) && ['flowing','stuck','idle_open'].includes(input.state);
    if (work) {
      this.lastWork = now;
      this.recovery = this.previous === 'work' && !discontinuity ? this.recovery + delta : 0;
      const celebrate = this.activeEpisode && this.recovery >= pipConfig.recoveryMs;
      if (celebrate) this.clearEpisode();
      this.previous = 'work';
      return result(0, 'working', celebrate, true);
    }
    if (input.state === 'flowing') { this.previous = 'other'; return result(0, 'working', false, true); }
    // Work context qualifies an episode at entry; otherwise the 20-minute level could never be reached.
    const recentWork = this.activeEpisode || this.lastWork !== null && now - this.lastWork <= pipConfig.recentWorkMs;
    const remote = input.remote;
    const overlap = Boolean(remote?.connected && remote.overlap && remote.ts <= now && now - remote.ts < pipConfig.remoteFreshMs);
    const approvedCategory = includes(pipConfig.distraction, input.category);
    const browsing = input.state === 'browsing' && (approvedCategory || input.surface !== 'watch' && ['feed','shorts'].includes(input.surface));
    if (!recentWork || !(browsing || input.state === 'gaming' || overlap)) {
      this.clearEpisode(); this.previous = 'other'; return result(0, recentWork ? 'no_evidence' : 'no_recent_work');
    }
    if (!this.activeEpisode) { this.episode++; this.activeEpisode = true; }
    if (this.previous === 'distraction' && !discontinuity) this.dwell += delta;
    this.previous = 'distraction'; this.recovery = 0;
    const factor = (input.surface === 'watch' ? pipConfig.watchMultiplier : 1) * Math.max(1, Number.isFinite(multiplier) ? multiplier : 1);
    let level: Level = 0;
    for (let i = 1; i <= pipConfig.maximumLevel; i++) if (this.dwell >= pipConfig.thresholdsMs[i] * factor) level = i as Level;
    if (overlap && this.dwell >= pipConfig.overlapBubbleMs * factor) level = Math.max(level, 2) as Level;
    return result(level, overlap ? 'second_screen' : 'distraction');
  }
  private clearEpisode() { this.dwell = 0; this.recovery = 0; this.activeEpisode = false; }
  private reset() { this.clearEpisode(); this.last = null; this.lastWork = null; this.previous = 'other'; this.gapStart = null; }
}
export interface Nudge {level: Level; showBubble: boolean; motion: boolean; expiresAt: number|null; reason: string}
export class EscalationLadder {
  private shown: number[] = [];
  private daily = new Map<number, number>();
  private lastLevel = new Map<Level, number>();
  private level4Episode = -1;
  private suppressed = new Map<string, number>();
  private dismissals = 0;
  private pauseUntil = 0;
  private multiplier = 1;
  private lastNow = -1;
  readonly counters: {day: number; level: Level; shown: number; accepted: number; dismissed: number}[] = [];
  thresholdMultiplier() { return this.multiplier; }
  feedback(kind: 'accepted'|'dismissed', day: number, level: Level, category: Category, surface: Surface, now: number) {
    const row = this.counters.find(r => r.day === day && r.level === level);
    if (!row || row.accepted + row.dismissed >= row.shown || !Number.isFinite(now) || now < this.lastNow) return;
    row[kind]++;
    if (kind === 'accepted') { this.dismissals = 0; return; }
    this.suppressed.set(category + ':' + surface, now + pipConfig.suppressMs);
    this.multiplier = pipConfig.dismissMultiplier;
    if (++this.dismissals >= pipConfig.dismissalLimit) { this.pauseUntil = now + pipConfig.suppressMs; this.dismissals = 0; }
  }
  decide(a: Assessment, e: Evidence, respect: Respect): Nudge {
    const no = (reason: string, level: Level = 0): Nudge => ({level, showBubble: false, motion: false, expiresAt: null, reason});
    const now = e.nowMs;
    if (!Number.isFinite(now) || now < this.lastNow || !Number.isInteger(respect.minuteOfDay) || respect.minuteOfDay < 0 || respect.minuteOfDay >= 1440 || !Number.isInteger(respect.dayKey)) return no('invalid_clock');
    this.lastNow = now;
    if (a.working || e.declaredFocus || e.state === 'flowing') return no('working');
    if (!e.healthy || e.state === 'offline' || e.storagePaused || e.ts > now || now - e.ts >= pipConfig.freshMs) return no('unavailable');
    if (respect.paused || respect.dnd || e.category === 'games') return no('paused');
    if (respect.breakUntilMs > now || respect.snoozeUntilMs > now || this.pauseUntil > now) return no('snoozed');
    if (respect.minuteOfDay >= pipConfig.quietStart || respect.minuteOfDay < pipConfig.quietEnd) return no('quiet_hours');
    if ((this.suppressed.get(e.category + ':' + e.surface) ?? 0) > now) return no('dismissed');
    if (a.level < 2) return no(a.reason, a.level);
    this.shown = this.shown.filter(at => now - at < pipConfig.hourMs);
    if (this.shown.length >= pipConfig.hourCap || (this.daily.get(respect.dayKey) ?? 0) >= pipConfig.dayCap) return no('budget');
    const previous = this.lastLevel.get(a.level);
    if (previous !== undefined && now - previous < pipConfig.cooldownMs[a.level]) return no('cooldown');
    if (a.level === 4 && this.level4Episode === a.episode) return no('episode_limit');
    this.lastLevel.set(a.level, now); this.shown.push(now);
    this.daily.set(respect.dayKey, (this.daily.get(respect.dayKey) ?? 0) + 1);
    if (a.level === 4) this.level4Episode = a.episode;
    let row = this.counters.find(r => r.day === respect.dayKey && r.level === a.level);
    if (!row) { row = {day: respect.dayKey, level: a.level, shown: 0, accepted: 0, dismissed: 0}; this.counters.push(row); }
    row.shown++;
    return {level: a.level, showBubble: true, motion: a.level >= 3 && !respect.calm && !respect.reducedMotion,
      expiresAt: now + (a.level === 4 ? pipConfig.cardMs : pipConfig.bubbleMs), reason: a.reason};
  }
}
const copy = [
  'Need a hand getting back?', 'Ready for a small next step?', 'Would a short pause help?', 'Want to return to your plan?',
  'Time for a gentle reset?', 'Shall we make room for focus?', 'Would a stretch feel good?', 'What would feel useful now?',
] as const;
/** Explicit seed; the picker never reads ambient randomness or content. */
export class BubblePolicy {
  private seed: number;
  private previous = -1;
  constructor(seed: number) { this.seed = seed >>> 0; }
  next(level: Level, reducedText = false): string|null {
    if (level < 2 || reducedText) return null;
    this.seed = (Math.imul(this.seed, 1664525) + 1013904223) >>> 0;
    const index = (this.previous + 1 + this.seed % (copy.length - 1)) % copy.length;
    this.previous = index;
    return copy[index];
  }
}
