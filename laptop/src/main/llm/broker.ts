// laptop/src/main/llm/broker.ts
import { getKey, type Provider } from '../privacy/keys.ts';
import fs from 'node:fs';
import path from 'node:path';

function getUserDataPath(): string {
  try {
    // Dynamic access if running inside electron
    const electron = (globalThis as any).electron || undefined;
    if (electron?.app?.getPath) return electron.app.getPath('userData');
  } catch {}
  return process.env.DHYAN_USER_DATA || path.join(process.cwd(), '.dhyan-data');
}

export interface ChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

export interface BrokerRequest {
  task: string;
  messages: ChatMessage[];
  maxTokens?: number;
}

export interface BrokerResult {
  text: string | null;
  provider: Provider | 'ollama' | 'none';
  usage?: {
    promptTokens?: number;
    completionTokens?: number;
  };
}

export interface UserPrefs {
  cloudConsent: boolean;
  providerOrder: Provider[];
  ollamaEndpoint: string;
}

interface AuditRecord {
  ts: number;
  task: string;
  provider: string;
  promptTokens: number;
  completionTokens: number;
  success: boolean;
}

// Token bucket rate limiter
class TokenBucket {
  private capacity: number;
  private refillPerSec: number;
  private tokens: number;
  private lastRefill: number;
  private backoffUntil: number = 0;

  constructor(capacity: number, refillPerSec: number) {
    this.capacity = capacity;
    this.refillPerSec = refillPerSec;
    this.tokens = capacity;
    this.lastRefill = Date.now();
  }

  tryTake(): boolean {
    const now = Date.now();
    if (now < this.backoffUntil) return false;

    const deltaSec = (now - this.lastRefill) / 1000;
    this.tokens = Math.min(this.capacity, this.tokens + deltaSec * this.refillPerSec);
    this.lastRefill = now;

    if (this.tokens >= 1) {
      this.tokens -= 1;
      return true;
    }
    return false;
  }

  applyBackoff(seconds: number = 60): void {
    this.backoffUntil = Date.now() + seconds * 1000;
  }
}

const limiters: Record<Provider, TokenBucket> = {
  groq: new TokenBucket(30, 0.5), // 30 bursts, 1 every 2s
  gemini: new TokenBucket(15, 0.25), // 15 bursts, 1 every 4s
};

const prefsPath = (): string => path.join(getUserDataPath(), 'llm_prefs.json');
const auditLogPath = (): string => path.join(getUserDataPath(), 'llm_audit.jsonl');

export function loadPrefs(): UserPrefs {
  try {
    if (fs.existsSync(prefsPath())) {
      const data = JSON.parse(fs.readFileSync(prefsPath(), 'utf8'));
      return {
        cloudConsent: Boolean(data.cloudConsent),
        providerOrder: Array.isArray(data.providerOrder) ? data.providerOrder : ['groq', 'gemini'],
        ollamaEndpoint: typeof data.ollamaEndpoint === 'string' ? data.ollamaEndpoint : 'http://127.0.0.1:11434',
      };
    }
  } catch {}
  return {
    cloudConsent: false,
    providerOrder: ['groq', 'gemini'],
    ollamaEndpoint: 'http://127.0.0.1:11434',
  };
}

export function savePrefs(prefs: Partial<UserPrefs>): void {
  const current = loadPrefs();
  const updated = { ...current, ...prefs };
  fs.mkdirSync(path.dirname(prefsPath()), { recursive: true });
  fs.writeFileSync(prefsPath(), JSON.stringify(updated, null, 2), 'utf8');
}

