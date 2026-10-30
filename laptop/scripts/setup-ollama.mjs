#!/usr/bin/env node
/**
 * setup-ollama.mjs
 * Auto-setup health check and model puller for Tier 1 Local Ollama.
 * Models: llama3.2:1b (text classification) & moondream (local vision).
 */

import { fileURLToPath } from 'node:url';
import path from 'node:path';

function normalizeEndpoint(ep) {
  let url = (ep || process.env.OLLAMA_HOST || 'http://127.0.0.1:11434').trim();
  if (!url.startsWith('http://') && !url.startsWith('https://')) {
    url = `http://${url}`;
  }
  return url.replace('://0.0.0.0:', '://127.0.0.1:');
}

const DEFAULT_ENDPOINT = normalizeEndpoint();
const REQUIRED_MODELS = ['llama3.2:1b', 'moondream'];

export async function checkOllamaHealth(endpoint = DEFAULT_ENDPOINT) {
  try {
    const res = await fetch(`${endpoint}/api/version`, {
      signal: AbortSignal.timeout(2000)
    });
    if (!res.ok) return { ok: false, error: `HTTP ${res.status} from Ollama` };
    const data = await res.json();
    return { ok: true, version: data.version || 'unknown' };
  } catch (err) {
    return { ok: false, error: err instanceof Error ? err.message : String(err) };
  }
}

export async function listInstalledModels(endpoint = DEFAULT_ENDPOINT) {
  try {
    const res = await fetch(`${endpoint}/api/tags`, {
      signal: AbortSignal.timeout(3000)
    });
    if (!res.ok) return [];
    const data = await res.json();
    return (data.models || []).map((m) => (m.name || '').toLowerCase());
  } catch {
    return [];
  }
}

export async function pullModel(modelName, endpoint = DEFAULT_ENDPOINT) {
  console.log(`[setup-ollama] Requesting pull for ${modelName}...`);
  try {
    const res = await fetch(`${endpoint}/api/pull`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: modelName, stream: false })
    });
    if (!res.ok) {
      console.warn(`[setup-ollama] Failed to pull ${modelName}: HTTP ${res.status}`);
      return false;
    }
    console.log(`[setup-ollama] Successfully pulled ${modelName}!`);
    return true;
  } catch (err) {
    console.warn(`[setup-ollama] Pull error for ${modelName}:`, err instanceof Error ? err.message : String(err));
    return false;
  }
}

export async function setupOllama(endpoint = DEFAULT_ENDPOINT) {
  console.log(`[setup-ollama] Checking Ollama at ${endpoint}...`);
  const health = await checkOllamaHealth(endpoint);
  if (!health.ok) {
    console.warn(`[setup-ollama] Ollama is not accessible at ${endpoint}: ${health.error}`);
    console.info(`[setup-ollama] For Tier 1 local private inference, install and start Ollama from https://ollama.ai`);
    return { ok: false, error: health.error };
  }

  console.log(`[setup-ollama] Ollama v${health.version} is running.`);
  const installed = await listInstalledModels(endpoint);
  console.log(`[setup-ollama] Installed models: ${installed.join(', ') || 'none'}`);

  const results = {};
  for (const model of REQUIRED_MODELS) {
    const isPresent = installed.some((name) => name === model || name.startsWith(`${model}:`));
    if (isPresent) {
      console.log(`[setup-ollama] Model '${model}' is already available.`);
      results[model] = true;
    } else {
      results[model] = await pullModel(model, endpoint);
    }
  }

  return { ok: true, version: health.version, models: results };
}

const isDirectRun = process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1]);
if (isDirectRun) {
  setupOllama().then((res) => {
    if (!res.ok) process.exit(0);
  });
}
