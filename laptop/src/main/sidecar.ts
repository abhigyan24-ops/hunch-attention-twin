import {spawn,type ChildProcess} from 'node:child_process';
import {join} from 'node:path';
import type {CategorizedEvent} from './jsonlStore';

export interface TwinState {
 focus_depth: number;
 fatigue_p: number;
 held_count: number;
 alignment: number;
 pet: {
  mode: string;
  opacity: number;
  emotion: string;
  threshold_multiplier: number;
 };
 ts: number;
}

export class SidecarManager {
 private process: ChildProcess | null = null;
 private running = false;
 private buffer: Array<{ts: number; type: string; payload: string}> = [];
 private flushTimer: ReturnType<typeof setInterval> | null = null;
 private lastClockOffset = 0;
 readonly port = 8765;
 readonly baseUrl = 'http://127.0.0.1:8765';

 constructor(private readonly projectRoot: string) {}

 async start(): Promise<boolean> {
  if (this.running) return true;
  this.running = true;

  // 1. Check if sidecar is already running (e.g. independently started)
  if (await this.isHealthy()) {
   console.info('[Sidecar] Engine already running on port', this.port);
   this.startFlushLoop();
   return true;
  }

  // 2. Spawn python sidecar
  const pythonBin = process.platform === 'win32' ? 'python' : 'python3';
  try {
   this.process = spawn(
    pythonBin,
    ['-m', 'uvicorn', 'engine.twin.api.app:app', '--host', '127.0.0.1', '--port', String(this.port), '--no-access-log'],
    {
     cwd: this.projectRoot,
     env: {...process.env, PYTHONUNBUFFERED: '1'},
     stdio: ['ignore', 'pipe', 'pipe'],
    }
   );

   this.process.stdout?.on('data', (d: Buffer) => {
    const msg = d.toString();
    if (msg.includes('Uvicorn running') || msg.includes('Application startup complete')) {
     console.info('[Sidecar Output]', msg.trim());
    }
   });

   this.process.stderr?.on('data', (d: Buffer) => {
    const err = d.toString();
    if (!err.includes('INFO:')) {
     console.warn('[Sidecar Error]', err.trim());
    }
   });

   this.process.on('exit', code => {
    console.info('[Sidecar] Process exited with code', code);
    this.process = null;
   });

   // Wait up to 8 seconds for health check
   for (let i = 0; i < 16; i++) {
    await new Promise(r => setTimeout(r, 500));
    if (await this.isHealthy()) {
     console.info('[Sidecar] Engine verified healthy on port', this.port);
     this.startFlushLoop();
     return true;
    }
   }
  } catch (err) {
   console.warn('[Sidecar] Could not spawn Python sidecar:', err);
  }

  this.startFlushLoop();
  return false;
 }

 async isHealthy(): Promise<boolean> {
  try {
   const res = await fetch(`${this.baseUrl}/health`, {signal: AbortSignal.timeout(1500)});
   return res.ok;
  } catch {
   return false;
  }
 }

 queueEvent(event: CategorizedEvent): void {
  this.buffer.push({
   ts: event.ts,
   type: event.type,
   payload: JSON.stringify(event.payload ?? {}),
  });
  if (this.buffer.length >= 25) {
   void this.flush();
  }
 }

 private startFlushLoop(): void {
  if (this.flushTimer) clearInterval(this.flushTimer);
  this.flushTimer = setInterval(() => void this.flush(), 10000);
 }

 async flush(): Promise<void> {
  if (this.buffer.length === 0) return;
  const items = [...this.buffer];
  this.buffer = [];

  const sendTs = Date.now();
  const payload = {
   device_id: 'laptop',
   client_send_ts: sendTs,
   events: items.map(it => ({
    device_id: 'laptop',
    ts: it.ts,
    event_type: it.type,
    payload: it.payload,
   })),
  };

  try {
   const res = await fetch(`${this.baseUrl}/ingest/batch`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(3000),
   });
   if (res.ok) {
    const data = await res.json() as {clock_offset_ms?: number};
    if (typeof data.clock_offset_ms === 'number') {
     this.lastClockOffset = data.clock_offset_ms;
    }
   } else {
    // Put back unwritten events if failed (up to 100)
    this.buffer.unshift(...items.slice(-50));
   }
  } catch {
   this.buffer.unshift(...items.slice(-50));
  }
 }

 async sendFeedback(action: 'accepted' | 'dismissed', stage = 2, context = ''): Promise<void> {
  try {
   await fetch(`${this.baseUrl}/feedback`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({action, stage, context}),
    signal: AbortSignal.timeout(2000),
   });
  } catch {
   // Non-blocking telemetry
  }
 }

 async fetchTwinState(): Promise<TwinState | null> {
  try {
   const res = await fetch(`${this.baseUrl}/twin/state`, {signal: AbortSignal.timeout(2000)});
   if (res.ok) {
    return await res.json() as TwinState;
   }
  } catch {
   // Fallback when sidecar is offline
  }
  return null;
 }

 async stop(): Promise<void> {
  this.running = false;
  if (this.flushTimer) {
   clearInterval(this.flushTimer);
   this.flushTimer = null;
  }
  await this.flush();
  if (this.process) {
   try {
    this.process.kill();
   } catch {
    // Already exited
   }
   this.process = null;
  }
 }
}
