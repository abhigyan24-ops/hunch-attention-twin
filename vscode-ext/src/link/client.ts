import { WebSocket } from 'ws';
import { randomBytes } from 'node:crypto';
import type { Outbox } from './outbox.ts';
import type { EndpointInfo } from './endpoint.ts';
import type { ExtensionCapabilities } from '../capabilities.ts';
import { parseEngineMessage, type EngineMessage, type StateMessage } from './protocol.ts';

export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'authenticated';

export interface ClientOptions {
  instanceId?: string;
  windowId?: string;
  vscodeVersion?: string;
  platform?: string;
  remote?: boolean;
  capabilities: ExtensionCapabilities;
  onConnectionChange?: (state: ConnectionState) => void;
  onStateMessage?: (msg: StateMessage) => void;
  onMessage?: (msg: EngineMessage) => void;
}

export class EngineClient {
  private outbox: Outbox;
  private options: ClientOptions;
  private ws: WebSocket | null = null;
  private currentEndpoint: EndpointInfo | null = null;

  private state: ConnectionState = 'disconnected';
  private ackedUpTo: number = 0;

  private flushTimer: ReturnType<typeof setInterval> | null = null;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempts = 0;
  private stopped = false;

  private readonly instanceId: string;
  private readonly windowId: string;

  constructor(outbox: Outbox, options: ClientOptions) {
    this.outbox = outbox;
    this.options = options;
    this.instanceId = options.instanceId || randomBytes(8).toString('hex');
    this.windowId = options.windowId || randomBytes(6).toString('hex');
  }

  setEndpoint(endpoint: EndpointInfo | null): void {
    const changed =
      !this.currentEndpoint ||
      !endpoint ||
      this.currentEndpoint.port !== endpoint.port ||
      this.currentEndpoint.token !== endpoint.token;

    this.currentEndpoint = endpoint;

    if (changed) {
      this.resetBackoff();
      this.reconnect();
    }
  }

  resetBackoff(): void {
    this.reconnectAttempts = 0;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  start(): void {
    this.stopped = false;
    this.startFlushTimer();
    this.startPingTimer();
    this.connect();
  }

  stop(): void {
    this.stopped = true;
    if (this.flushTimer) clearInterval(this.flushTimer);
    if (this.pingTimer) clearInterval(this.pingTimer);
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);

    this.disconnect();
    this.setState('disconnected');
  }

  private setState(newState: ConnectionState): void {
    if (this.state !== newState) {
      this.state = newState;
      this.options.onConnectionChange?.(newState);
    }
  }

  getState(): ConnectionState {
    return this.state;
  }

  getWindowId(): string {
    return this.windowId;
  }

  private connect(): void {
    if (this.stopped || !this.currentEndpoint) {
      this.setState('disconnected');
      return;
    }

    this.disconnect();
    this.setState('connecting');

    const url = `ws://127.0.0.1:${this.currentEndpoint.port}/ext`;

    try {
      this.ws = new WebSocket(url, {
        headers: {
          Authorization: `Bearer ${this.currentEndpoint.token}`
        }
      });
      this.ws.on('error', () => {
        // Silent on connection errors per rule U2
      });
    } catch {
      this.scheduleReconnect();
      return;
    }

    this.ws.on('open', () => {
      this.setState('connected');
      this.reconnectAttempts = 0;
      this.sendHello();
    });

    this.ws.on('message', (data: any) => {
      const msg = parseEngineMessage(data.toString('utf8'));
      if (!msg) return;
      this.handleEngineMessage(msg);
    });

    this.ws.on('close', () => {
      this.setState('disconnected');
      this.disconnect();
      if (!this.stopped) {
        this.scheduleReconnect();
      }
    });

    this.ws.on('error', () => {
      // Rule U2: Silent on connection errors. Handled by close listener
    });
  }

  private disconnect(): void {
    if (this.ws) {
      try {
        this.ws.removeAllListeners();
        this.ws.on('error', () => {});
        if (this.ws.readyState === WebSocket.CONNECTING) {
          this.ws.terminate();
        } else {
          this.ws.close();
        }
      } catch {}
      this.ws = null;
    }
  }

  private scheduleReconnect(): void {
    if (this.stopped || this.reconnectTimer) return;

    this.reconnectAttempts++;
    // Exponential backoff: 1s to 30s with +-20% jitter
    const baseDelay = Math.min(1000 * Math.pow(1.5, this.reconnectAttempts), 30000);
    const jitter = (Math.random() * 0.4 - 0.2) * baseDelay; // -20% to +20%
    const delay = Math.max(500, Math.round(baseDelay + jitter));

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
  }

  public reconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.connect();
  }

  private sendHello(): void {
    const helloPayload = {
      kind: 'hello',
      v: 1,
      instanceId: this.instanceId,
      windowId: this.windowId,
      vscodeVersion: this.options.vscodeVersion || '1.93.0',
      platform: this.options.platform || process.platform,
      remote: Boolean(this.options.remote),
      capabilities: this.options.capabilities
    };
    this.sendRaw(helloPayload);
  }

  private handleEngineMessage(msg: EngineMessage): void {
    this.options.onMessage?.(msg);

    switch (msg.kind) {
      case 'welcome':
        this.setState('authenticated');
        this.ackedUpTo = msg.ackedUpTo;
        // Resend all unacked events starting from ackedUpTo
        this.flushBatch(this.ackedUpTo);
        break;

      case 'ack':
        this.ackedUpTo = Math.max(this.ackedUpTo, msg.upTo);
        this.outbox.ack(msg.upTo);
        break;

      case 'state':
        this.options.onStateMessage?.(msg);
        break;
    }
  }

  private startFlushTimer(): void {
    if (this.flushTimer) clearInterval(this.flushTimer);
    // Flush outbox every 5 seconds
    this.flushTimer = setInterval(() => {
      if (this.state === 'authenticated') {
        this.flushBatch();
      }
    }, 5000);
  }

  private startPingTimer(): void {
    if (this.pingTimer) clearInterval(this.pingTimer);
    this.pingTimer = setInterval(() => {
      if (this.state === 'connected' || this.state === 'authenticated') {
        this.sendRaw({ kind: 'ping' });
      }
    }, 10000);
  }

  /**
   * Immediately flushes unacked events. Called on ed_resume or ed_break.
   */
  public flushImmediate(): void {
    if (this.state === 'authenticated') {
      this.flushBatch();
    }
  }

  public flushBatch(afterSeq?: number): void {
    if (this.state !== 'authenticated') return;

    const startSeq = afterSeq !== undefined ? afterSeq : this.ackedUpTo;
    const batch = this.outbox.getBatchAfter(startSeq, 200);

    if (batch.length === 0) return;

    const batchId = randomBytes(6).toString('hex');
    const msg = {
      kind: 'events',
      batchId,
      windowId: this.windowId,
      events: batch
    };

    this.sendRaw(msg);
  }

  public sendFeedback(id: string, target: string, action: 'useful' | 'not_useful' | 'dismiss' | 'click'): void {
    if (this.state !== 'authenticated') return;
    this.sendRaw({
      kind: 'feedback',
      id,
      target,
      action
    });
  }

  private sendRaw(data: any): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
    try {
      this.ws.send(JSON.stringify(data));
    } catch {}
  }
}
