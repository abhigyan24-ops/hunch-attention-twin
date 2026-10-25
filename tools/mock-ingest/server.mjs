import http from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import crypto from 'crypto';
import { validateIngest } from '../lib/contract-validate.mjs';

export function startMockIngest(options = {}) {
  const host = '127.0.0.1';
  const port = options.port || 0; // 0 for ephemeral port in tests
  const token = options.token || 'mock-secret-token-32-chars-long-1234';
  const allowedExtensionIds = new Set(options.allowedExtensionIds || ['dhyan-mock-extension-id']);

  const stats = {
    authSuccess: 0,
    authFail: 0,
    invalid: 0,
    rateLimited: 0,
    framesReceived: 0,
    activeConnections: 0,
    eventsReceived: []
  };

  // Track failed auth attempts for rate limiting: ip -> list of timestamps
  const failedAttempts = new Map();
  // Active client by kind: 'vscode' | 'browser' -> WebSocket
  const activeClients = new Map();

  const server = http.createServer((req, res) => {
    res.writeHead(404);
    res.end();
  });

  const wss = new WebSocketServer({ noServer: true });

  server.on('upgrade', (req, socket, head) => {
    const origin = req.headers['origin'];
    const remoteIp = req.socket.remoteAddress;

    // Check blocked IP / port
    const attempts = failedAttempts.get(remoteIp) || [];
    const now = Date.now();
    const recentAttempts = attempts.filter(t => now - t < 60000);
    failedAttempts.set(remoteIp, recentAttempts);

    if (recentAttempts.length >= 5) {
      socket.write('HTTP/1.1 429 Too Many Requests\r\n\r\n');
      socket.destroy();
      return;
    }

    // Origin Check (Section 9.1)
    if (origin !== undefined) {
      const match = origin.match(/^chrome-extension:\/\/([a-zA-Z0-9_-]+)$/);
      if (!match || !allowedExtensionIds.has(match[1])) {
        socket.write('HTTP/1.1 403 Forbidden\r\n\r\n');
        socket.destroy();
        return;
      }
    }

    wss.handleUpgrade(req, socket, head, (ws) => {
      wss.emit('connection', ws, req);
    });
  });

  wss.on('connection', (ws, req) => {
    stats.activeConnections++;
    let authenticated = false;
    let clientKind = null;

    // Rate limiting: sliding 1-second window of messages
    const messageTimestamps = [];

    // 3-second auth timeout
    const authTimeout = setTimeout(() => {
      if (!authenticated) {
        ws.close(1008, 'Auth timeout');
      }
    }, 3000);

    ws.on('message', (data, isBinary) => {
      stats.framesReceived++;

      if (isBinary || data.length > 4096) {
        stats.invalid++;
        ws.close(1009, 'Frame too large');
        return;
      }

      // 20 messages per second rate limit
      const now = Date.now();
      messageTimestamps.push(now);
      while (messageTimestamps.length > 0 && now - messageTimestamps[0] > 1000) {
        messageTimestamps.shift();
      }
      if (messageTimestamps.length > 20) {
        stats.rateLimited++;
        ws.send(JSON.stringify({ kind: 'error', code: 'rate_limited' }));
        return;
      }

      let msg;
      try {
        msg = JSON.parse(data.toString('utf8'));
      } catch (err) {
        stats.invalid++;
        ws.send(JSON.stringify({ kind: 'error', code: 'schema_invalid', detail: 'Invalid JSON' }));
        return;
      }

      if (!authenticated) {
        if (msg.kind !== 'auth') {
          stats.authFail++;
          ws.close(1008, 'First message must be auth');
          return;
        }

        const msgToken = Buffer.from(String(msg.token || ''));
        const expToken = Buffer.from(token);

        let match = false;
        if (msgToken.length === expToken.length) {
          match = crypto.timingSafeEqual(msgToken, expToken);
        }

        if (!match) {
          stats.authFail++;
          const remoteIp = req.socket.remoteAddress;
          const attempts = failedAttempts.get(remoteIp) || [];
          attempts.push(Date.now());
          failedAttempts.set(remoteIp, attempts);
          ws.close(1008, 'Invalid token');
          return;
        }

        if (msg.client !== 'vscode' && msg.client !== 'browser') {
          stats.authFail++;
          ws.close(1008, 'Unknown client kind');
          return;
        }

        authenticated = true;
        clearTimeout(authTimeout);
        clientKind = msg.client;
        stats.authSuccess++;

        // Only one connection per client kind at a time; replace old
        if (activeClients.has(clientKind)) {
          const oldWs = activeClients.get(clientKind);
          try {
            oldWs.close(1000, 'Replaced by new connection');
          } catch (_) {}
        }
        activeClients.set(clientKind, ws);

        ws.send(JSON.stringify({
          kind: 'auth_ok',
          serverVersion: '1',
          config: { heartbeatSec: 5 }
        }));
        return;
      }

      // Validate message schema and forbidden fields
      const validation = validateIngest(msg);
      if (!validation.valid) {
        stats.invalid++;
        ws.send(JSON.stringify({
          kind: 'error',
          code: 'schema_invalid',
          detail: validation.errors[0]
        }));
        return;
      }

      stats.eventsReceived.push(msg);
    });

    ws.on('close', () => {
      stats.activeConnections--;
      clearTimeout(authTimeout);
      if (clientKind && activeClients.get(clientKind) === ws) {
        activeClients.delete(clientKind);
      }
    });
  });

  return new Promise((resolve) => {
    server.listen(port, host, () => {
      const assignedPort = server.address().port;
      resolve({
        server,
        wss,
        port: assignedPort,
        token,
        stats,
        close: () => new Promise((res) => {
          wss.close(() => {
            server.close(() => res());
          });
        })
      });
    });
  });
}

// Standalone CLI runner
if (process.argv[1] && process.argv[1].endsWith('tools/mock-ingest/server.mjs')) {
  let port = 47821;
  let token = 'mock-secret-token-32-chars-long-1234';
  const args = process.argv.slice(2);
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--port' && args[i + 1]) port = parseInt(args[++i], 10);
    if (args[i] === '--token' && args[i + 1]) token = args[++i];
  }

  const inst = await startMockIngest({ port, token });
  console.log(`Mock Ingest Server listening on ws://127.0.0.1:${inst.port}`);
  console.log(`Auth Token: ${token}`);
}
