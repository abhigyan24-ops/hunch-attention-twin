# Stage 8L verification review — 2026-10-29

Reviewed e957a73 on s8-laptop-sync against frozen sync contract sections4–8/16, the owner-approved partial state mapping, and SPEC25.8/25.9. The branch was clean at review start. Scope stayed laptop/** plus its running notes; no phone/core-spec/contracts/shared-tool/root-package edit in this review.

## Finding fixed

P2 — delayed command reservation could permit an effect after expiry or disconnection. CommandLedger checked time before awaiting durable identity persistence, then called the handler without checking time again. SessionClient also supplied no live-origin check after that I/O boundary. Two new regressions were run before the fix: tests2/pass0/fail2, both showing actual applied rather than expired/rejected. No real device action was performed; handlers were synthetic counters.

The ledger now refreshes time on dequeue, checks expiry again immediately before effect, and checks that the originating session is still live. SessionClient supplies a current-clock callback and an identity/OPEN/key-state check for the originating WebSocket. Reservation remains durable before any effect, so uncertain/rejected commands are not retried on reconnect. Expiry during persistence returns expired; loss of connection returns rejected/connection_lost. No args or raw content were added to persistence/diagnostics.

Three review regressions pass: expiry during reservation; disconnection during reservation; and an actual encrypted command received through WebSocket with a deliberately blocked reservation followed by disconnect. Targeted tests3/pass3/fail0/duration_ms2386.1269. Typecheck also passed after the fix.

## Fresh complete verification

`npm run check:s8l` exited0:
- relay tests10/pass10/fail0/duration_ms1325.4376;
- laptop typecheck/buildPASS (710ms/29ms/4.31s build phases);
- laptop tests86/pass86/fail0/duration_ms11475.8255;
- schema-sync PASS1.1.0 (1 Kotlin version file), twin-purity PASS96files, no-persona-imports PASS361productionfiles;
- contract tests59/pass59/fail0/duration_ms1650.345;
- local synthetic encrypted hello/ping/pong/state smokePASS.

Root `npm test` exited0:117/pass117/fail0/duration_ms6896.7733. Recursive laptop/node_modules *.node scan returned0. Exact stdout is in VERIFICATION_8L_REVIEW.txt and VERIFICATION_8L_REVIEW_ROOT.txt.

The existing suite exercises crypto vector intermediates, explicit SAS confirmation bound to an attempt, bad pairing/third-client paths, encrypted credential recovery, real relay100-event forwarding, durable partial settlement and payload-free tombstones, privacy scans, replay/direction/tag/MAC attacks, old-hello nonce rollback, reconnect/backfill and command authorization/idempotency. State tests verify approved mappings,5second heartbeat/on-change delivery, strict unmapped/silent/stale/future suppression and continuing ping/events/commands. No new frozen-schema assumption was introduced.

## Remaining limits

This is local source review and automated verification, not Gate3 or an independent security audit. Real phone/Reno14, deployed relay/WSS/TLS, Windows8L UI, real extensions and ActivityWatch remain not verified on target. CCR-8L-2 unsupported-version negotiation and the shared mock CLI's absent live-device attack flags remain documented in DECISIONS_8L.md and GATE3_8L.md. No actual phone test or deployment was attempted.

Ready for owner review of the corrected code. No merge or push performed. Only the root test's generated notification sample was restored after testing.
