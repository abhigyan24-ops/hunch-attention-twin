# DHYAN Sync and Ingest Contract v1.0.0

Commit this file as `docs/contracts/SYNC_CONTRACT.md` on `main`. It is the single authority for everything that crosses a process or device boundary in Stages 7 to 9: the extension-to-laptop ingest protocol, the pairing flow, the relay protocol, session encryption, commands, time and ordering.

- Sources: spec sections 6, 12.4, 13, 14, 25.8, 25.9, 25.11, 25.14.
- Where this contract adds to or replaces spec wording, the item is listed in section 14 (CA-1 to CA-9) for owner approval.
- **Change control:** nobody changes this file, the JSON schemas under `contracts/sync/`, or the test vectors without a Contract Change Request (CCR). See the guide, section 6. Any change bumps `contracts/sync/VERSION`, regenerates the vectors, and both platforms must pass before merge.
- **Not production security.** See section 12 for the threat model and the things this design does not claim.

All times are epoch milliseconds (UTC). All binary fields are standard base64 with padding. All JSON is UTF-8, one JSON object per WebSocket text frame.

---

## 1. Parties and roles

| Party | Role |
|---|---|
| Phone app | Reference time base. Runs the twin and the actuator. Authoritative for held items and decisions |
| Laptop app (Electron main process) | Sensor, pet, state inference. Hosts the local ingest server. Authoritative for laptop events |
| Relay | Dumb forwarder (Node `ws`). Never decrypts. In-memory only |
| VS Code extension, browser extension | Report counts to the laptop app over localhost only. Never talk to the relay or the phone |

Telemetry (events, state, pet events, held list) flows both ways but is never executed. Commands are separate messages with their own authorization (section 8).

---

## 2. Constants

| Name | Value | Notes |
|---|---|---|
| `PROTOCOL_VERSION` | `1` | integer, in every frame as `v` |
| Max frame size (relay) | 65536 bytes | relay closes with 1009 above this |
| Max plaintext body | 32768 bytes | after decryption |
| Relay rate limit | 50 messages per second per client | spec 14.3 |
| Events batch | at most 50 events or 32 KB, flushed within 2 s | spec 14.3 |
| Heartbeat `state` interval | 5 s | spec 12.4 |
| Peer silent threshold | 30 s | emit a `gap` event, spec 14.3 |
| Laptop snapshot stale threshold | 15 s | three missed heartbeats; then `laptopState = offline` |
| Phone snapshot stale threshold | 30 s | shown as stale on the laptop |
| Pairing code | 6 characters from `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`, uppercase | about 30 bits; a rendezvous token, not a secret |
| Pairing validity | 5 minutes | or 3 failed confirmations, whichever first |
| Relay join attempts on rendezvous rooms | 10 per minute per IP | |
| Ping interval | 30 s | clock offset estimation |
| Command default expiry | 30 s, maximum 60 s | |
| Command idempotency window | 24 h | |
| Reconnect backoff | 1, 2, 4, 8, 16, 30 s then 30 s, with 20% jitter | |
| Unacked event retention (sender) | 72 h or 50 000 events, then drop oldest and emit `gap queue_overflow` | |
| Local ingest default port | `47821` (configurable) | assumption CA-9 |
| Ingest max frame | 4096 bytes; 20 messages per second per connection | |

---

## 3. Relay-visible frames (outer layer)

The relay validates only this layer: valid JSON object, size limit, rate limit, `v == 1`, `t` in the allowlist for the room type.

### 3.1 Room types and URLs

| Room | URL | Who joins | Allowed `t` |
|---|---|---|---|
| Rendezvous | `wss://<relay>/ws?room=<rv>&device=phone\|laptop` with `rv = hex(sha256("dhyan-rendezvous-v1\|" + CODE))` (64 hex) | the two devices, only during pairing | `pair_offer`, `pair_join`, `pair_reveal`, `pair_confirm`, `pair_cancel` |
| Session | `wss://<relay>/ws?room=<sid>&device=phone\|laptop` with `sid` = 64 hex from section 5.4 | the paired devices | `hello`, `ping`, `pong`, `events`, `ack`, `state`, `pet_event`, `held_list`, `command`, `cmd_ack`, `error` |

