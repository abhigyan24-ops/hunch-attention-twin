# Antigravity handoff — W3 schema1.1.0

Owner approved DD-1–DD-15, retained initial pairing offer, and delegated ACK semantics. Work is isolated on `contracts`; no main merge/push by Codex. Read docs/OWNER_DECISIONS.md and SYNC_CONTRACT sections15/16 before8P1 M0.

Phone changes made here are **only** the version literals1.0.0 ->1.1.0 in:
- phone/app/src/main/java/com/dhyan/app/SchemaVersion.kt
- phone/app/src/test/java/com/dhyan/app/SchemaVersionTest.kt

This is the narrow F3 schema-sync allowance plus its matching assertion, explicitly accepted by the owner as Codex's best coordination option. No UI, Room, collector, twin, safety, actuator, Gradle, manifest or runtime logic edited. Rebase future phone work only after the owner merges the controlled contract bump. Do not copy old constants back or destructively migrate user data.

Important contract deltas:
- Event schemaVersion1.1.0; optional videoState playing/paused/none; old incompatible events reject pending a reviewed historical mapping.
- state.declared is null or {task,plannedMin,strict,startedTs}; optional adapterHealthy disambiguates active/unclassified vs silent. No new laptopState; confidence is uncalibrated, never a correctness percentage.
- Strict command args, opaque heldId/all only, kill_switch.on; cmd_ack is separate from event ACK; laptop on:false remains unauthorized.
- ACK cursor means durably SETTLED (valid canonical event OR controlled rejection tombstone), committed atomically before ACK. Bad identity/version rejects whole batch; isolated invalid payload settles metadata only; never persist rejected text. See section16 and tools/mock-peer/settled-events.mjs/tests.
- Phone events are still flattened and lack canonical envelope identity. DD-8 authorizes a real Room migration only after owner-approved8P1 M0; no destructive migration.8P1 must describe transactional settlement/tombstones and restore/dedup semantics.
- Gate2 remains owner-run after F1 merge. Do not start phoneStage8 just because Node mocks pass. Check current Android foreground-service restrictions in8P1 M0 as DD-10 requires.

Codex has no connected Antigravity messaging tool or Codex thread for that app; this file is the update to read/share, not a claim that a chat message was delivered. The owner can give Antigravity: `git show contracts:docs/contracts/ANTIGRAVITY_W3_HANDOFF.md`.
