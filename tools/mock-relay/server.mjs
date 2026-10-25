import http from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import url from 'url';

const MAX_FRAME_SIZE = 65536; // 64 KB
const MAX_RATE_PER_SEC = 50;

const RENDEZVOUS_ALLOWED_TYPES = new Set([
  'pair_offer', 'pair_join', 'pair_reveal', 'pair_confirm', 'pair_cancel'
]);

const SESSION_ALLOWED_TYPES = new Set([
  'hello', 'ping', 'pong', 'events', 'ack', 'state', 'pet_event', 'held_list', 'command', 'cmd_ack', 'error'
]);

export function startMockRelay(options = {}) {
  const host = options.host || '127.0.0.1';
  const port = options.port || 0; // 0 for random port

  // Room registry: roomId -> { type: 'rendezvous' | 'session' | null, clients: Map(device -> ws), createdAt, lastActivity }
  const rooms = new Map();

  const stats = {
    connections: 0,
    disconnections: 0,
    framesForwarded: 0,
    framesRejected: 0,
    roomsCreated: 0,
    roleCollisions: 0,
    thirdClientRejections: 0
  };

  const server = http.createServer((req, res) => {
    res.writeHead(404);
    res.end();
  });

  const wss = new WebSocketServer({ noServer: true });

  server.on('upgrade', (req, socket, head) => {
    const parsed = new URL(req.url, 'http://127.0.0.1');
    if (parsed.pathname !== '/ws') {
      socket.write('HTTP/1.1 404 Not Found\r\n\r\n');
      socket.destroy();
      return;
    }

    const roomId = parsed.searchParams.get('room');
    const device = parsed.searchParams.get('device');

    if (!roomId || (device !== 'phone' && device !== 'laptop')) {
      socket.write('HTTP/1.1 400 Bad Request\r\n\r\n');
      socket.destroy();
      return;
    }

    // Check room rules
    let room = rooms.get(roomId);
    if (!room) {
      room = {
        id: roomId,
        type: null,
        clients: new Map(),
        createdAt: Date.now(),
        lastActivity: Date.now()
      };
      rooms.set(roomId, room);
      stats.roomsCreated++;
    }

    // Role check: max 2 clients, only one per device role
    if (room.clients.has(device)) {
      stats.roleCollisions++;
      socket.write('HTTP/1.1 403 Forbidden\r\n\r\n');
      socket.destroy();
      return;
    }

    if (room.clients.size >= 2) {
      stats.thirdClientRejections++;
      socket.write('HTTP/1.1 403 Forbidden\r\n\r\n');
      socket.destroy();
      return;
    }

    wss.handleUpgrade(req, socket, head, (ws) => {
      wss.emit('connection', ws, req, roomId, device);
    });
  });

  wss.on('connection', (ws, req, roomId, device) => {
    stats.connections++;
    const room = rooms.get(roomId);
    room.clients.set(device, ws);
    room.lastActivity = Date.now();

    const messageTimestamps = [];

    ws.on('message', (data, isBinary) => {
      room.lastActivity = Date.now();

      // Size check
      if (data.length > MAX_FRAME_SIZE) {
        stats.framesRejected++;
        ws.close(1009, 'Frame too large');
        return;
      }

      // Rate limit check: 50 msgs/sec
      const now = Date.now();
      messageTimestamps.push(now);
      while (messageTimestamps.length > 0 && now - messageTimestamps[0] > 1000) {
        messageTimestamps.shift();
      }
      if (messageTimestamps.length > MAX_RATE_PER_SEC) {
        stats.framesRejected++;
        ws.send(JSON.stringify({ v: 1, t: 'error', code: 'rate_limited' }));
        return;
      }

      // JSON Parse
      let frame;
      try {
        frame = JSON.parse(data.toString('utf8'));
      } catch (err) {
        stats.framesRejected++;
        ws.send(JSON.stringify({ v: 1, t: 'error', code: 'schema_invalid' }));
        return;
      }

      if (frame.v !== 1 || !frame.t) {
        stats.framesRejected++;
        ws.send(JSON.stringify({ v: 1, t: 'error', code: 'schema_invalid' }));
        return;
      }

      // Determine room type from first frame
      if (room.type === null) {
        if (frame.t === 'pair_offer') {
          room.type = 'rendezvous';
        } else {
          room.type = 'session';
        }
      }

      // Check allowed types
      const allowed = room.type === 'rendezvous' ? RENDEZVOUS_ALLOWED_TYPES : SESSION_ALLOWED_TYPES;
      if (!allowed.has(frame.t)) {
        stats.framesRejected++;
        ws.send(JSON.stringify({ v: 1, t: 'error', code: 'schema_invalid', detail: `Type ${frame.t} not allowed in ${room.type} room` }));
        return;
      }

      // Forward to other peer in the room
      const peerDevice = device === 'phone' ? 'laptop' : 'phone';
      const peerWs = room.clients.get(peerDevice);
      if (peerWs && peerWs.readyState === WebSocket.OPEN) {
        peerWs.send(data.toString('utf8'));
        stats.framesForwarded++;
      }
    });

    ws.on('close', () => {
      stats.disconnections++;
      room.clients.delete(device);
      if (room.clients.size === 0) {
        // Keep room around until expiry or delete
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
        stats,
        rooms,
        close: () => new Promise((res) => {
          wss.close(() => {
            server.close(() => res());
          });
        })
      });
    });
  });
}

if (process.argv[1] && process.argv[1].endsWith('tools/mock-relay/server.mjs')) {
  let port = 8080;
  const args = process.argv.slice(2);
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--port' && args[i + 1]) port = parseInt(args[++i], 10);
  }

  const inst = await startMockRelay({ port });
  console.log(`Mock Relay listening on ws://127.0.0.1:${inst.port}/ws`);
}