The relay tells the two room types apart by a registry it keeps: a room is rendezvous if its first frame is `pair_offer`, otherwise session. Rendezvous rooms expire after 5 minutes. Session rooms expire after 24 hours idle. Max two clients per room, one per `device` role; a second client for the same role is rejected with close code 1008.

### 3.2 Frame shapes

Clear pairing frames and `hello` have their own fields (sections 4 and 5). Every other session frame is an encrypted frame:

```json
{ "v": 1, "t": "events", "d": "l2p", "ctr": 17, "c": "<base64 ciphertext+tag>" }
```

`d` is `l2p` (laptop to phone) or `p2l`. `ctr` is a strictly increasing integer starting at 1 per direction per session.

The relay never sees a body, a `mid`, a timestamp, or any payload field. It does see: message type, direction, counter, size, timing, room id. This metadata leak is accepted for the prototype (section 12).

---

## 4. Pairing (authenticated key agreement with human confirmation)

Goal: no reliance on a low-entropy code as a key. The code only gets both devices into the same rendezvous room. Keys come from ECDH. Humans compare a short code on both screens (this satisfies spec 25.8 "explicit confirmation on both devices").

Curve: **P-256 (secp256r1)**. Public keys are 65-byte uncompressed points (`0x04 || X || Y`), base64. ECDH output is the 32-byte shared X coordinate. HKDF is HKDF-SHA256 (RFC 5869). Implement HKDF by hand on Android (a 15-line HMAC loop); Node has `crypto.hkdfSync`.

Sequence (all frames in the rendezvous room, all `v:1`):

1. Laptop shows `CODE` and creates the rendezvous room. It generates an ephemeral key pair (`skL`, `pkL`) and a 16-byte random `nL`. It sends
   `pair_offer {device:"laptop", commit: b64(SHA256(pkL || nL))}`.
2. Phone user types `CODE`. Phone generates (`skP`, `pkP`) and 16-byte `nP`, sends
   `pair_join {device:"phone", pk: b64(pkP), n: b64(nP)}`.
3. Laptop sends `pair_reveal {device:"laptop", pk: b64(pkL), n: b64(nL)}`. Phone checks `SHA256(pkL || nL)` equals the commit from step 1. On mismatch it sends `pair_cancel {reason:"commit_mismatch"}` and aborts.
4. Both compute `secret = ECDH(sk_self, pk_peer)` and the transcript
   `T = SHA256("dhyan-pair-v1" || pkL || pkP || nL || nP)`.
   The **SAS** (short authentication string) is `uint32_be(T[0..4]) mod 1000000` shown as six digits, zero padded, grouped `123 456`.
5. Both screens show the SAS and ask "Do the digits match?". Only after the local user presses Yes does a device send
   `pair_confirm {device, mac: b64(HMAC-SHA256(kConf, "confirm|" + device))}` with `kConf = HKDF(secret, salt=T, info="dhyan-pair-confirm-v1", 32)`.
6. Pairing completes on a device when it has (a) the local user's Yes and (b) a valid peer `pair_confirm`. Then both derive and persist:
   - `PK = HKDF(secret, salt=T, info="dhyan-pairing-key-v1", 32)`
   - `sid = hex(HKDF(PK, salt="", info="dhyan-room-v1", 32))` (64 hex, the permanent session room)
   Both then leave the rendezvous room. The laptop invalidates the code.
7. Failure paths: user presses No, wrong confirm MAC, timeout, or a third client attempts to join: send `pair_cancel {reason}` and discard all key material. After 3 failures or 5 minutes the laptop generates a new code. A visible, recoverable error is shown on both devices.

**What the SAS buys:** an active attacker in the middle (including the relay operator who can brute-force the code from its hash) cannot make the SAS match without about a one-in-a-million guess, because the laptop committed to its key before seeing the phone's. This is why commit-reveal is mandatory.

