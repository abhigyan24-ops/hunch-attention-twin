# Dhyan encrypted relay — Stage 8L

Node20 target, in-memory `ws` relay. Keep the full repository layout: the relay loads frozen outer schemas from `contracts/sync/schemas/`. It never decrypts or validates notification bodies. Endpoints: GET /healthz returns ok; WebSocket /ws?room=<64hex>&device=phone|laptop. No plaintext legacy frames are accepted.

From the repository root:
```powershell
npm --prefix relay test
node relay/server.js
# Separate terminal, local synthetic session only:
node relay/test-client.mjs ws://127.0.0.1:8080/ws --pair
```

The smoke exchanges authenticated hello and encrypted ping/pong/state in both directions using ephemeral shared test credentials; it does not claim real device pairing. Tests additionally use both MockPeer roles through actual ECDH/SAS pairing, then100 encrypted events. No keys, pairing codes, SAS or payloads are printed. Single-role mode is preserved as `node relay/test-client.mjs <ws-url> --device phone|laptop --room <64hex>`. Both synthetic peers must receive the same32-byte base64 test key through DHYAN_SMOKE_KEY; never use a real pairing key or print/store it. Authentication is mandatory under the new wire protocol.

`createRelayServer({port:0,nowFn,logger,rendezvousTtlMs,idleTtlMs})` returns ready/server/wss/address/expireIdleRooms/close. Await ready before connecting. Defaults:65536-byte frame,50messages/second/socket,5minute fixed rendezvous lifetime,24hour session idle lifetime. Environment MAX_FRAME_BYTES/RATE_LIMIT_PER_SEC/IDLE_TTL_MS and corresponding options remain supported. Invalid endpoints reject HTTP400; join limit HTTP429; invalid frames/rates/roles/expiry close1008; oversize close1009. Closing/replaced/expired sockets cannot forward queued frames.

Rooms classify from the first laptop pair_offer or a session frame. Rendezvous keeps only the first immutable offer for a late phone, checks join/reveal/confirm order and clears on cancel/expiry. A duplicate/third role in rendezvous aborts the existing pairing with a controlled pair_cancel; in session it rejects only the newcomer. No role is silently replaced. Rendezvous joins are limited to10/min/IP. X-Forwarded-For is trusted only when the actual TCP peer is127.0.0.1 (or IPv4-mapped equivalent), for the Caddy proxy. Logs contain only eight-character room prefix, role, approved type/controlled rejection token and byte count.

## AWS Option A

Install the full repository at /opt/dhyan on the owner-reviewed Node20 host. Replace `<ELASTIC-IP-with-dashes>.sslip.io` in relay/Caddyfile. Caddy terminates TLS and proxies127.0.0.1:8080; keep that port private. The supplied systemd unit uses an unprivileged user. Review relay/deploy.sh, which checks the layout, installs root test dependencies and relay dependencies, runs tests and configures systemd/Caddy. Do not enable access logging of query strings or pairing secrets. No deployment was run here. Cloud WSS/TLS: not verified on target.

## Render/Railway Option B

Deploy the full repository, service root at repository root. Build `npm ci && npm --prefix relay ci --omit=dev`; start `node relay/server.js`; health path /healthz. Configure HOST=0.0.0.0 and platform PORT; the platform terminates TLS. Use one instance: rooms cannot span replicas, and restart loses them. No cloud resources were created or changed here. Provider TLS, sleep and persistence behavior: not verified on target.

## Security limits

Room IDs are rendezvous/session routing metadata, not identity proof. Endpoints authenticate ECDH/SAS pairing and encrypted sessions; relay never knows PK. Metadata type/direction/counter/size/timing remains visible. Prototype lacks forward secrecy after PK compromise, an independent security audit, and production DoS guarantees. See the frozen sync contract threat model. Do not claim cloud or device success from local mock tests.
