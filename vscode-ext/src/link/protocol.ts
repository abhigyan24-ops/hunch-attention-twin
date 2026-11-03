import type { ExtensionCapabilities } from '../capabilities.ts';

export interface HelloMessage {
  kind?: 'hello';
  v: number;
  instanceId: string;
  windowId: string;
  vscodeVersion: string;
  platform: string;
  remote: boolean;
  capabilities: ExtensionCapabilities;
}

export interface EventsBatchMessage {
  kind: 'events';
  batchId: string;
  windowId?: string;
  events: Record<string, unknown>[];
}

export interface FeedbackMessage {
  kind: 'feedback';
  id: string;
  target: string;
  action: 'useful' | 'not_useful' | 'dismiss' | 'click';
}

export interface PingMessage {
  kind: 'ping';
}

export type ClientMessage =
  | ({ type: 'hello' } & HelloMessage)
  | EventsBatchMessage
  | FeedbackMessage
  | PingMessage;

// Engine -> Client Messages

export interface WelcomeMessage {
  kind: 'welcome';
  ackedUpTo: number;
  cfg?: Record<string, unknown>;
}

export interface AckMessage {
  kind: 'ack';
  batchId?: string;
  upTo: number;
}

export interface StateMessage {
  kind: 'state';
  focus: 'deep' | 'normal';
  leaseMs: number;
  version: number;
}

export interface DigestMessage {
  kind: 'digest';
  items: Array<{
    id: string;
    title?: string;
    category?: string;
    ts?: number;
  }>;
}

export interface NudgeMessage {
  kind: 'nudge';
  id: string;
  kindName?: string;
  text?: string;
}

export interface ConfigMessage {
  kind: 'cfg';
  config: Record<string, unknown>;
}

export interface CommandMessage {
  kind: 'cmd';
  action: 'pause' | 'resume' | 'forget_range';
  rangeStart?: number;
  rangeEnd?: number;
}

export interface PongMessage {
  kind: 'pong';
}

export type EngineMessage =
  | WelcomeMessage
  | AckMessage
  | StateMessage
  | DigestMessage
  | NudgeMessage
  | ConfigMessage
  | CommandMessage
  | PongMessage;

/**
 * Safely parses and validates messages received from the local engine.
 * Returns null for any malformed message without throwing an exception.
 */
export function parseEngineMessage(raw: unknown): EngineMessage | null {
  if (raw === null || raw === undefined) return null;

  let data: any = raw;
  if (typeof raw === 'string') {
    try {
      data = JSON.parse(raw);
    } catch {
      return null;
    }
  }

  if (typeof data !== 'object' || Array.isArray(data)) {
    return null;
  }

  const kind = data.kind;
  if (typeof kind !== 'string') return null;

  switch (kind) {
    case 'welcome':
      if (typeof data.ackedUpTo !== 'number') return null;
      return {
        kind: 'welcome',
        ackedUpTo: data.ackedUpTo,
        cfg: typeof data.cfg === 'object' ? data.cfg : undefined
      };

    case 'ack':
      if (typeof data.upTo !== 'number') return null;
      return {
        kind: 'ack',
        batchId: typeof data.batchId === 'string' ? data.batchId : undefined,
        upTo: data.upTo
      };

    case 'state':
      if (data.focus !== 'deep' && data.focus !== 'normal') return null;
      if (typeof data.leaseMs !== 'number' || typeof data.version !== 'number') return null;
      return {
        kind: 'state',
        focus: data.focus,
        leaseMs: data.leaseMs,
        version: data.version
      };

    case 'digest':
      if (!Array.isArray(data.items)) return null;
      return {
        kind: 'digest',
        items: data.items
      };

    case 'nudge':
      if (typeof data.id !== 'string') return null;
      return {
        kind: 'nudge',
        id: data.id,
        kindName: typeof data.kindName === 'string' ? data.kindName : undefined,
        text: typeof data.text === 'string' ? data.text : undefined
      };

    case 'cfg':
      if (typeof data.config !== 'object') return null;
      return {
        kind: 'cfg',
        config: data.config
      };

    case 'cmd':
      if (data.action !== 'pause' && data.action !== 'resume' && data.action !== 'forget_range') {
        return null;
      }
      return {
        kind: 'cmd',
        action: data.action,
        rangeStart: typeof data.rangeStart === 'number' ? data.rangeStart : undefined,
        rangeEnd: typeof data.rangeEnd === 'number' ? data.rangeEnd : undefined
      };

    case 'pong':
      return { kind: 'pong' };

    default:
      return null;
  }
}
