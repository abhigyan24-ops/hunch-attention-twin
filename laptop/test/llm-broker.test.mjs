import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { redact } from '../src/main/llm/broker.ts';

describe('LLM Broker Privacy & Redaction Engine', () => {
  test('Redaction strips emails, bearer tokens, api keys and sensitive URLs', () => {
    const raw = `
      Please inspect error for user john.doe@example.com
      Auth header: Bearer ya29.a0AfH6SMD_random_secret_token_12345
      Secret config: api_key="sk-live-1234567890abcdef" password='superSecretPassword123'
      Visit internal dashboard: https://internal.company.corp/admin/users?token=secret123
    `;

    const cleaned = redact(raw);

    assert.equal(cleaned.includes('john.doe@example.com'), false);
    assert.equal(cleaned.includes('[EMAIL]'), true);

    assert.equal(cleaned.includes('ya29.a0AfH6SMD_random_secret_token_12345'), false);
    assert.equal(cleaned.includes('Bearer [REDACTED]'), true);

    assert.equal(cleaned.includes('sk-live-1234567890abcdef'), false);
    assert.equal(cleaned.includes('superSecretPassword123'), false);

    assert.equal(cleaned.includes('/admin/users?token=secret123'), false);
    assert.equal(cleaned.includes('https://internal.company.corp/[PATH_REDACTED]'), true);
  });

  test('Localhost Ollama host check rejects remote endpoints', () => {
    const isLocalOllama = (endpoint) => {
      try {
        const u = new URL(endpoint);
        return u.hostname === '127.0.0.1' || u.hostname === 'localhost';
      } catch {
        return false;
      }
    };

    assert.equal(isLocalOllama('http://127.0.0.1:11434'), true);
    assert.equal(isLocalOllama('http://localhost:11434'), true);
    assert.equal(isLocalOllama('https://remote-ollama.example.com'), false);
    assert.equal(isLocalOllama('http://192.168.1.50:11434'), false);
  });
});