**Storage of `PK`:** laptop uses Electron `safeStorage` (Windows DPAPI) writing an encrypted file in `userData`; phone wraps it with an Android Keystore AES-GCM key and stores the blob in the app-private settings table. `PK`, `skL`, `skP`, `secret`, `T`, SAS, code: never logged, never in source control, never in events.

**Unpair / re-pair:** "Unpair" on either device deletes `PK` and `sid`, sends nothing, and the other device finds out when its next hello fails. Re-pairing is a fresh run of this section. Replacing a lost device means unpair on the surviving device, then pair again.

---

## 5. Session establishment and encryption

### 5.1 Derived keys

```
kHello = HKDF(PK, salt="", info="dhyan-hello-mac-v1", 32)
```

### 5.2 `hello` (clear, authenticated)

```json
{ "v":1, "t":"hello", "device":"phone", "sn":"<b64 16 random bytes>",
  "proto":[1], "lastSeqAcked": 1234, "mac":"<b64>" }
```

`mac = HMAC-SHA256(kHello, "hello|" + device + "|" + sn + "|" + lastSeqAcked)`.

- `sn` is fresh random per connection. `lastSeqAcked` is the highest peer event `seq` this device has durably stored (0 if none).
- A device that receives a `hello` with a bad MAC ignores it and counts a failure; three failures in 60 s close the connection.
- A `proto` list with no common version produces `error{code:"unsupported_version"}` and a visible "update the app" state.

### 5.3 Session keys

When both hellos are valid, with `snL` the laptop's and `snP` the phone's:

```
okm   = HKDF(PK, salt = snL || snP, info = "dhyan-session-v1", 64)
k_l2p = okm[0..32)     k_p2l = okm[32..64)
```

Keys are fresh for every connection, so the counters restart at 1 safely and nonce reuse under one key is impossible if the counter is strictly increasing.

### 5.4 Encrypted frame rules

- Cipher: AES-256-GCM, 16-byte tag appended to the ciphertext.
- Nonce (12 bytes): `0x00000000 || ctr` where `ctr` is the 64-bit big-endian counter. The nonce is not transmitted.
- AAD (authenticated, not encrypted): ASCII `"dhyan|1|" + t + "|" + d + "|" + ctr`.
- Plaintext: UTF-8 JSON `{ "mid": "<uuid>", "ts": <ms>, "b": { ...body } }`.
- Sender: use `k_l2p` when `d=="l2p"`, `k_p2l` when `d=="p2l"`. Never wrap the counter; at `ctr > 10^9` reconnect to rekey.
- Receiver: reject frames whose `d` is not the peer's direction, whose `ctr <= lastCtr` (replay or reorder), whose tag fails, whose `t` is not allowed, or whose body fails schema validation. Count each rejection by reason. Three tag failures within 60 s close the session. A rejected frame is never partially applied.
- Key rotation: automatic at every reconnect. Long-lived `PK` rotation is by unpair and re-pair.

### 5.5 Forward secrecy

None for this prototype: a stolen `PK` plus recorded traffic exposes past sessions. Listed in section 12.

---

## 6. Message bodies (inside the encrypted frame, field `b`)

All bodies have `additionalProperties: false`. Unknown fields are rejected. Schemas live in `contracts/sync/schemas/`.

