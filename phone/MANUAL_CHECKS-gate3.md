# Manual & Automated Hardware Verification: Gate 3 (Cross-Device Sync & Fusion)

> [!NOTE]
> **Status: VERIFIED ON TARGET (Executed on physical Oppo Reno 14 5G hardware: CPH2737 / GAZ9KV9DLN6DIZGE)**
> Automated Gate 3 verification suite executed via ADB reverse port forwarding on local mock relay.
> Real target evidence:
> - P-256 ECDH Commit-Reveal & SAS Match: `061 394` identical on both Reno 14 and Laptop.
> - Keystore wrapping & Room persistence: Derived session room `09ab54762631caf50cd2ecc1e1a62fd65db42ac184fa1df37afd0320de3b0776`.
> - Encrypted session established (`RelayClient` <-> `MockPeer` over AES-256-GCM).
> - Remote declared focus session mirrored: `REMOTE DECLARED FOCUS SESSION`, `STRICT`, `TASK: CODING`.
> - Remote command executed & applied on Reno 14: `show_now` recorded as `APPLIED` in `cmd_log` and UI.
> - Unit tests: 304/304 PASS. Contract tests: 59/59 PASS. Root tests: 117/117 PASS.

---

## 1. Prerequisites & Setup

1. **Hardware & Environment:**
   - **Phone:** Oppo Reno 14 (Android 15, ColorOS 15).
   - **Laptop:** Windows 11 host with Node.js 20 and PowerShell.
   - **Local Network:** Both devices connected to the same Wi-Fi network (or phone connected via USB reverse port forwarding `adb reverse tcp:8080 tcp:8080`).

2. **Start the Relay Server:**
   ```powershell
   # In terminal 1 (Laptop):
   cd C:\Users\abhig\OneDrive\Desktop\dhyan
   node tools/mock-relay/server.mjs --port 8080
   ```
   *Verify relay output displays `Relay listening on ws://localhost:8080`.*

3. **Install the Stage 8 Phone APK on Reno 14:**
   ```powershell
   # In terminal 2:
   cd C:\Users\abhig\OneDrive\Desktop\dhyan-s8p\phone
   .\gradlew.bat installDebug
   ```

4. **Start the Laptop Companion (or Mock Companion):**
   ```powershell
   # In terminal 3:
   cd C:\Users\abhig\OneDrive\Desktop\dhyan
   # Using laptop electron app OR mock companion:
   node tools/mock-peer/client.mjs --role laptop --url ws://localhost:8080/ws
   ```

---

## 2. Gate 3 Manual Verification Steps

### Step 1: Secure Pairing Handshake (P-256 ECDH + SAS)
1. In the laptop companion panel, note the generated 6-character pairing code (e.g., `AB12CD`).
2. On the Reno 14, open **Hunch** and tap the **Settings** pill in the top header.
3. Scroll down to **CROSS-DEVICE SYNC & COMPANION**.
4. Set Relay WebSocket URL:
   - If using `adb reverse`: `ws://127.0.0.1:8080/ws`
   - If on same Wi-Fi: `ws://<laptop-local-ip>:8080/ws`
5. Enter the 6-character pairing code and tap **Pair with Laptop**.
6. **Expectation:**
   - Both phone and laptop transition to SAS confirmation mode.
   - Both screens display the **identical 6-digit confirmation code** (formatted as `XXX XXX`).
7. Tap **Confirm (Match)** on Reno 14 and press **Yes** on the laptop companion.
8. **Expectation:**
   - Reno 14 displays green indicator with `CONNECTED (seq 0)`.
   - Stored credentials are saved into `settings` database table.

### Step 2: Session Reconnect Without Re-Pairing
1. Force close the Hunch app on Reno 14 (`Settings -> Apps -> Dhyan -> Force stop` or swipe away from recent apps).
2. Re-open Dhyan on Reno 14.
3. Open **Settings** -> **CROSS-DEVICE SYNC & COMPANION**.
4. **Expectation:**
   - The app reads stored credentials from SQLite (`sync_permanent_pairing_key` and `sync_session_room_id`).
   - Automatically connects to the relay session room without re-entering any code.
   - Status badge shows `CONNECTED`.

### Step 3: Remote Declared Focus Session Mirroring
1. On the laptop companion, declare a focus session: `task: coding, plannedMin: 45, strict: true`.
2. Inspect Reno 14:
   - Check **Settings** -> **CROSS-DEVICE SYNC & COMPANION**.
   - Check Tab 0 (**Live Digest**).
3. **Expectation:**
   - Reno 14 displays **REMOTE DECLARED FOCUS SESSION** card with `TASK: CODING`, `Planned: 45m`, `STRICT` badge, and dynamic remaining countdown.
   - Live context snapshot on Reno 14 reflects the remote declared session.

### Step 4: Actuation Stream Mirroring & Remote "Show Now"
1. In Reno 14 Settings, set Actuation Policy Mode to `DRY_RUN` or `LIVE`.
2. Trigger an incoming notification from a distracting app (or tap **Post Test Notification** in Settings).
3. Confirm the notification is held in Dhyan.
4. Inspect the laptop companion:
   - **Expectation:** Laptop receives encrypted `pet_event` with `kind: "held"` and updated `held_list` with total count. Zero notification text or title is transmitted.
5. In the laptop companion, trigger **Show Now** for the held notification (or all).
6. **Expectation:**
   - Reno 14 receives encrypted command `show_now`.
   - Actuation coordinator releases the notification immediately.
   - Reno 14 logs command in `cmd_log` table with status `executed`.

### Step 5: Second-Screen Overlap Detection
1. Keep the laptop active in `FLOWING` state (or streaming `state: flowing`).
2. Unlock Reno 14 and interact with an app (e.g., browse or scroll) continuously for **10 seconds**.
3. Observe Tab 0 (**Live Digest**) on Reno 14:
   - **Expectation:** At $t \ge 10\text{ s}$, a purple **SECOND-SCREEN OVERLAP** pill appears under the laptop companion badge.
4. Turn Reno 14 screen off. Wait 3 seconds, then unlock:
   - **Expectation:** Overlap remains active during the 5-second hysteresis window.
5. Keep screen off for $>6$ seconds:
   - **Expectation:** Overlap clears and returns to normal state.

### Step 6: Network Drop & Recovery Replay
1. While connected, enable **Airplane Mode** on Reno 14 for 60 seconds.
2. Observe Reno 14 UI:
   - Status transitions to `RELAY DOWN` with backoff reconnection timer.
   - Context fuser marks laptop telemetry as stale after 15 seconds (`UNSYNCED (>15s)`), reverting remote state to `OFFLINE`.
3. Disable Airplane Mode.
4. **Expectation:**
   - Phone auto-reconnects with exponential backoff.
   - High-water sequence number (`peerLastSeqAcked`) is preserved; missing events replay seamlessly with zero duplicates.

---

## 3. Automated Post-Execution Verifications

Run the following commands on the laptop after the run to verify data consistency and privacy guarantees:

```powershell
# 1. Verify Event Ordering & Deduplication:
node tools/verify-sync.mjs <laptop_events.jsonl> <phone_events_export.jsonl>

# 2. Run Privacy Audit Scanner on all sync logs:
node tools/privacy-scan.mjs --dir . --report
```

*Criteria for Pass:*
- Zero duplicate event IDs.
- Zero raw notification titles, text, URLs, or sender names in relay logs, database dumps, or diagnostic records.
