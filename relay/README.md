# Dhyan relay — Stage 1B

Node 20 target, in-memory `ws` relay. From this folder: `npm install`, `npm test`, `npm start`. In another terminal: `node test-client.mjs ws://127.0.0.1:8080 --pair`. Single peer mode: `node test-client.mjs <ws-url> --device phone --room <64-hex>`; it waits for a separately connected peer exchanging the same test frames.

`createRelayServer({port:0, nowFn, logger})` returns `{ready, server, wss, address, expireIdleRooms, close}`. Await ready before connecting and close after use. Limits accept options `MAX_FRAME_BYTES`, `RATE_LIMIT_PER_SEC`, `IDLE_TTL_MS` or environment values (65536, 50, 86400000 by default). Rate limiting uses a sliding second. Idle expiry closes sockets with 4000, sweeping at most every minute and before join/message processing. There is no persistence or offline queue; clients own backfill and deduplication.

Strict schemas live in the unchanged sibling core-spec directory, so deploy the repository, not server.js alone. Frames must pass the frozen schema, recursive privacy key checks, and nested event 8192-byte caps. Errors never include input bodies. Logs have exactly room prefix (8 chars), device, approved type and byteLength. Close codes: 4001 invalid pairing/path, 4002 replaced role, 4003 malformed JSON/object/UTF-8/binary, 4004 unapproved type/schema/content/command, 1009 size, 4008 rate/slow receiver. Valid frames go only to the opposite role in the same room.

## AWS Option A

Use an Ubuntu EC2 host in the intended region, Node 20, Caddy, and a checkout at /opt/dhyan containing core-spec/ and relay/. Replace `<ELASTIC-IP-with-dashes>.sslip.io` in Caddyfile with the public hostname pointing at the host. Allow HTTPS 443 (and HTTP 80 for certificate issuance), restrict SSH to your administration IP; keep 8080 bound to loopback behind Caddy. Review and run `bash deploy.sh`. The systemd service runs as an unprivileged user. Ensure that user can read /opt/dhyan. Caddy terminates TLS and proxies WebSocket upgrades. Do not enable access logging of query strings or pairing secrets. Test `/healthz` and run the pair CLI against `wss://<hostname>`. Cloud deployment: **not verified on target**. No cloud resources were created.

## Render/Railway Option B

Deploy the whole repository. Set the service root to relay/, build command `npm ci --omit=dev`, start command `npm start`, health path `/healthz`; preserve sibling core-spec/ in the deployed checkout. The platform supplies PORT; use HOST=0.0.0.0. Use the platform HTTPS/WSS hostname. Keep one instance: in-memory rooms cannot span replicas, and restart loses room state. Check the provider's current sleep/retention settings and reconnect both peers after restart. Run the pair CLI using the public WSS URL. This alternative is **not verified on target**.

## Security and Stage 8

Room possession currently pairs the two claimed roles; it is not strong peer authentication. Commands get schema and expiry validation, with an optional authorizeCommand hook. Explicit confirmed pairing, high-entropy keys, revocation and durable client command idempotency/acknowledgements are still pending. Do not attach notification actuators to this scaffold without those gates. See ../DECISIONS_PENDING.md (consolidated Stage 1B notes).

Stage 8 intends opaque AES-GCM ciphertext forwarding under WSS. The frozen contract currently permits only the reserved `{ "type": "encrypted" }` marker. Ciphertext fields are rejected until an authorized contract change specifies authenticated metadata, nonce uniqueness, tag checking, replay prevention and key rotation. PBKDF2 of a short pairing code alone does not provide production-grade pairing security. No encryption is implemented here.

## Stage 8L protocol update

The old flat relay protocol is retired. The server imports strict outer schemas from ../contracts/sync/schemas, retains only the initial immutable laptop pairing offer, and forwards encrypted session frames. It never decrypts. Duplicate roles close1008; oversized frames close1009. Rendezvous joins are limited to10/min/IP; X-Forwarded-For is used only when the actual TCP peer is loopback (Caddy). Do not expose a loopback-trusted proxy endpoint directly.

Keep the entire repository layout at /opt/dhyan. The deployment script installs root test dependencies and relay production dependencies before testing; the canonical schemas and mock test harness must be present. AWS Option A and hosted Option B need this repository layout, not a server.js-only upload. Render/Railway root is the repository, build `npm ci && npm --prefix relay ci --omit=dev`, start `node relay/server.js`; configure HOST=0.0.0.0 and the platform PORT. TLS termination is external; no deployment was executed here.

Local synthetic encrypted-session smoke: `node relay/test-client.mjs ws://127.0.0.1:8080/ws --pair`. It uses fresh ephemeral shared credentials only within that test process, exchanges authenticated hello and encrypted ping/pong/state in both directions, and prints no keys or bodies. Full human-confirmed ECDH pairing is tested separately with the laptop client and mock phone. Cloud wss, TLS and target-device interoperability remain not verified on target.
