# Prompt C0 Completion Report — Dhyan Sync Protocol v1.0.0

Commit: `contracts-M0: complete sync contract artifacts and mocks`  
Date: 2026-10-25  
Author: Antigravity  
Branch: `contracts` in worktree `C:\Users\abhig\OneDrive\Desktop\dhyan-contracts`

---

## 1. Executive Summary

Prompt C0 delivers the complete contract implementation, deterministic test vectors, mock servers, mock peers, validation tools, and comprehensive attack test suites for the **Dhyan Sync & Ingest Protocol v1.0.0** (`docs/contracts/SYNC_CONTRACT.md`).

All requirements have been met with zero deviations:
- **7 outer frame schemas** (`contracts/sync/schemas/`): `pair_offer`, `pair_join`, `pair_reveal`, `pair_confirm`, `pair_cancel`, `hello`, `encrypted_frame`.
- **11 session message schemas**: `frame_envelope`, `ping`, `pong`, `events`, `ack`, `state`, `pet_event`, `held_list`, `command`, `cmd_ack`, `error`.
- **6 local ingest schemas**: `ingest_auth`, `ingest_ext_heartbeat`, `ingest_edit_burst`, `ingest_resume_first_edit`, `ingest_vscode_state`, `ingest_tab_category`.
- **Reference crypto module** (`contracts/sync/ref/crypto.mjs`): P-256 ECDH, commit-reveal, transcript hash, 6-digit SAS (`123 456`), HKDF-SHA256, HMAC-SHA256, AES-256-GCM with 12-byte counter nonce (`0x00000000 || ctr`) and ASCII AAD (`dhyan|1|${t}|${d}|${ctr}`).
- **Deterministic test vectors** (`contracts/sync/test-vectors.json`): Golden vectors with fixed scalars and nonces reproducing identical ECDH secrets, transcript hashes, SAS digits, confirm MACs, pairing key `PK`, room `sid`, hello MACs, session keys (`k_l2p`, `k_p2l`), and encrypted ciphertexts.
- **Mock servers and attack suites**:
  - `tools/mock-ingest/server.mjs`: `127.0.0.1` binding, Origin validation (`chrome-extension://<id>`), 3-second auth timeout, constant-time token comparison, rate limiting (20 msgs/s), max frame 4096 bytes, strict schema validation, recursive forbidden-field scan.
  - `tools/mock-ingest/attack-suite.mjs`: 7 automated attack scenarios (wrong origin, wrong token, non-auth first message, forbidden fields, oversized frame, rate limit exhaustion).
  - `tools/mock-relay/server.mjs`: In-memory forwarder, room segregation (rendezvous vs session), role collision prevention (max 1 phone, max 1 laptop), 3rd-client rejection, 64 KB max frame, 50 msgs/s rate limit.
  - `tools/mock-peer/index.mjs`: Programmatic and CLI peer supporting pairing flow, session handshake, frame encryption/decryption, and attack modes (tamper, replay, old-ctr, wrong-direction, wrong-key, expired-cmd, bad-sas).
- **Tooling**:
  - `tools/verify-sync.mjs`: Compares laptop JSONL vs phone export (exact match, zero duplicate event IDs, monotonic seq).
  - `tools/privacy-scan.mjs`: Scans logs, JSONL, and frames for forbidden fields and canary strings.
- **Test Suites**:
  - `tools/test/contract-crypto.test.mjs`
  - `tools/test/contract-schemas.test.mjs`
  - `tools/test/contract-mocks.test.mjs`
  - `tools/test/contract-tools.test.mjs`

---

## 2. Verification & Test Results

### 2.1 Contract Test Suite (`npm run check:contracts`)
```
> check:contracts
> node --test tools/test/contract-*.test.mjs

✔ public vectors reproduce deterministically (6.0672ms)
✔ HKDF matches RFC 5869 SHA256 case 1 (1.2102ms)
✔ P256 key pair generation and shared secret match vectors (0.6531ms)
✔ pairing transcript, SAS digits, and confirm MACs match vectors (0.3993ms)
✔ session handshake, hello MACs, and session keys match vectors (0.2869ms)
✔ both directions authenticate and reject changed metadata, key and ciphertext (0.721ms)
✔ counter nonce is big endian with 12 bytes (0.1881ms)
✔ SAS preserves leading zeros and formats 6 digits grouped 3-3 (0.5897ms)
✔ mock-ingest handles all attack cases correctly (240.7986ms)
✔ mock-relay and mock-peers execute complete authenticated pairing and session communication (270.4692ms)
✔ all valid fixtures validate according to their schema (19.8246ms)
✔ all invalid fixtures are rejected (4.6291ms)
✔ walkForbiddenFields catches forbidden keys in shallow and nested structures (0.2469ms)
✔ body schemas reject extra properties and invalid bounds (0.127ms)
✔ verify-sync detects clean sync and violations (16.5775ms)
✔ privacy-scan detects forbidden fields and canary strings (2.5903ms)
ℹ tests 16
ℹ suites 0
ℹ pass 16
ℹ fail 0
```

