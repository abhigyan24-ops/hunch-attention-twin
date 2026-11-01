export type FieldType = 'number' | 'string' | 'boolean' | 'object' | 'array';

export interface FieldRule {
  type: FieldType;
  allowedValues?: string[];
  maxStringLength?: number;
  properties?: Record<string, FieldRule>;
}

export type EventSchema = Record<string, FieldRule>;

export const EVENT_SCHEMAS: Record<string, EventSchema> = {
  hello: {
    v: { type: 'number' },
    instanceId: { type: 'string', maxStringLength: 64 },
    windowId: { type: 'string', maxStringLength: 64 },
    vscodeVersion: { type: 'string', maxStringLength: 64 },
    platform: { type: 'string', maxStringLength: 64 },
    remote: { type: 'boolean' },
    capabilities: {
      type: 'object',
      properties: {
        windowStateActive: { type: 'boolean' },
        shellIntegration: { type: 'boolean' },
        tabGroups: { type: 'boolean' },
        gitApi: { type: 'boolean' },
        changeReason: { type: 'boolean' },
        remoteName: { type: 'string', maxStringLength: 64 }
      }
    }
  },

  ed_summary: {
    type: { type: 'string', allowedValues: ['ed_summary'] },
    seq: { type: 'number' },
    ts: { type: 'number' },
    mono: { type: 'number' },
    windowStart: { type: 'number' },
    durMs: { type: 'number' },
    focusedMs: { type: 'number' },
    activeMs: { type: 'number' },
    taskKey: { type: 'string', maxStringLength: 16 },
    taskType: {
      type: 'string',
      allowedValues: ['debugging', 'testing', 'coding', 'reading', 'idle']
    },
    lang: { type: 'string', maxStringLength: 64 },
    typingChars: { type: 'number' },
    deletions: { type: 'number' },
    undo: { type: 'number' },
    redo: { type: 'number' },
    pasteLike: { type: 'number' },
    bulk: { type: 'number' },
    external: { type: 'number' },
    cursorMoves: { type: 'number' },
    scrolls: { type: 'number' },
    fileSwitches: { type: 'number' },
    saves: { type: 'number' },
    diagErr: { type: 'number' },
    diagWarn: { type: 'number' },
    dirtyDocs: { type: 'number' },
    openTabs: { type: 'number' },
    debugActive: { type: 'boolean' },
    debugPaused: { type: 'boolean' },
    uncommitted: { type: 'number' },
    gapP50Ms: { type: 'number' },
    gapP90Ms: { type: 'number' },
    longPauseRatio: { type: 'number' }
  },

  ed_blur: {
    type: { type: 'string', allowedValues: ['ed_blur'] },
    seq: { type: 'number' },
    ts: { type: 'number' },
    mono: { type: 'number' }
  },

  ed_focus: {
    type: { type: 'string', allowedValues: ['ed_focus'] },
    seq: { type: 'number' },
    ts: { type: 'number' },
    mono: { type: 'number' },
    awayMs: { type: 'number' }
  },

  ed_break: {
    type: { type: 'string', allowedValues: ['ed_break'] },
    seq: { type: 'number' },
    ts: { type: 'number' },
    mono: { type: 'number' },
    kind: {
      type: 'string',
      allowedValues: [
        'save_green',
        'test_ok',
        'build_ok',
        'commit',
        'branch_switch',
        'debug_end',
        'file_closed_clean',
        'idle_after_save'
      ]
    },
    confidence: { type: 'number' }
  },

  ed_debug: {
    type: { type: 'string', allowedValues: ['ed_debug'] },
    seq: { type: 'number' },
    ts: { type: 'number' },
    mono: { type: 'number' },
    action: {
      type: 'string',
      allowedValues: ['start', 'pause', 'continue', 'stop']
    }
  },

  ed_task: {
    type: { type: 'string', allowedValues: ['ed_task'] },
    seq: { type: 'number' },
    ts: { type: 'number' },
    mono: { type: 'number' },
    taskCategory: {
      type: 'string',
      allowedValues: ['build', 'test', 'lint', 'run', 'other']
    },
    exitCode: { type: 'number' }
  },

  ed_term: {
    type: { type: 'string', allowedValues: ['ed_term'] },
    seq: { type: 'number' },
    ts: { type: 'number' },
    mono: { type: 'number' },
    commandCategory: {
      type: 'string',
      allowedValues: ['git', 'build', 'test', 'lint', 'run', 'nav', 'edit', 'pkg', 'other']
    },
    exitCode: { type: 'number' }
  },

  ed_git: {
    type: { type: 'string', allowedValues: ['ed_git'] },
    seq: { type: 'number' },
    ts: { type: 'number' },
    mono: { type: 'number' },
    action: {
      type: 'string',
      allowedValues: ['commit', 'branch_switch', 'checkout', 'stash', 'pull', 'push']
    },
    uncommittedCount: { type: 'number' }
  },

  ed_resume: {
    type: { type: 'string', allowedValues: ['ed_resume'] },
    seq: { type: 'number' },
    ts: { type: 'number' },
    mono: { type: 'number' },
    awayMs: { type: 'number' },
    assist: { type: 'boolean' },
    propensity: { type: 'number' },
    experiment: { type: 'boolean' },
    used: { type: 'boolean' },
    lagMs: { type: 'number' },
    resumed: { type: 'boolean' },
    taskKey: { type: 'string', maxStringLength: 16 },
    load: {
      type: 'object',
      properties: {
        dirtyDocs: { type: 'number' },
        openTabs: { type: 'number' },
        debugPaused: { type: 'boolean' },
        uncommitted: { type: 'number' },
        taskType: {
          type: 'string',
          allowedValues: ['debugging', 'testing', 'coding', 'reading', 'idle']
        }
      }
    }
  },

  ed_session: {
    type: { type: 'string', allowedValues: ['ed_session'] },
    seq: { type: 'number' },
    ts: { type: 'number' },
    mono: { type: 'number' },
    action: {
      type: 'string',
      allowedValues: ['start', 'end', 'cancel']
    },
    durationMs: { type: 'number' },
    intentionHash: { type: 'string', maxStringLength: 16 },
    intentionLength: { type: 'number' },
    focusRating: { type: 'number' },
    energyRating: { type: 'number' }
  },

  ed_feedback: {
    type: { type: 'string', allowedValues: ['ed_feedback'] },
    seq: { type: 'number' },
    ts: { type: 'number' },
    mono: { type: 'number' },
    id: { type: 'string', maxStringLength: 64 },
    target: { type: 'string', maxStringLength: 64 },
    action: {
      type: 'string',
      allowedValues: ['useful', 'not_useful', 'dismiss', 'click']
    }
  }
};
