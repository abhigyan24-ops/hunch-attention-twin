import http from 'node:http';
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';
import { TextDecoder } from 'node:util';
import { WebSocketServer, WebSocket } from 'ws';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const read = name => JSON.parse(fs.readFileSync(new URL(`../core-spec/${name}`, import.meta.url), 'utf8'));
const ajv = new Ajv2020({ strict: true }); addFormats(ajv);
ajv.addSchema(read('context-snapshot.schema.json')); ajv.addSchema(read('event.schema.json'));
const validate = ajv.compile(read('relay-frame.schema.json'));
const forbidden = new Set(read('forbidden-fields.json'));
const types = new Set(['hello','ping','pong','events','ack','state','pet_event','command','encrypted']);
const decoder = new TextDecoder('utf-8', { fatal: true });
function hasForbidden(value) {
  const pending = [value];
  while (pending.length) {
    const current = pending.pop();
    if (!current || typeof current !== 'object') continue;
    for (const key of Object.keys(current)) {
      if (forbidden.has(key)) return true;
      pending.push(current[key]);
    }
  }
  return false;
}
function positive(value, fallback) {
  const n = Number(value ?? fallback);
  if (!Number.isSafeInteger(n) || n <= 0) throw new Error('Invalid relay limit');
  return n;
}

export function createRelayServer(options = {}) {
  const now = options.nowFn ?? Date.now;
  const maxBytes = positive(options.MAX_FRAME_BYTES ?? options.maxFrameBytes ?? process.env.MAX_FRAME_BYTES, 65536);
  const rate = positive(options.RATE_LIMIT_PER_SEC ?? options.rateLimitPerSec ?? process.env.RATE_LIMIT_PER_SEC, 50);
  const ttl = positive(options.IDLE_TTL_MS ?? options.idleTtlMs ?? process.env.IDLE_TTL_MS, 86400000);
  const logger = options.logger ?? (entry => console.log(JSON.stringify(entry)));
  const rooms = new Map();
  const server = http.createServer((req, res) => {
    if (req.method === 'GET' && req.url === '/healthz') { res.writeHead(200, {'Content-Type':'text/plain'}); res.end('ok'); }
    else { res.writeHead(404); res.end(); }
  });
  // Validate UTF-8 ourselves so malformed text receives the documented 4003 code.
  const wss = new WebSocketServer({ noServer: true, maxPayload: maxBytes, skipUTF8Validation: true, perMessageDeflate: false });
  server.on('upgrade', (req, socket, head) => {
    wss.handleUpgrade(req, socket, head, ws => wss.emit('connection', ws, req));
  });
  function expireIdleRooms() {
    for (const [id, room] of rooms) if (now() - room.lastActivity >= ttl) {
      rooms.delete(id);
      for (const ws of room.clients.values()) ws.close(4000, 'idle expired');
    }
  }
  wss.on('connection', (ws, req) => {
    ws.on('error', () => {}); // ws handles protocol closes; never log error/input text.
    let url;
    try { url = new URL(req.url, 'http://relay.local'); } catch { ws.close(4001, 'invalid pairing'); return; }
    const roomId = url.searchParams.get('room'), device = url.searchParams.get('device');
    if (url.pathname !== '/ws' || !/^[a-fA-F0-9]{64}$/.test(roomId ?? '') || !['phone','laptop'].includes(device) || url.searchParams.getAll('room').length !== 1 || url.searchParams.getAll('device').length !== 1) { ws.close(4001, 'invalid pairing'); return; }
    expireIdleRooms();
    const id = roomId.toLowerCase();
    const room = rooms.get(id) ?? { clients: new Map(), lastActivity: now() };
    rooms.set(id, room);
    const old = room.clients.get(device);
    room.clients.set(device, ws); room.lastActivity = now();
    if (old) old.close(4002, 'replaced');
    let arrivals = [];
    ws.on('close', () => { if (room.clients.get(device) === ws) room.clients.delete(device); });
    ws.on('message', (data, binary) => {
      // Replaced/expired sockets cannot forward while their closing handshake runs.
      if (rooms.get(id) !== room || room.clients.get(device) !== ws || ws.readyState !== WebSocket.OPEN) return;
      if (now() - room.lastActivity >= ttl) { expireIdleRooms(); return; }
      const timestamp = now();
      arrivals = arrivals.filter(t => timestamp - t < 1000);
      arrivals.push(timestamp);
      if (arrivals.length > rate) { ws.close(4008, 'rate limit'); return; }
      const bytes = Buffer.byteLength(data);
      if (bytes > maxBytes) { ws.close(1009, 'frame too large'); return; }
      let frame;
      try { if (binary) throw new Error(); frame = JSON.parse(decoder.decode(data)); }
      catch { ws.close(4003, 'invalid JSON object'); return; }
      if (!frame || typeof frame !== 'object' || Array.isArray(frame)) { ws.close(4003, 'invalid JSON object'); return; }
      if (!types.has(frame.type)) { ws.close(4004, 'invalid type'); return; }
      if (hasForbidden(frame) || !validate(frame) || (frame.type === 'events' && frame.events.some(e => Buffer.byteLength(JSON.stringify(e)) > 8192)) || (frame.type === 'hello' && frame.device !== device)) { ws.close(4004, 'invalid frame'); return; }
      if (frame.type === 'command' && (frame.issuedAt > timestamp || frame.expiresAt <= timestamp || frame.expiresAt <= frame.issuedAt || (options.authorizeCommand && !options.authorizeCommand({room:id, device, frame})))) { ws.close(4004, 'invalid command'); return; }
      room.lastActivity = timestamp;
      // These are the only four logging fields; input values never reach diagnostics.
      try { logger({ room: id.slice(0,8), device, type: frame.type, byteLength: bytes }); } catch { /* logging must not crash the relay */ }
      const peer = room.clients.get(device === 'phone' ? 'laptop' : 'phone');
      if (peer?.readyState === WebSocket.OPEN) {
        if (peer.bufferedAmount > maxBytes * rate) { peer.close(4008, 'slow peer'); return; }
        peer.send(data, { binary: false }, () => {});
      }
    });
  });
  const timer = setInterval(expireIdleRooms, Math.min(ttl, 60000)); timer.unref();
  const ready = new Promise((resolve, reject) => { server.once('error', reject); server.listen(options.port ?? Number(process.env.PORT ?? 8080), options.host ?? '127.0.0.1', () => { server.off('error', reject); resolve(server.address()); }); });
  async function close() {
    clearInterval(timer); for (const ws of wss.clients) ws.terminate(); rooms.clear();
    await new Promise(resolve => wss.close(resolve));
    await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  }
  return { server, wss, ready, close, expireIdleRooms, address: () => server.address() };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const relay = createRelayServer({ host: process.env.HOST ?? '0.0.0.0' });
  relay.ready.catch(() => { console.error('Relay startup failed'); process.exitCode = 1; });
  for (const signal of ['SIGINT','SIGTERM']) process.once(signal, () => relay.close().catch(() => { process.exitCode = 1; }));
}
