// laptop/src/main/privacy/keys.ts
import type { IpcMainInvokeEvent } from 'electron';
import fs from 'node:fs';
import path from 'node:path';
import { z } from 'zod';

export type Provider = 'groq' | 'gemini';
export type StorageMode = 'secure' | 'memory-only';
export type LoadStatus = 'ok' | 'empty' | 'needs-reentry';

interface Stored {
  v: 1;
  keys: Partial<Record<Provider, string>>;
}

// Session-only copy. It is the only place a key lives when secure storage is unavailable.
let cache: Partial<Record<Provider, string>> = {};
let loadStatus: LoadStatus = 'empty';

// Resolve Electron APIs dynamically to allow unit tests to run under Node.js test runner
function getElectron(): any {
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const el = require('electron');
    if (el && typeof el === 'object' && el.app) return el;
  } catch {}
  return null;
}

const getUserData = (): string => {
  const el = getElectron();
  if (el?.app?.getPath) return el.app.getPath('userData');
  return process.env.DHYAN_USER_DATA || path.join(process.cwd(), '.dhyan-data');
};

const filePath = (): string => path.join(getUserData(), 'secure', 'keys.bin');

/** Encryption must be queried AFTER app 'ready'. On Linux, 'basic_text' is not real protection. */
export function storageMode(): StorageMode {
  const el = getElectron();
  const safeStorage = el?.safeStorage;
  if (!safeStorage || !safeStorage.isEncryptionAvailable()) return 'memory-only';
  if (process.platform === 'linux') {
    const backend = (safeStorage as any).getSelectedStorageBackend?.();
    if (backend === 'basic_text' || backend === 'unknown') return 'memory-only';
  }
  return 'secure';
}

function persist(): void {
  if (storageMode() !== 'secure') return; // NEVER write plaintext
  const el = getElectron();
  const safeStorage = el?.safeStorage;
  if (!safeStorage) return;

  const data: Stored = { v: 1, keys: cache };
  const enc = safeStorage.encryptString(JSON.stringify(data));
  const dir = path.dirname(filePath());
  fs.mkdirSync(dir, { recursive: true });
  const tmp = filePath() + '.tmp';
  fs.writeFileSync(tmp, enc, { mode: 0o600 });
  fs.renameSync(tmp, filePath()); // atomic replace, no half-written file
}

export function loadKeys(): LoadStatus {
  if (!fs.existsSync(filePath())) {
    cache = {};
    return (loadStatus = 'empty');
  }
  if (storageMode() !== 'secure') {
    cache = {};
    return (loadStatus = 'needs-reentry');
  }
  const el = getElectron();
  const safeStorage = el?.safeStorage;
  if (!safeStorage) {
    cache = {};
    return (loadStatus = 'needs-reentry');
  }

  try {
    const raw = fs.readFileSync(filePath());
    const parsed = JSON.parse(safeStorage.decryptString(raw)) as Stored;
    cache = parsed.keys ?? {};
    return (loadStatus = Object.keys(cache).length ? 'ok' : 'empty');
  } catch {
    cache = {}; // profile changed, file corrupt, etc.
    return (loadStatus = 'needs-reentry'); // tell the UI; do not fail silently
  }
}

/** Main-process-only accessor. Never exposed over IPC. */
export function getKey(p: Provider): string | undefined {
  return cache[p];
}

/** For testing or main-process inspection */
export function setKeyInMemory(p: Provider, key: string | undefined): void {
  if (key) cache[p] = key;
  else delete cache[p];
}

export function getAllKeys(): Partial<Record<Provider, string>> {
  return { ...cache };
}

// ---------- IPC (renderer-facing): validated, minimal, no raw key reads ----------

const SetInput = z.object({
  provider: z.enum(['groq', 'gemini']),
  key: z.string().trim().min(20).max(256).regex(/^[\x21-\x7E]+$/, 'unexpected characters'),
  verify: z.boolean().default(true),
});

const RemoveInput = z.object({
  provider: z.enum(['groq', 'gemini']),
});

function trustedSender(e: IpcMainInvokeEvent): boolean {
  const url = e.senderFrame?.url ?? '';
  return url.startsWith('file://') || (Boolean(process.env.ELECTRON_RENDERER_URL) && url.startsWith(process.env.ELECTRON_RENDERER_URL ?? '\0'));
}

const last4 = (k?: string): string => (k && k.length >= 8 ? `••••${k.slice(-4)}` : k ? '••••' : '');

export function registerKeyIpcHandlers(): void {
  const el = getElectron();
  const ipcMain = el?.ipcMain;
  if (!ipcMain) return;

  ipcMain.handle('keys:status', (e: IpcMainInvokeEvent) => {
    if (!trustedSender(e)) throw new Error('forbidden');
    return {
      storage: storageMode(),
      loadStatus,
      providers: {
        groq: { present: !!cache.groq, hint: last4(cache.groq) },
        gemini: { present: !!cache.gemini, hint: last4(cache.gemini) },
      },
    };
  });

  ipcMain.handle('keys:set', async (e: IpcMainInvokeEvent, raw: unknown) => {
    if (!trustedSender(e)) throw new Error('forbidden');
    const parsed = SetInput.safeParse(raw);
    if (!parsed.success) {
      return { ok: false, reason: 'validation-error', issues: parsed.error.issues };
    }
    const { provider, key, verify } = parsed.data;
    if (verify) {
      const r = await verifyKey(provider, key);
      if (r.status === 'invalid') return { ok: false, reason: 'invalid-key' };
      // 'unreachable' is allowed: store anyway, tell the user it could not be verified
      cache[provider] = key;
      persist();
      return { ok: true, verified: r.status === 'valid', storage: storageMode() };
    }
    cache[provider] = key;
    persist();
    return { ok: true, verified: false, storage: storageMode() };
  });

  ipcMain.handle('keys:remove', (e: IpcMainInvokeEvent, raw: unknown) => {
    if (!trustedSender(e)) throw new Error('forbidden');
    const parsed = RemoveInput.safeParse(raw);
    if (!parsed.success) return { ok: false, reason: 'invalid-provider' };
    delete cache[parsed.data.provider];
    persist();
    return { ok: true };
  });
}

// ---------- Cheap key check ----------
export async function verifyKey(p: Provider, key: string): Promise<{ status: 'valid' | 'invalid' | 'unreachable' }> {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), 6000);
  try {
    const res =
      p === 'groq'
        ? await fetch('https://api.groq.com/openai/v1/models', {
            headers: { Authorization: `Bearer ${key}` },
            signal: ctl.signal,
          })
        : await fetch('https://generativelanguage.googleapis.com/v1beta/models', {
            headers: { 'x-goog-api-key': key },
            signal: ctl.signal,
          });
    if (res.ok) return { status: 'valid' };
    if (res.status === 400 || res.status === 401 || res.status === 403) return { status: 'invalid' };
    return { status: 'unreachable' };
  } catch {
    return { status: 'unreachable' };
  } finally {
    clearTimeout(t);
  }
}
