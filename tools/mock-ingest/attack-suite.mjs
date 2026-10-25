import { WebSocket } from 'ws';
import assert from 'node:assert/strict';
import http from 'http';
import { startMockIngest } from './server.mjs';

export async function runIngestAttackSuite(ingestInstance = null) {
  const ownServer = !ingestInstance;
  const inst = ingestInstance || await startMockIngest({
    port: 0,
    token: 'test-secret-token-32-chars-long-1',
    allowedExtensionIds: ['allowed-test-ext']
  });

  const baseUrl = `ws://127.0.0.1:${inst.port}`;
  const results = { passed: 0, failed: 0, details: [] };

  function logPass(name) {
    results.passed++;
    results.details.push({ test: name, status: 'pass' });
  }

  // 1. Origin attack: reject unauthorized origin with HTTP 403
  await new Promise((resolve) => {
    const req = http.request({
      hostname: '127.0.0.1',
      port: inst.port,
      headers: {
        'Origin': 'https://attacker.site',
        'Connection': 'Upgrade',
        'Upgrade': 'websocket',
        'Sec-WebSocket-Version': 13,
        'Sec-WebSocket-Key': 'dGhlIHNhbXBsZSBub25jZQ=='
      }
    });
    req.on('response', (res) => {
      assert.equal(res.statusCode, 403);
      logPass('reject unauthorized origin 403');
      resolve();
    });
    req.end();
  });

  // 2. Token attack: wrong token closes with 1008
  await new Promise((resolve) => {
    const ws = new WebSocket(baseUrl, {
      headers: { 'Origin': 'chrome-extension://allowed-test-ext' }
    });
    ws.on('open', () => {
      ws.send(JSON.stringify({ kind: 'auth', token: 'wrong-token-value', client: 'browser', clientVersion: '1.0.0' }));
    });
    ws.on('close', (code) => {
      assert.equal(code, 1008);
      logPass('reject invalid token with close 1008');
      resolve();
    });
  });

  // 3. First message not auth: closes with 1008
  await new Promise((resolve) => {
    const ws = new WebSocket(baseUrl);
    ws.on('open', () => {
      ws.send(JSON.stringify({ kind: 'ext_heartbeat', ts: Date.now() }));
    });
    ws.on('close', (code) => {
      assert.equal(code, 1008);
      logPass('reject non-auth first message with close 1008');
      resolve();
    });
  });

  // 4. Successful auth and message sending
  const clientWs = await new Promise((resolve) => {
    const ws = new WebSocket(baseUrl, {
      headers: { 'Origin': 'chrome-extension://allowed-test-ext' }
    });
    ws.on('open', () => {
      ws.send(JSON.stringify({ kind: 'auth', token: inst.token, client: 'browser', clientVersion: '1.0.0' }));
    });
    ws.on('message', (data) => {
      const msg = JSON.parse(data.toString());
      if (msg.kind === 'auth_ok') {
        logPass('accept valid token and origin with auth_ok');
        resolve(ws);
      }
    });
  });

  // 5. Forbidden field attack: message containing forbidden field
  await new Promise((resolve) => {
    clientWs.once('message', (data) => {
      const msg = JSON.parse(data.toString());
      assert.equal(msg.kind, 'error');
      assert.equal(msg.code, 'schema_invalid');
      logPass('reject message with forbidden field');
      resolve();
    });
    clientWs.send(JSON.stringify({
      kind: 'tab_category',
      ts: Date.now(),
      domainCat: 'dev',
      surface: 'editor',
      tabActive: true,
      url: 'https://leak.example.com' // forbidden!
    }));
  });

  // 6. Oversized payload attack: frame > 4096 bytes
  await new Promise((resolve) => {
    const bigWs = new WebSocket(baseUrl);
    bigWs.on('open', () => {
      const big = 'A'.repeat(5000);
      bigWs.send(big);
    });
    bigWs.on('close', (code) => {
      assert.equal(code, 1009);
      logPass('reject oversized frame >4096 bytes with 1009');
      resolve();
    });
  });

  // 7. Rate limit attack: > 20 messages in 1 second
  await new Promise((resolve) => {
    let gotRateLimit = false;
    clientWs.on('message', (data) => {
      const msg = JSON.parse(data.toString());
      if (msg.kind === 'error' && msg.code === 'rate_limited') {
        gotRateLimit = true;
      }
    });

    for (let i = 0; i < 25; i++) {
      clientWs.send(JSON.stringify({ kind: 'ext_heartbeat', ts: Date.now() }));
    }

    setTimeout(() => {
      assert.ok(gotRateLimit, 'Expected rate_limited error');
      logPass('enforce 20 msgs/sec rate limit');
      clientWs.close();
      resolve();
    }, 200);
  });

  if (ownServer) {
    await inst.close();
  }

  return results;
}

if (process.argv[1] && process.argv[1].endsWith('tools/mock-ingest/attack-suite.mjs')) {
  console.log('Running Ingest Attack Suite...');
  const res = await runIngestAttackSuite();
  console.log(`Ingest Attacks: ${res.passed} passed, ${results.failed || 0} failed.`);
}
