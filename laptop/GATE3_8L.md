# Stage 8L local verification and Gate 3 runbook

Branch s8-laptop-sync; dedicated worktree C:\Users\abhig\OneDrive\Desktop\dhyan-s8l. Do not change the phone worktree. Do not run Gradle/adb from this track. Gate3 requires owner-reviewed8L and8P branches merged, both apps built from main, a verified deployed WSS relay and phone permissions. None of those device/cloud prerequisites is asserted here.

## Current blocker

CCR-8L-1: laptopState has no approved mapping to the mandatory coarse wire focusState for stuck/idle_open/offline. The owner was asked to approve the limited mapping (flowing->working, browsing/gaming->distracted, away->break) and suppression of unmapped states. Until answered, the main app leaves stateMappingApproved=false and visibly says state mapping awaits owner review. Events, authenticated ping/pong, held-item mirror and commands remain implemented and tested; state heartbeat integration is gated, not claimed complete. No placeholder working/focus value is emitted for unknown activity.

No full ContextSnapshot is fabricated from sparse laptop inputs. The earlier missing persisted-focus-snapshot source remains open, so today's observed total may still be no-data. Generic focus scores stay rule-based, not calibrated.

## Automated local commands

```powershell
Set-Location C:\Users\abhig\OneDrive\Desktop\dhyan-s8l
npm run check:s8l
npm test
node laptop/scripts/relay-smoke.mjs
```

check:s8l runs relay tests, laptop typecheck/build/tests, root guards, contract tests and the local two-role encrypted smoke. Local sessions use injected synthetic confirmation only in tests; the app has no auto-confirm control. The real relay tests cover ECDH pairing through both mock roles and100 canonical events. Session tests cover partial-invalid settlement/tombstones, crash recovery/no premature ACK, MAC/tag/direction/replay rejection, old-hello rekey rollback rejection, commands and retention. A fake-clock60second outage is a simulation, not an actual phone airplane-mode check.

## Gate 3: laptop-side steps1–9

Run these only after the owner has reviewed and merged the branches and resolved CCR-8L-1. For local-only rehearsal you may use the worktree and `node relay/server.js`; this does not verify deployed TLS. Keep your existing phone app/session under Antigravity's control.

1. In the laptop terminal, record the test window and start the app:
   ```powershell
   Set-Location C:\Users\abhig\OneDrive\Desktop\IIITN
   $Gate3Started = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
   npm --prefix laptop run dev
   ```
   Panel -> Pairing and sync: enter the actual owner-approved `wss://.../ws`, Start pairing. Enter the shown code on the phone, compare SAS on both screens, press Yes on both only if they match. Never include code, SAS, PK, token or raw notifications in reports/logs. No command deploys a relay here.
2. File -> Exit, rerun `npm --prefix laptop run dev` in that same terminal. Expect encrypted credential recovery and automatic reconnect without a new pairing. Keep `$Gate3Started` for step7.
3. Choose coding/120minutes/strict and click Declare session on phone. Declare locally is explicitly separate. Check the actual phone session and cmd_ack; laptop sent status alone does not prove application.
4. Owner triggers a held notification through the existing phone flow. Panel should show only counts/categories/opaque heldId actions, no content. Click Show now; verify actual release on phone. A stale/missing held list disables item controls. Stop holding on phone sends only kill_switch.on=true; no laptop control re-enables holding.
5. Compare both screens for several minutes, recording categorized states/timestamps only. Gate remains pending until state mapping is approved and tested. Do not treat uncalibrated confidence as a probability.
6. Owner puts the phone into airplane mode for60seconds while laptop activity continues, then restores it. Observe stale/unsynced states, gap timestamps, reconnect and canonical backfill. No commands are queued or replayed; an interrupted command outcome may be unknown.
7. Quit normally so writes flush, then use the laptop events.jsonl path and the canonical phone export path supplied by Antigravity/owner:
   ```powershell
   $LaptopEvents = '<actual laptop userData path>\events.jsonl'
   $PhoneExport = '<actual canonical phone export path>.jsonl'
   node tools/verify-sync.mjs $LaptopEvents $PhoneExport
   node laptop/scripts/verify-sync-coverage.mjs $LaptopEvents $PhoneExport $Gate3Started
   ```
   The shared checker compares common IDs but does not prove missing-event coverage. The second wrapper additionally requires every laptop event in the recorded test window on the phone exactly once and compares canonical fields. Neither prints payloads. If rotation occurred, combine the required canonical laptop files in sequence in an owner-selected scratch export; do not delete or edit live storage.
8. Local production-code attack regressions can be rerun without touching the live paired device:
   ```powershell
   node --test --test-name-pattern='direction/replay|live commands|wrong key|old authenticated hello' laptop/test/sync-session.test.mjs
   node --test --test-name-pattern='pairing cancellation|bad peer confirmation' laptop/test/sync-core.test.mjs
   ```
   The shared MockPeer CLI currently prints ready and does not implement the guide's --tamper/--replay/--expired-cmd command-line flags. Do not claim those commands exercised the live phone. A real-device attack harness is still an integration dependency; tests invoke the exported mock API against the production SessionClient and real relay locally.
9. Use a counts-only wrapper around tools/privacy-scan.mjs so a failing scan never echoes sensitive excerpts:
   ```powershell
   $RelayLog = '<owner-selected metadata-only relay log file>'
   node laptop/scripts/privacy-check.mjs $RelayLog $LaptopEvents $PhoneExport
   ```
   Scan all relevant rotated JSONL and sync/remote-settlements.jsonl files as additional explicit paths. Expect0violations. Do not run the verbose shared CLI on real data: it prints matching excerpts on failure. Send only pass/fail counts, never offending content or pairing material.

## Limitations and integration requests

- Phone/cloud/TLS/Windows/real extensions and ActivityWatch: not verified on target by8L. Node host verification uses the actual installed version recorded in notes, not a Node20 target claim.
- CCR-8L-2: strict hello schema requires proto containing1; consequently the relay rejects a no-common-version hello before a client can display a precise update-app state. Current clients count/reject it; cross-version negotiation needs a reviewed contract decision, not an unapproved schema edit.
- Gate3 shared-tool issues are handled by laptop-local wrappers; no tools/mock-*, shared contract, phone or governance file was modified.
- Room schema3 and command façade compatibility belong to Antigravity. No claim is made about its current migration or Reno14 results.
- Local remote-settlement journal fsync/recovery is tested; storage hardware power-loss guarantees and long-term remote journal compaction are not proven here. No destructive data migration or plaintext key fallback.
- Independent audit and forward secrecy remain outside the prototype security claims. Deployment scripts were updated but never run against a host.
