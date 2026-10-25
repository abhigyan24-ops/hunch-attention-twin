# Stage 1B verification — 2026-10-25

Local Windows, Node v24.13.0, npm 11.6.2. Node 20 and cloud WSS: not verified on target. Sandbox registry DNS initially failed with ENOTFOUND; sandbox localhost tests failed with EACCES. Approved network/local socket access allowed installation and tests.

`npm.cmd install`, exit 0:

```text
added 7 packages, and audited 8 packages in 7s

2 packages are looking for funding
  run `npm fund` for details

found 0 vulnerabilities
```

`npm test`, exit 0, final output:

```text
> test
> node --test test/*.test.js

✔ GET /healthz returns 200 ok (179.3268ms)
✔ invalid room, device and path close 4001 (27.2866ms)
✔ pair forwards bidirectionally without echo or cross-room leakage (49.9932ms)
✔ same-role replacement closes old socket 4002; old close cannot remove new role (9.6451ms)
✔ invalid JSON, array, null, binary, malformed UTF-8 close 4003 (25.763ms)
✔ unknown type, extra content, bad schema and reserved ciphertext close 4004 (14.5377ms)
✔ oversized frame closes 1009 (6.7555ms)
✔ deep invalid JSON structure is rejected without overflowing privacy traversal (11.8987ms)
✔ sliding one-second rate limit closes 4008 and resets after window (46.8269ms)
✔ fake clock expires idle rooms and active valid frames refresh TTL (11.9054ms)
✔ logs contain only short room, role, allowed type and byteLength; rejected input never leaks (13.3011ms)
✔ commands respect expiry and optional peer authorization hook (10.7029ms)
ℹ tests 12
ℹ suites 0
ℹ pass 12
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 1523.646
```

`node test-client.mjs <temporary-local-ws-url> --pair`, subprocess against createRelayServer({port:0}), exit 0:

```text
PAIR PASS: hello, ping, pong, state forwarded in both directions.
```

No cloud resources, Caddy process, Linux systemd service or E2E encryption were deployed or tested.
