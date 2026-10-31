# Laptop ↔ Android: Pairing, Connection and Sync (v2 — Dhyan Production Edition)

A complete, fault-tolerant specification for connecting the **Dhyan Laptop App** (Electron + Python Attention Twin Sidecar) and the **Dhyan Android App** (`com.dhyan.app`) so that pairing takes under 60 seconds, the link self-heals across LAN, Hotspot, USB (`adb reverse`), and Relay WebSockets, events arrive **effectively exactly-once** in strict per-device sequence order, clocks align within $\le 50\text{ ms}$, and the phone's Gatekeeper and Cloud Spirit Pet never get stuck if the laptop disconnects.

---

## 1. Guarantees and UX Targets

### 1.1 System Guarantees

| Guarantee | Meaning | Mechanism |
| :--- | :--- | :--- |
| **No Loss** | Every phone usage event, notification decision, and pet nudge action reaches the laptop | Durable Room `outbox` on Android, resumed from the laptop's contiguous acknowledged watermark |
| **No Duplicates** | Retries and flapping Wi-Fi never create duplicate rows in the Attention Twin DB | Primary/Unique key `(device, epoch, seq)` with `INSERT OR IGNORE` |
| **Strict Per-Device Ordering** | Events from the phone are applied in exact causal sequence | Monotonically increasing per-epoch `seq` assigned inside the Room insert transaction |
| **Bounded Time Error** | Cross-device timestamps align within $\le 50\text{ ms}$ on LAN/USB and $\le 250\text{ ms}$ via relay | 8-sample min-RTT NTP offset estimator + `SystemClock.elapsedRealtime()` jump detection |
| **Fail-Safe Disconnection** | Phone never stays stuck in DND if the laptop sleeps or loses Wi-Fi | 90-second expiring focus leases (`STATE`); automatic revert to user's prior interruption filter |
| **End-to-End Authenticated Confidentiality** | Neither LAN snoopers nor the Relay Server can read or inject frames | Signed ephemeral P-256 ECDH $\rightarrow$ HKDF-SHA256 $\rightarrow$ AES-256-GCM with pinned identity keys |

### 1.2 UX Targets (Aligned with Dhyan Aesthetic)

* **Top Status Pill (`StatusPillBadge`):** Directly drives the signature Dhyan status pill (`● Connected (Wi-Fi)`, `● Connected (USB)`, `● Connected (Relay)`, or `○ Offline · 42 queued`).
* **First-Time Pairing:** Under 60 seconds via 1 QR scan and 1 confirmation click on the laptop.
* **Zero Manual IPs or Ports:** Users never type IP addresses or port numbers.

---

## 2. Architecture: Three Independent Layers + Twin Bridge

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 4. DHYAN TWIN BRIDGE   Forwards verified inbox batches to Python        │
│                        FastAPI Sidecar (`POST /ingest/batch`) & pushes  │
│                        `GET /policy/cache` + `GET /twin/state` to phone │
├─────────────────────────────────────────────────────────────────────────┤
│ 3. SYNC LAYER          Event log replication · 90s DND state leases ·   │
│                        Policy cache push · Nudge telemetry · NTP sync   │
├─────────────────────────────────────────────────────────────────────────┤
│ 2. SECURE SESSION      Mutual auth with pinned P-256 identity keys ·    │
│                        Forward-secret ECDH · AES-256-GCM counter frames │
├─────────────────────────────────────────────────────────────────────────┤
│ 1. TRANSPORT           Raced in parallel ("Happy Eyeballs"):            │
│                        USB (`127.0.0.1`) · Saved LAN TCP · mDNS · Relay │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Technology Choices

| Concern | Choice | Why |
| :--- | :--- | :--- |
| **Discovery** | QR payload $\rightarrow$ USB loopback (`127.0.0.1:48211`) $\rightarrow$ Saved LAN IPs $\rightarrow$ mDNS $\rightarrow$ Relay | Works on home Wi-Fi, campus Wi-Fi, phone hotspots, and USB cables |
| **LAN / USB Transport** | Raw TCP with `[u32be length][payload]` framing | Avoids Android cleartext HTTP/WS restrictions (`cleartextTrafficPermitted`) while encrypted at Layer 2 |
| **Relay Transport** | WebSocket over TLS (`wss://`) carrying the exact same encrypted frames | Traverses NAT and campus AP isolation without exposing plaintext |
| **Session Crypto** | Ephemeral ECDH (`secp256r1` / `prime256v1`) + ECDSA-SHA256 + HKDF-SHA256 + AES-256-GCM | 100% native in Node `crypto` and Android `javax.crypto` / `AndroidKeyStore` |
| **Persistence** | Android: Room (`OutboxEvent`, `PeerRecord`) + DataStore · Laptop: `better-sqlite3` (`WAL`) | Crash-safe atomic transactions |

---

## 4. Pairing: 60-Second QR Flow & Numeric Fallback

### 4.1 Compact QR Payload (`< 400 bytes` JSON in `base64url`)

```json
{
  "v": 1,
  "id": "9f2c1a4b8e1104d2f9a0123456789abc",
  "pub": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
  "sec": "q3Jx89L_mP2vR7kW1nZ0aB...",
  "a": ["192.168.1.23:48211", "10.0.0.7:48211", "127.0.0.1:48211"],
  "m": "_attwin._tcp",
  "r": "wss://relay.dhyan.app/v1",
  "room": "b7e1904a2c8f...",
  "exp": 1730003600
}
```

* Includes `127.0.0.1:<port>` automatically in candidate list `a` so if the user is connected over USB with `adb reverse`, pairing succeeds in $< 50\text{ ms}$ even on completely blocked venue Wi-Fi.

---

## 5. Loopback-Safe Android Network Socket Binding

When connected to a Wi-Fi hotspot without internet, Android routes default sockets over mobile data unless bound to `wifi`. However, binding `127.0.0.1` (`adb reverse`) to the `wifi` interface fails with `ENETUNREACH`. The loopback-safe `NetworkBinder` (`com.dhyan.app.sync.NetworkBinder`) solves this by routing loopback sockets directly and LAN sockets over the bound Wi-Fi interface.

---

## 6. Secure Session Cryptography

- **Hardware-Backed Device Identity:** Stored in AndroidKeyStore under `dhyan_attwin_id_v1` using P-256 (`secp256r1`) with SHA-256 signing.
- **Pure-JVM RFC 5869 HKDF-SHA256:** Implemented in `CryptoPrimitives.hkdfSha256`, producing identical session key expansion as Node's `crypto.hkdfSync('sha256', ikm, salt, info, length)`.
- **Authenticated Symmetric Transport:** `SecureChannel` performs AES-256-GCM encryption with 128-bit authentication tags and monotonic 96-bit sequence nonces.

---

## 7. Unified Wire Framing & Twin Bridge

Every frame on both TCP and Relay WebSocket uses a unified length-prefixed format:
`[ u32be byteLength ][ payload ]`
- Handshake frames: UTF-8 JSON.
- Session frames: encrypted by `SecureChannel`.

When phone batches are committed on Desktop, `forwardBatchToTwinSidecar()` (`laptop/src/main/link/twin-bridge.ts`) immediately delivers the events and nudge telemetry to `http://127.0.0.1:8765/ingest/batch`.
