import type { Clock } from '../clock.ts';
import type { EditKind } from './classify.ts';
import { RhythmTracker } from './rhythm.ts';

export interface WindowContext {
  taskKey: string;
  taskType: 'debugging' | 'testing' | 'coding' | 'reading' | 'idle';
  dirtyDocs: number;
  openTabs: number;
  uncommitted: number;
}

export class WindowAggregator {
  private rhythm = new RhythmTracker();

  // Accumulator counters for the current 10s window
  private typingChars = 0;
  private deletions = 0;
  private undoCount = 0;
  private redoCount = 0;
  private pasteCount = 0;
  private bulkCount = 0;
  private externalCount = 0;
  private cursorMoves = 0;
  private scrolls = 0;
  private fileSwitches = 0;
  private saves = 0;

  // Current states
  private diagErr = 0;
  private diagWarn = 0;
  private debugActive = false;
  private debugPaused = false;
  private activeLang = 'plaintext';

  // Context load
  private context: WindowContext = {
    taskKey: '0000000000000000',
    taskType: 'idle',
    dirtyDocs: 0,
    openTabs: 0,
    uncommitted: 0
  };

  // Focus and Active duration tracking
  private isFocused = true;
  private isActive = true;
  private lastMono: number;
  private windowStartNow: number;
  private accumulatedFocusedMs = 0;
  private accumulatedActiveMs = 0;

  constructor(clock: Clock) {
    this.lastMono = clock.mono();
    this.windowStartNow = clock.now();
  }

  onEdit(kind: EditKind, ins: number, del: number, nowMs?: number): void {
    this.deletions += del;

    switch (kind) {
      case 'typing':
        this.typingChars += ins;
        if (nowMs !== undefined) {
          this.rhythm.recordTyping(nowMs);
        }
        break;
      case 'undo':
        this.undoCount++;
        break;
      case 'redo':
        this.redoCount++;
        break;
      case 'paste':
        this.pasteCount++;
        break;
      case 'bulk':
        this.bulkCount++;
        break;
      case 'external':
        this.externalCount++;
        break;
    }
  }

  onCursorMove(): void {
    this.cursorMoves++;
  }

  onScroll(): void {
    this.scrolls++;
  }

  onFileSwitch(lang?: string): void {
    this.fileSwitches++;
    if (lang) {
      this.activeLang = lang.slice(0, 64);
    }
  }

  onSave(): void {
    this.saves++;
  }

  onWindow(focused: boolean, active: boolean, monoMs: number): void {
    // Flush elapsed time into current accumulators
    this.accumulateTime(monoMs);
    this.isFocused = focused;
    this.isActive = active;
  }

  onDiagnostics(err: number, warn: number): void {
    this.diagErr = err;
    this.diagWarn = warn;
  }

  onDebug(phase: 'start' | 'pause' | 'continue' | 'stop'): void {
    if (phase === 'start') {
      this.debugActive = true;
      this.debugPaused = false;
    } else if (phase === 'pause') {
      this.debugActive = true;
      this.debugPaused = true;
    } else if (phase === 'continue') {
      this.debugActive = true;
      this.debugPaused = false;
    } else if (phase === 'stop') {
      this.debugActive = false;
      this.debugPaused = false;
    }
  }

  setContext(ctx: Partial<WindowContext>): void {
    this.context = {
      ...this.context,
      ...ctx
    };
  }

  private accumulateTime(currentMono: number): void {
    const delta = Math.max(0, currentMono - this.lastMono);
    if (this.isFocused) {
      this.accumulatedFocusedMs += delta;
    }
    if (this.isActive) {
      this.accumulatedActiveMs += delta;
    }
    this.lastMono = currentMono;
  }

  /**
   * Ticks the 10-second window.
   * Returns an `ed_summary` payload or `null` if completely inactive with zero edits.
   */
  tick(clock: Clock): Record<string, unknown> | null {
    const currentMono = clock.mono();
    const currentNow = clock.now();
    this.accumulateTime(currentMono);

    const actualDurMs = Math.max(1000, currentNow - this.windowStartNow);

    const focusedMs = Math.min(Math.round(this.accumulatedFocusedMs), actualDurMs);
    const activeMs = Math.min(Math.round(this.accumulatedActiveMs), actualDurMs);

    // Activity check: If no focused time, no active time, and no edit/navigation activity, return null
    const hasActivity =
      this.typingChars > 0 ||
      this.deletions > 0 ||
      this.undoCount > 0 ||
      this.redoCount > 0 ||
      this.pasteCount > 0 ||
      this.bulkCount > 0 ||
      this.externalCount > 0 ||
      this.cursorMoves > 0 ||
      this.scrolls > 0 ||
      this.fileSwitches > 0 ||
      this.saves > 0 ||
      focusedMs > 0 ||
      activeMs > 0;

    if (!hasActivity) {
      this.windowStartNow = currentNow;
      return null;
    }

    const rhythmStats = this.rhythm.getStats();

    const summary: Record<string, unknown> = {
      type: 'ed_summary',
      windowStart: this.windowStartNow,
      durMs: actualDurMs,
      focusedMs,
      activeMs,
      taskKey: this.context.taskKey,
      taskType: this.context.taskType,
      lang: this.activeLang,
      typingChars: this.typingChars,
      deletions: this.deletions,
      undo: this.undoCount,
      redo: this.redoCount,
      pasteLike: this.pasteCount,
      bulk: this.bulkCount,
      external: this.externalCount,
      cursorMoves: this.cursorMoves,
      scrolls: this.scrolls,
      fileSwitches: this.fileSwitches,
      saves: this.saves,
      diagErr: this.diagErr,
      diagWarn: this.diagWarn,
      dirtyDocs: this.context.dirtyDocs,
      openTabs: this.context.openTabs,
      debugActive: this.debugActive,
      debugPaused: this.debugPaused,
      uncommitted: this.context.uncommitted,
      gapP50Ms: rhythmStats.gapP50Ms,
      gapP90Ms: rhythmStats.gapP90Ms,
      longPauseRatio: rhythmStats.longPauseRatio
    };

    // Reset accumulators for next window
    this.typingChars = 0;
    this.deletions = 0;
    this.undoCount = 0;
    this.redoCount = 0;
    this.pasteCount = 0;
    this.bulkCount = 0;
    this.externalCount = 0;
    this.cursorMoves = 0;
    this.scrolls = 0;
    this.fileSwitches = 0;
    this.saves = 0;
    this.accumulatedFocusedMs = 0;
    this.accumulatedActiveMs = 0;
    this.windowStartNow = currentNow;

    return summary;
  }
}
