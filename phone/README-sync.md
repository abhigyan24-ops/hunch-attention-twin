# Dhyan Sync Protocol (Stage 8P1) â€” Runbook & Interoperability Harness

This document describes how to run and verify peer-to-peer sync between the Android phone client and the mock laptop companion / mock relay over a local LAN (or via ADB port forwarding).

---

## 1. Architecture & Security Invariants

- **Wire Encryption:** AES-256-GCM authenticated encryption with 12-byte big-endian counter nonces and ASCII AAD (`dhyan|1|<type>|<dir>|<ctr>`).
- **Key Agreement:** P-256 ECDH + HKDF-SHA256, verified out-of-band via a 6-digit numeric Short Authentication String (SAS, `XXX XXX`).
- **Permanent Credentials:** 32-byte permanent pairing key (`PK`) wrapped with Android Keystore AES-GCM; 64-char hex session room ID (`sid`).
- **Privacy Invariant:** Categories and counts only. No notification text, titles, URLs, contact names, or raw keystrokes are ever transmitted on wire or stored in database tables.
- **Fail-Open & Authorization:** Laptop companion can ONLY assert `kill_switch.on = true`. Any attempt by laptop to assert `kill_switch.on = false` is rejected with status `rejected` and reason `unauthorized`.

---

## 2. Setting Up the Mock Relay & Peer on Laptop / Developer PC

All commands should be executed from PowerShell at the repository root.

### Step 1: Start the Mock Relay
```powershell
node tools/mock-relay/server.mjs --port 8080
```
Console output will show: `Mock relay listening on port 8080`.

### Step 2: Set Up ADB Reverse Forwarding (Optional, for USB testing)
If testing on physical device over USB instead of Wi-Fi LAN:
```powershell
adb reverse tcp:8080 tcp:8080
```
This maps `ws://127.0.0.1:8080/ws` on the phone directly to port 8080 on the PC.

### Step 3: Run the Mock Laptop Peer
```powershell
node tools/mock-peer/index.mjs --role laptop --relay ws://127.0.0.1:8080/ws --code 234567
```
(Replace `127.0.0.1` with your developer PC LAN IP if connecting over Wi-Fi without ADB reverse).

The laptop mock peer will generate its P-256 key share, join the rendezvous room, and wait for the phone.

---

## 3. Pairing from the Phone App

1. In the Dhyan app, tap the **Settings** pill to open `SettingsBottomSheet`.
2. Scroll to the **Cross-Device Sync** section.
3. Enter the Relay URL: `ws://127.0.0.1:8080/ws` (or your LAN IP).
4. Enter the Pairing Code: `234567`.
5. Tap **Pair Devices**.
6. The phone connects to the rendezvous room, exchanges key shares, and displays the **6-digit SAS code** (`XXX XXX`).
7. Verify that the SAS digits on the phone display match the SAS digits printed in the laptop console.
8. Tap **Confirm** on the phone.
9. Credentials (`PK` and `sid`) are derived, encrypted in Android Keystore, and saved to the database. Status changes to **Connected**.

---

## 4. Normal Data Flow & Mirror Verification

- **Outbound Event Streaming:** Phone emits events in batches (<= 50 events, 32 KB, 2 s intervals). The laptop console prints received events.
- **Durable Settlement:** Received laptop events are validated against `core-spec` schemas and `PrivacyGuard`. Any invalid event settles as a metadata tombstone without storing forbidden content.
- **Remote Commands:**
  - `show_now { heldId: "..." }` or `show_now { all: true }`: releases held notifications immediately on phone.
  - `declare_session { task: "coding", plannedMin: 25, strict: false }`: declares focus session.
  - `kill_switch { on: true }`: triggers fail-open emergency release of all held notifications.

---

## 5. Security & Attack Verification Scenarios

Run these tests from PowerShell on the PC to verify that the phone client correctly enforces protocol security policies:

### Scenario A: SAS Mismatch / Man-in-the-Middle Attack
```powershell
node tools/mock-peer/index.mjs --role laptop --relay ws://127.0.0.1:8080/ws --code 234567 --attack bad-sas
```
**Expected outcome:** The phone displays mismatched SAS digits or fails commitment verification, rejecting the session without deriving permanent keys.

### Scenario B: Unauthorized Kill Switch Deactivation
```powershell
node tools/mock-peer/index.mjs --role laptop --relay ws://127.0.0.1:8080/ws --attack disable-kill-switch
```
**Expected outcome:** Phone rejects command with `cmd_ack { status: "rejected", reason: "unauthorized" }`. Holding is NOT re-enabled.

### Scenario C: Tampered Ciphertext / 3-Tag Failure Policy
Send 3 corrupted encrypted frames to the phone:
**Expected outcome:** AES-256-GCM AEAD tag check fails. On the third failure within 60 seconds, phone closes the WebSocket connection with code `1008` (Policy Violation).

### Scenario D: Replay / Old Counter Attack
Send a frame with a counter `ctr <= lastCtr`:
**Expected outcome:** Phone silently rejects the frame. Counter is not updated; no payload is parsed or executed.

### Scenario E: Expired / Duplicate Commands
- Command with `exp < now`: Phone replies `cmd_ack { status: "expired" }`.
- Repeated command with already-settled `cmdId`: Phone replies `cmd_ack { status: "duplicate" }`.