### 2.2 Full Repository Test Suite (`npm test`)
```
> test
> node --test tools/test/*.test.mjs

ℹ tests 71
ℹ suites 0
ℹ pass 71
ℹ fail 0
```

### 2.3 Repository Checks (`npm run checks`)
```
> checks
> node tools/check-schema-sync.mjs && node tools/check-twin-purity.mjs && node tools/check-no-persona-imports.mjs

schema-sync: PASS (1.0.0; 1 Kotlin version file(s))
twin-purity: PASS (96 twin file(s); absent modules are not target verification)
no-persona-imports: PASS (235 production file(s); absent modules are not target verification)
```

---

## 3. PowerShell Commands for Running Mocks & Tools

### Start Mock Ingest Server
```powershell
node tools/mock-ingest/server.mjs --port 47821 --token "my-super-secret-token"
```

### Run Ingest Attack Suite
```powershell
node tools/mock-ingest/attack-suite.mjs
```

### Start Mock Relay Server
```powershell
node tools/mock-relay/server.mjs --port 8080
```

### Run Sync Verification
```powershell
node tools/verify-sync.mjs path\to\laptop-events.jsonl path\to\phone-events.jsonl
```

### Run Privacy Scanner
```powershell
node tools/privacy-scan.mjs path\to\logs_or_events_dir
```

---

## 4. Contract Change Requests (CCRs)

**Zero CCRs required.**
- `core-spec/event.schema.json` already defines `gap.reason` as `string, minLength 1`, which accepts `peer_silent`, `relay_down`, `schema_mismatch`, `queue_overflow`.
- Contract additions CA-1 through CA-9 are approved in `COORDINATION.md` and fully implemented in schemas and reference code.
- Contract version remains `1.0.0` and is now frozen.

# Next-phase setup and review status — 2026-10-29

Setup commit c0af347 archives only available DHYAN prompts and records owner approval DD-1–DD-14; DD-15 disabled. No main/F1 changes or push. Missing companion files: DHYAN_NEXT_PROMPTS.md and DHYAN_NEXT_ACTIONS.md; their contents/sectionL are not fabricated. Electron binary exists45.0.0,249797632bytes; GUI not launched/verified. AW live check remains unverified from7B.

Read-only F1 review at detached d1e3a06 is complete; report ../dhyan-review/notes/REVIEW_F1.md. Verdict NOT READY TO MERGE: persona replay can apply wrong store after selection changes; pre-existing touched chart overruns vals[80]; deltaL interval mislabeled as SHOW interval; Baseline2m still hard-coded; NotReady zeros still rendered; print-only sensitivity tests miss regressions; pitch has forbidden zero-knowledge/universal guarantees and unsupported metrics. No F1 source edited. GradleselectedViewModeltests19/pass19/fail0/errors0/skipped0, BUILD SUCCESSFUL7m16s,25tasks executed. Rootnpmtest74/pass73/fail1(CRLF.env.examplehygiene); guards PASS; actuator6checks0violations. Passing tests do not dismiss source findings. Fullsuite/devicebuild/Gate2 not claimed. W6 still waits for F1 merge.

## W3 boundary choices still needed
- Pairing: proposed retain the initial immutable laptop pair_offer in mock rendezvous relay, deliver it to late phone before accepting pair_join, reject replacement offers, clear on cancel/expiry. Production relay remains untouched until8L (DD-7). Tests: late join, order, replacement, expiry, third role. Alternative requires an explicitly versioned readiness handshake. This addresses CCR-1, not an already-approved DD.
- ACK: proposed durable rejection tombstones (device,seq,id,controlledreason) and a settled cumulative cursor so good events can still be applied in partial batches without falsely claiming rejected payloads were stored. Whole-version incompatibility remains atomic reject. Alternative is atomic rejection of any-invalid batch, requiring explicit change to section6. Tests: valid-invalid-valid, restart/backfill, duplicate cursor, never ACK unpersisted data. This addresses CCR-2.
- Schema-version coordination: DD-1 authorizes core1.1.0 with schema-sync green, while the queue explicitly says Codex never touches phone/. The existing checker requires phone/app/src/main/java/com/dhyan/app/SchemaVersion.kt VERSION="1.1.0"; the matching phone/app/src/test/java/com/dhyan/app/SchemaVersionTest.kt assertion must also become1.1.0. Proposed exception is ONLY those two literal versions (no imports/runtime/otherphoneedit), or the phone owner supplies that commit for coordination. No exception assumed and no phone file edited.

