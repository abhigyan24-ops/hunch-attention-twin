import {spawn, execFile, type ChildProcess} from 'node:child_process';
import {join} from 'node:path';
import {existsSync} from 'node:fs';
import type {CategorizedEvent} from './jsonlStore';

export function killTree(pid: number): Promise<void> {
 return new Promise(res => {
  if (process.platform === 'win32') {
   execFile('taskkill', ['/pid', String(pid), '/T', '/F'], () => res());
  } else {
   try {
    process.kill(-pid, 'SIGTERM');
   } catch {}
   res();
  }
 });
}

export interface TwinState {
 focus_depth: number;
 fatigue_p: number;
 held_count: number;
 alignment: number;
 lease?: {
  focus: string;
  leaseMs: number;
 };
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
 private leaseTimer: ReturnType<typeof setInterval> | null = null;
 private currentLeaseFocus: 'normal' | 'deep' = 'normal';
 private phoneLeaseSender: ((focus: 'normal' | 'deep', leaseMs: number) => void) | null = null;
 readonly port = 8765;
 readonly baseUrl = 'http://127.0.0.1:8765';

 constructor(
  private readonly projectRoot: string,
  private readonly dbPath?: string
 ) {}

 setPhoneLeaseSender(sender: (focus: 'normal' | 'deep', leaseMs: number) => void): void {
  this.phoneLeaseSender = sender;
 }

 async broadcastFocusLease(focus: 'normal' | 'deep', leaseMs: number): Promise<boolean> {
  try {
   const res = await fetch(`${this.baseUrl}/twin/lease`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({focus, leaseMs}),
    signal: AbortSignal.timeout(2000),
   });
   if (this.phoneLeaseSender) {
    this.phoneLeaseSender(focus, leaseMs);
   }
   return res.ok;
  } catch {
   if (this.phoneLeaseSender) {
    this.phoneLeaseSender(focus, leaseMs);
   }
   return false;
  }
 }

 updateFocusState(isFocusing: boolean): void {
  const targetFocus: 'normal' | 'deep' = isFocusing ? 'deep' : 'normal';
  if (targetFocus === 'deep') {
   if (this.currentLeaseFocus !== 'deep') {
    this.currentLeaseFocus = 'deep';
    void this.broadcastFocusLease('deep', 90000);
    if (this.leaseTimer) clearInterval(this.leaseTimer);
    this.leaseTimer = setInterval(() => {
     if (this.currentLeaseFocus === 'deep') {
      void this.broadcastFocusLease('deep', 90000);
     }
    }, 30000);
   }
  } else {
   if (this.currentLeaseFocus === 'deep') {
    this.currentLeaseFocus = 'normal';
    if (this.leaseTimer) {
     clearInterval(this.leaseTimer);
     this.leaseTimer = null;
    }
    void this.broadcastFocusLease('normal', 0);
   }
  }
 }

 engineEnv(frozen: boolean, baseEnv: NodeJS.ProcessEnv = process.env): NodeJS.ProcessEnv {
  const dbEnv = this.dbPath ? {DHYAN_DB_PATH: this.dbPath} : {};
  return frozen
   ? {...baseEnv, ...dbEnv, TWIN_TOKEN: 'dhyan-local-twin-token'}
   : {...baseEnv, ...dbEnv, PYTHONPATH: this.projectRoot, PYTHONUNBUFFERED: '1'};
 }

 async start(): Promise<boolean> {
  if (this.running) return true;
  this.running = true;

  // 1. Check if sidecar is already running (e.g. independently started)
  if (await this.isHealthy()) {
   console.info('[Sidecar] Engine already running on port', this.port);
   this.startFlushLoop();
   return true;
  }

  // 2. Check for frozen engine if packaged or fallback to python
  const packagedDir = (process as any).resourcesPath ? join((process as any).resourcesPath, 'engine') : '';
  const frozenExe = packagedDir ? join(packagedDir, process.platform === 'win32' ? 'dhyan-engine.exe' : 'dhyan-engine') : '';
  const useFrozen = Boolean(frozenExe && existsSync(frozenExe));

  try {
   if (useFrozen) {
    this.process = spawn(frozenExe, [], {
     cwd: packagedDir,
     env: this.engineEnv(true),
     stdio: ['ignore', 'pipe', 'pipe'],
    });
   } else {
    const pythonBin = process.platform === 'win32' ? 'python' : 'python3';
    this.process = spawn(
     pythonBin,
     ['-m', 'uvicorn', 'engine.twin.api.app:app', '--host', '127.0.0.1', '--port', String(this.port), '--no-access-log'],
     {
      cwd: this.projectRoot,
      env: this.engineEnv(false),
      stdio: ['ignore', 'pipe', 'pipe'],
     }
    );
   }

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
  if (this.leaseTimer) {
   clearInterval(this.leaseTimer);
   this.leaseTimer = null;
  }
  if (this.currentLeaseFocus === 'deep') {
   this.currentLeaseFocus = 'normal';
   void this.broadcastFocusLease('normal', 0);
  }
  if (this.flushTimer) {
   clearInterval(this.flushTimer);
   this.flushTimer = null;
  }
  await this.flush();
  if (this.process?.pid) {
   try {
    await killTree(this.process.pid);
   } catch {
    // Already exited
   }
   this.process = null;
  }
 }
}