/** Redaction layer: strips tokens, emails, API keys, passwords, and sensitive URLs */
export function redact(text: string): string {
  return text
    .replace(/(?:Bearer\s+)[A-Za-z0-9\-_.]+/gi, 'Bearer [REDACTED]')
    .replace(/(?:api[_-]?key|secret|password|token)\s*[:=]\s*["']?[A-Za-z0-9_\-.]+["']?/gi, '$1=[REDACTED]')
    .replace(/[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+/g, '[EMAIL]')
    .replace(/https?:\/\/[^\s/$.?#].[^\s]*/gi, (url) => {
      try {
        const u = new URL(url);
        return `${u.protocol}//${u.hostname}/[PATH_REDACTED]`;
      } catch {
        return '[URL_REDACTED]';
      }
    });
}

function writeAudit(record: AuditRecord): void {
  try {
    const dir = path.dirname(auditLogPath());
    fs.mkdirSync(dir, { recursive: true });
    fs.appendFileSync(auditLogPath(), JSON.stringify(record) + '\n', 'utf8');
  } catch {}
}

export function getAuditSummary(): { totalRequests: number; last24h: number; byProvider: Record<string, number> } {
  const summary = { totalRequests: 0, last24h: 0, byProvider: {} as Record<string, number> };
  try {
    if (!fs.existsSync(auditLogPath())) return summary;
    const lines = fs.readFileSync(auditLogPath(), 'utf8').trim().split('\n');
    const dayAgo = Date.now() - 86400000;
    for (const l of lines) {
      if (!l.trim()) continue;
      const rec = JSON.parse(l) as AuditRecord;
      summary.totalRequests++;
      if (rec.ts >= dayAgo) summary.last24h++;
      summary.byProvider[rec.provider] = (summary.byProvider[rec.provider] || 0) + 1;
    }
  } catch {}
  return summary;
}

// 1. Ollama (127.0.0.1 strictly)
async function tryOllama(endpoint: string, messages: ChatMessage[], maxTokens: number): Promise<string | null> {
  try {
    const host = new URL(endpoint).hostname;
    if (host !== '127.0.0.1' && host !== 'localhost') return null; // local only

    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), 1200); // 1.2s local deadline
    const res = await fetch(`${endpoint.replace(/\/$/, '')}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: 'llama3.2:1b',
        messages: messages.map((m) => ({ role: m.role, content: m.content })),
        stream: false,
        options: { num_predict: maxTokens, temperature: 0.1 },
      }),
      signal: ctl.signal,
    });
    clearTimeout(t);
    if (!res.ok) return null;
    const data = await res.json() as { message?: { content?: string } };
    return data.message?.content?.trim() || null;
  } catch {
    return null;
  }
}

// 2. Call Cloud Provider
async function callProvider(
  provider: Provider,
  key: string,
  messages: ChatMessage[],
  maxTokens: number
): Promise<{ text: string; usage: { promptTokens: number; completionTokens: number } }> {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), 8000); // 8s timeout

  try {
    if (provider === 'groq') {
      const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${key}`,
        },
        body: JSON.stringify({
          model: 'llama-3.1-8b-instant',
          messages,
          max_tokens: maxTokens,
          temperature: 0.1,
        }),
        signal: ctl.signal,
      });

      if (res.status === 429) {
        limiters.groq.applyBackoff(60);
        throw new Error('rate_limit: 429');
      }
      if (!res.ok) throw new Error(`groq_error: ${res.status}`);
      const data = await res.json() as {
        choices?: Array<{ message?: { content?: string } }>;
        usage?: { prompt_tokens?: number; completion_tokens?: number };
      };
      return {
        text: data.choices?.[0]?.message?.content?.trim() || '',
        usage: {
          promptTokens: data.usage?.prompt_tokens || 0,
          completionTokens: data.usage?.completion_tokens || 0,
        },
      };
    } else {
      // Gemini API
      const res = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${key}`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            contents: messages.map((m) => ({
              role: m.role === 'assistant' ? 'model' : 'user',
              parts: [{ text: m.content }],
            })),
            generationConfig: {
              maxOutputTokens: maxTokens,
              temperature: 0.1,
            },
          }),
          signal: ctl.signal,
        }
      );

      if (res.status === 429) {
        limiters.gemini.applyBackoff(60);
        throw new Error('rate_limit: 429');
      }
      if (!res.ok) throw new Error(`gemini_error: ${res.status}`);
      const data = await res.json() as {
        candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
        usageMetadata?: { promptTokenCount?: number; candidatesTokenCount?: number };
      };
      return {
        text: data.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || '',
        usage: {
          promptTokens: data.usageMetadata?.promptTokenCount || 0,
          completionTokens: data.usageMetadata?.candidatesTokenCount || 0,
        },
      };
    }
  } finally {
    clearTimeout(t);
  }
}

/** Complete an LLM request according to the multi-tier privacy and provider policy */
export async function complete(req: BrokerRequest): Promise<BrokerResult> {
  const prefs = loadPrefs();
  const maxTokens = req.maxTokens ?? 150;
  const clean = req.messages.map((m) => ({ ...m, content: redact(m.content) }));

  // 1. Local first: Ollama
  const local = await tryOllama(prefs.ollamaEndpoint, clean, maxTokens);
  if (local) {
    writeAudit({
      ts: Date.now(),
      task: req.task,
      provider: 'ollama',
      promptTokens: 0,
      completionTokens: 0,
      success: true,
    });
    return { text: local, provider: 'ollama' };
  }

  // 2. Cloud only with consent and a key
  if (!prefs.cloudConsent) {
    return { text: null, provider: 'none' };
  }

  for (const p of prefs.providerOrder) {
    const key = getKey(p);
    if (!key) continue;
    if (!limiters[p].tryTake()) continue;

    try {
      const result = await callProvider(p, key, clean, maxTokens);
      writeAudit({
        ts: Date.now(),
        task: req.task,
        provider: p,
        promptTokens: result.usage.promptTokens,
        completionTokens: result.usage.completionTokens,
        success: true,
      });
      return {
        text: result.text,
        provider: p,
        usage: result.usage,
      };
    } catch (e: any) {
      writeAudit({
        ts: Date.now(),
        task: req.task,
        provider: p,
        promptTokens: 0,
        completionTokens: 0,
        success: false,
      });
      // Fall through to next provider
    }
  }

  return { text: null, provider: 'none' };
}