| `t` | Direction | Body |
|---|---|---|
| `ping` | both | `{t0}` (sender's clock) |
| `pong` | both | `{t0, t1, t2}`: `t1` responder receive time, `t2` responder send time |
| `events` | both | `{fromSeq, events:[Event...]}` with `Event` exactly as in `core-spec/event.schema.json`. Only that sender's own events |
| `ack` | both | `{upToSeq}` cumulative |
| `state` | both | `{ts, focusState, laptopState?, phoneState?, confidence, declared, heldCount, minutesInState?}` using the enums in `core-spec/context-snapshot.schema.json` |
| `pet_event` | phone to laptop | `{kind: "held"\|"released"\|"nudge"\|"celebrate", count}` |
| `held_list` | phone to laptop | `{total, items:[{heldId, appCat, heldAt, expiresAt, status}]}` at most 50 items; **no text, no sender, no package** |
| `command` | both | `{cmdId, cmd, args, issuedTs, exp}` (section 8) |
| `cmd_ack` | both | `{cmdId, status: "applied"\|"rejected"\|"expired"\|"duplicate", reason?}` |
| `error` | both | `{code, detail?}` codes: `schema_invalid`, `unsupported_version`, `unauthorized`, `expired`, `replay`, `rate_limited`, `too_large` |

Validation on receive: JSON schema for the body, then for `events` each event against the **core-spec event schema plus the forbidden-field walk** (`core-spec/forbidden-fields.json`). A bad event is rejected and counted; good events in the same batch are still applied. `heldId` is a random opaque id with no relation to the notification key.

---

## 7. Time, order, backfill

### 7.1 Clock offset

Every 30 s each side sends `ping {t0}`. The peer answers `pong {t0, t1, t2}`. The requester records `t3` on receipt and computes

```
rtt    = (t3 - t0) - (t2 - t1)
offset = ((t1 - t0) + (t2 - t3)) / 2        # peer clock minus my clock
```

Keep the last 8 samples, ignore samples with `rtt > 2000 ms`, use the sample with the smallest `rtt`. If there is no valid sample, `offset = 0` and the receiver marks cross-device timing as `estimated`.

`tsAdj = ts + offsetToReceiver`. The **receiver** computes `tsAdj` and sets `ingestedTs`. The sender never rewrites `ts`. The phone is the reference time base: laptop events get `tsAdj` in phone time on the phone.

Events with `tsAdj` more than 5 minutes in the future are rejected and counted. Old events are accepted.

### 7.2 Sequence, ack, dedup, replay

- Each device keeps one `seq` counter for its own events (core-spec envelope). `seq` is persisted. **Holes are allowed** (events lost in a crash before they were stored). Holes never block an ack.
- `ack {upToSeq}` means: every event with `seq <= upToSeq` that the sender transmitted has been durably stored. Because the transport is ordered, the receiver acks the highest `seq` it has stored in the batch.
- On `hello`, each side replays its stored events with `seq > peer.lastSeqAcked`, in order, in batches.
- Dedup: by `(device, seq)` and by event `id`. Duplicates are dropped silently and counted. Dedup state survives restarts (it is the stored data itself).
- Reconnect never re-executes anything: telemetry replays are idempotent by dedup; commands are only valid live (section 8).
- If no frame arrives from the peer for 30 s while the relay connection is up, emit `gap {deviceFrom, deviceTo, reason:"peer_silent"}`. If the relay connection is down, emit `gap ... "relay_down"` once at disconnect and once at recovery with the outage window. Schema mismatch emits `gap ... "schema_mismatch"` and the batch is dropped with an `error{schema_invalid}`.

---

## 8. Commands (control plane)

Commands are never inferred from telemetry and are never replayed on reconnect.

| `cmd` | Allowed direction | `args` | Effect | Extra rule |
|---|---|---|---|---|
| `declare_session` | both | `{task, plannedMin (5..480), strict}` | set a declared session (`taskSource=declared`) | cannot override never-hold rules |
| `end_session` | both | `{}` | end it | |
| `show_now` | laptop to phone | `{heldId}` or `{all:true}` | release held items now | always allowed |
| `kill_switch` | both | `{on}` | `on:true` stops all holding (fail open) | **`on:false` is accepted only from the phone** |

Authorization and handling:
1. The frame must be authenticated AEAD from the paired peer's direction. Nothing else is trusted.
2. `issuedTs` and `exp` are checked against the local clock plus the clock offset; `exp - issuedTs <= 60 s`. Expired: reply `cmd_ack{expired}`.
3. `cmdId` (uuid) is looked up in the idempotency store (24 h). Duplicate: reply `cmd_ack{duplicate}` and do nothing.
4. Apply through the phone's or laptop's single public control entry point. Reply `cmd_ack{applied|rejected, reason}`.
5. No command can add or remove never-hold rules, VIPs, allow-lists, change `kappa`, or touch parameters. The set above is closed.
6. Every received command and its result is logged as a diagnostic with ids and status only (no args beyond enums).

---

## 9. Local ingest protocol (extensions to laptop app)

The laptop app runs a WebSocket server bound to **`127.0.0.1` only** at `ws://127.0.0.1:<port>/ingest`.

### 9.1 Handshake and authentication

1. **Origin check at upgrade.** A request whose `Origin` is present must be exactly `chrome-extension://<id>` where `<id>` is in the configured allow-list (`ingest.allowedExtensionIds`). Any `http://`, `https://`, `file://`, or unknown origin is rejected with HTTP 403. A request with **no** `Origin` (the VS Code extension host, Node clients) is allowed to proceed to token authentication.
2. **Token.** The laptop app generates a 32-byte random token on first run, stores it with `safeStorage`, and shows it in the panel with a Copy button. The first client message must be `{"kind":"auth","token":"<b64url>","client":"vscode"|"browser","clientVersion":"x.y.z"}` within 3 seconds, or the socket is closed. Token comparison is constant time. Wrong token: close 1008; five failures in a minute from the same remote port range block that client for 5 minutes.
3. The server replies `{"kind":"auth_ok","serverVersion":"1","config":{"heartbeatSec":5}}` or closes.
4. Only one connection per `client` kind at a time; a new authenticated one replaces the old.

Browsers do not allow custom headers on `WebSocket`, which is why the token travels in the first message and the Origin check does the cross-site protection. Web pages cannot read the extension's storage, and the Origin header of a page script is its own page origin, so the Origin allow-list blocks them.

### 9.2 Messages, extension to laptop

All fields are required unless marked optional. No other fields are allowed.

| `kind` | Fields |
|---|---|
| `auth` | see above |
| `ext_heartbeat` | `{ts}` every 5 s while active |
| `edit_burst` | `{ts, editsPerMin (int 0..2000), saves (int), errors (int), warnings (int), windowSec (int 5..120)}` |
| `resume_first_edit` | `{ts, delayMs (int 0..3600000)}` |
| `vscode_state` | `{ts, state: "flowing"\|"stuck"\|"idle_open", windowFocused (bool), debugging (bool, optional)}` |
| `tab_category` | `{ts, domainCat (one of the 14 categories), surface (feed\|shorts\|watch\|chat\|editor\|other), videoState?: "playing"\|"paused"\|"none", scrollCount? (int), tabActive (bool)}` |

Rules:
- **Counts and categories only.** No document text, file names, URLs, page titles, domains, query strings, search text. The browser extension maps a domain to a category with a built-in table and sends only the category. The `/shorts` vs `/watch` path pattern is used immediately and only the `surface` enum leaves the page.
- The laptop app wraps accepted messages into core-spec events (`edit_burst`, `resume_first_edit`, `tab_category`) with `device:"laptop"`, its own `seq`, `source:"vscode"` or `"browser"`. **Extensions never assign `id` or `seq`.**
- Rate limit 20 messages per second per connection, frame limit 4096 bytes, strict schema, forbidden-field walk, invalid messages dropped and counted (`ingest.invalid`).

### 9.3 Messages, laptop to extension

`auth_ok`, `error {code, detail?}`, `config {heartbeatSec}`. Nothing else.

### 9.4 Stale and offline

If no `ext_heartbeat` for 15 s the laptop marks that extension feed `offline`. Offline feeds never count as focus and never as distraction; inference falls back to window and idle signals.

---

## 10. Persistence and recovery obligations (sync scope)

| Item | Where | Rule |
|---|---|---|
| Outbound event queue | phone: events table + `sync_cursor`; laptop: JSONL + `sync_cursor.json` | replay from `lastSeqAcked`; survives restart |
| Command idempotency store | phone: Room table; laptop: small JSON file | 24 h, bounded size |
| `PK` | see section 4 | never plaintext on disk |
| Laptop JSONL | rotation at 10 MB, keep 14 days, partial last line discarded on start, disk-full means pause writes, keep ring buffer, show a visible warning and emit `gap` | assumptions CA-9 |
| Relay | none | in-memory only; restart means devices reconnect and replay |

---

## 11. Required tests (the contract's test suite)

1. Cross-language crypto vectors (`contracts/sync/test-vectors.json`): fixed private keys give identical ECDH, HKDF outputs, SAS digits, `PK`, `sid`, hello MAC, session keys, and the same ciphertext for fixed plaintexts. Node reference and Kotlin must both match.
2. Schema validation: valid and invalid fixtures for every message and every ingest message.
3. Attack tests via the mock peers: flipped ciphertext byte, replayed frame, old counter, wrong direction, wrong key, expired command, duplicate command, unknown `t`, oversized frame, unauthorized `kill_switch on:false` from laptop, third client joining a rendezvous room, wrong SAS, wrong Origin, wrong token. Each must be rejected with a counted reason, with no state change.
4. Backfill: drop the connection for 60 s while the peer generates events; on reconnect the receiver stores each event exactly once; `tools/verify-sync.mjs` proves it from the two exported files.
5. Privacy regression: scan relay logs and captured relay frames, laptop JSONL, phone export, ingest logs and diagnostics for forbidden keys and for canary strings.

---

## 12. Threat model and non-claims

**Protects against:** a passive network observer; a relay that reads or alters ciphertext after pairing (tampering is detected, replays are rejected); a relay or third party that guesses the pairing code (the SAS confirmation exposes an active man-in-the-middle during pairing); a web page trying to feed fake focus events to the laptop (Origin allow-list plus token plus 127.0.0.1 binding).

**Does not protect against:** compromised endpoints or malware on either device; a user who confirms mismatching digits; metadata leakage (message types, sizes, timing, room id are visible to the relay); loss of forward secrecy if `PK` is stolen; denial of service by the relay (it can drop or delay everything); physical access to an unlocked device; a malicious VS Code or browser extension with the token.

**Claims allowed in the pitch:** "end-to-end encrypted with an authenticated pairing flow and a human confirmation step; prototype-grade; not audited." **Not allowed:** "unbreakable", "production-grade", "zero-knowledge".

---

## 13. Versioning

`contracts/sync/VERSION` holds `1.0.0`. Bump rules: patch for clarifications and test fixtures, minor for backward-compatible additions (new optional field, new message type with a version gate), major for incompatible changes (needs a new `PROTOCOL_VERSION`). `contracts/sync/CHANGELOG.md` records each change and the vectors hash.

---

## 14. Additions and deviations from the spec (owner approval needed)

| ID | Item | Why |
|---|---|---|
| CA-1 | `cmd_ack` message | 25.9: commands need acknowledgement and idempotent handling |
| CA-2 | `held_list` message | 12.5: the laptop panel shows held items with Show-now; counts alone are not enough |
| CA-3 | `error` message and codes | visible, recoverable failures (25.13) |
| CA-4 | `pair_*` messages and SAS | 25.8: authenticated pairing with confirmation on both devices |
| CA-5 | Local ingest messages (section 9) | spec 13 names the channel but not its protocol |
| CA-6 | P-256 ECDH plus commit-reveal SAS replaces "PBKDF2 from the code" | 25.8 says PBKDF2 alone is not adequate; it allows "an appropriate key-agreement design". The code stays as a rendezvous token only |
| CA-7 | `gap` reasons `peer_silent`, `relay_down`, `schema_mismatch` | needed to record sync failures; requires a core-spec minor bump if missing (handled in Prompt C0) |
| CA-8 | `kill_switch on:false` only from the phone | a laptop must never be able to re-enable holding on the phone |
| CA-9 | Constants chosen without spec values: ingest port 47821, JSONL rotation 10 MB and 14 days, stale thresholds 15 s and 30 s, pairing validity 5 min | tune later without a protocol change |