No core bump, sync executable mutation, relay rewrite, main merge, F1 merge/rebase, phoneStage8,7C orGate2 work was performed during setup/review. W3 must preserve the remaining scope/gates and not claim contract completion until approved choices and checks are implemented. Review report is an uncommitted report in the throwaway review checkout, not an F1 commit.

# W3 / CX-2 completion — 2026-10-29

Read current main7558bfc, signed DD-1–DD-15 and newly supplied docs/prompts/DHYAN_NEXT_ACTIONS.md F3. Rebased contracts onto main; preserved signed main decisions and canonical prompt. Earlier DD-15-disabled notes are historical; current signed DD-15 controls. No subagents used. Owner separately approved immutable initial offer, delegated ACK design and allowed Codex to choose the version-only phone coordination update.

## Implemented and scope
Core/sync versions1.1.0 exactly once. videoState optional playing/paused/none in both event definitions; new reconciliation matrices. Closed command unions, opaque heldId/all only, independent cmd_ack, nullable declared shape, canonical state enums, optional adapterHealthy (no new state enum), canonical held app categories. Legacy flat relay schema marked deprecated; production relay/ untouched. DD-6 reasons remain open controlled classification tokens; local endpoints remain same emitting role. Phone edits ONLY SchemaVersion.kt literal and matching SchemaVersionTest literal1.1.0; no phone runtime or migration. Root manifest unchanged.

Approved pairing CCR implemented in mocks: original immutable offer retained/delivered before late phone share, role/order checks and expiry cleanup. Session hello replies once to avoid a lost initial hello blocking late connections. ACK choice is durable settled cursor: validate identity/version for whole batch, then atomically commit valid canonical events or metadata-only rejection tombstones before advancing/ACK. Mock restart/dedup, valid-invalid-valid, failed write/noACK and privacy regressions pass. Rejected content never reaches ledger/callback/diagnostic plaintext buffers. Production8L/8P1 must implement equivalent store transactions; no Room migration or deployed relay is claimed.

All underlying crypto outputs/ciphertexts compared against HEAD's old vectors and were identical after removing metadata fields. Regenerated vector SHA256(canonical UTF8/LF)=3a3e92d7952ee3ed4a95787771c7732daf3699c86cd5f09b37e8a820142202a9. Public fixed test vectors are not production keys. Final byte diff reviewed before commit; no new crypto library.

## Real verification
- npm test: tests110, pass110, fail0, duration_ms5272.4381.
- npm run check:contracts: tests52, pass52, fail0, duration_ms1122.145.
- npm run checks: schema-syncPASS1.1.0(1Kotlinversionfile), twin-purityPASS96, no-persona-importsPASS284.
- npm run build-vectors: public vector hash above; deterministic regeneration test passes.
- Full stdout: docs/contracts/verification/W3-root-tests.txt and W3-contract-tests.txt.
- Corrected the observed Windows CRLF hygiene failure by comparing normalized line endings while still checking exact .env keys/content; no .env file changed. No swallowed test failure. A new encrypted-settlement test initially exposed whole-wrapper forbidden-key rejection preventing per-event settlement; fixed envelope-only validation followed by per-event privacy rejection. Final tests pass.
- Phone Gradle/full Kotlin suite, Kotlin crypto vectors, Node20 target, hardware, cloud/WSS, actual pairing/actuation were NOT run in W3: not verified on target. The known earlierF1 review defects are not silently certified by its owner merge. Gate2 remains owner-run.

## Antigravity update
Read/share docs/contracts/ANTIGRAVITY_W3_HANDOFF.md. It states exact two literal changes, no runtime edits, state/command/video changes, transactional settlement/migration needs and Gate2/M0 boundaries. No Antigravity thread exists in connected Codex threads, and no direct Antigravity messaging connector is available; no direct chat delivery is claimed. Owner explicitly authorized sending an update; the reviewable repository handoff is the available channel.

## Remaining limitations
No silent historical1.0event rewrite; both peers must upgrade before optional adapterHealthy under strict old receivers. Pure wire crypto labels remainv1; semver1.1.0 is schema revision, not a new key label. Current7A lacks a direct fresh adapter-health diagnostic and persisted focus history;7B's explicit unknown/no-data limitations remain. Ingest lockout identity, controlled detail vocabularies and full production transport lifecycle still require7C/8L review. Mock auto-confirm is a test harness, not a human confirmation UI; passing it is not a security audit. Mock DurableEventLedger is bounded reference storage, not a production sync subsystem or universal power-loss guarantee. DD-8 phone migration belongs8P1 after approved M0/Gate2. No main merge/push.
