# C0 contract preflight — contracts branch

Base032b573: canonical docs/contracts/SYNC_CONTRACT.md and owner-only COORDINATION.md were committed concurrently during initial inspection. Read the actual committed board: Overlap,Gate2pending,C1–C5approved,Stage5merged,Stage6ready-for-merge pendingGate2. User also explicitly confirmed Overlap in this chat. No phone sync, Stage6/phone changes, governance changes or main writes by C0. Worktree C:/Users/abhig/OneDrive/Desktop/dhyan-contracts. No push.

Read AGENTS, HANDOFF_STATUS, DECISIONS_PENDING, STAGE5_NOTES, canonical sync contract, all supplied guide/prompt sections for C0 and relevant spec17.3,25.8/25.9. Handoff/gate docs are stale relative to recent commits; the current coordination board and git history establish branch prerequisites. Stage5 logging CCRs remain in its notes; not silently resolved here.

## C0.0 schema inspection

Current gap.reason is an open nonempty string. peer_silent,relay_down,schema_mismatch and queue_overflow already satisfy the existing schema. No enum insertion, version bump, fixture rewrite or phone SchemaVersion change is needed. Do not narrow this existing open field merely to add named reasons.

## CCRs requiring owner resolution before dependent mocks/platform implementations

### CCR-1: delivery and ordering of the initial pairing commitment
Contract3 describes a forwarding relay without retained messages;4 sends pair_offer before phone joins. The late-joining phone would miss the commitment. Sending its key share first and accepting a newly supplied commitment afterward would not enforce commit-before-share ordering. Smallest proposed fix: rendezvous relay retains the first immutable laptop pair_offer for room lifetime and delivers it to the joining phone before accepting/forwarding pair_join; phone waits for that offer before generating/sending its join share. Define offer immutability, role enforcement and clear cache on5min expiry/cancel. Alternative readiness frame requires a versioned contract addition. Tests: late phone join receives original offer, no reveal/join ordering bypass, replacement offer rejected, expiry clears cached offer. No relay/mocks implement a guessed rule.

### CCR-2: partial invalid-event batches versus cumulative durable ACK
Section6 applies valid events even when another event in the same batch is rejected;7.2 says every transmitted seq<=ack was durably stored and also says schema mismatch drops the batch. A rejected event between two stored valid seqs makes an ACK of the highest good seq untrue under that definition. Proposed clarification: distinguish whole-batch incompatible schemaVersion from isolated invalid events; define a durable settled cursor including controlled rejection tombstones (device,seq,id,reason only), or explicitly choose batch-atomic rejection and revise section6. Tests: valid-invalid-valid, restart/backfill, invalid seq below highest ACK, no loss/duplicates. Need owner choice; do not silently reinterpret ACK or discard unpersisted input.

### CCR-3: browser videoState cannot currently enter canonical telemetry
Contract9 allows videoState playing|paused|none. core-spec/event.schema.json currently has videoState:false in tab_category. C0.0 authorizes only missing gap reasons, not this change. Proposed explicit authorization: update only this property to the stated enum, bump core event version according to canonical version policy, update fixtures/checks/SchemaVersion constant through the controlled schema process, or specify that videoState is intentionally transient inference-only and not emitted. No silent dropping or unapproved core edit. Tests: all3 values accepted, unknown rejected, cross-schema version sync. Gap reasons need no delta.

### CCR-4: incomplete body vocabularies and limits
state.declared has no type/shape: propose null or the exact canonical active declaredSession object (task,plannedMin,strict,startedTs). held_list.status is unspecified: propose the reviewed7-value ActuationStatus vocabulary. Pair-cancel reason,cmd_ack reason,error detail should be controlled code tokens/enums, never echoed input; choose allowed tokens/limits. Clarify whether32768bytes applies to the entire decrypted {mid,ts,b} or only inner b (propose entire plaintext). Tests: exact field/enum shapes, free-form content rejected, boundary byte sizes. These are data-contract choices, not implementation guesses.

### CCR-5: clock sign and ingest client blocking key
The ping formula yields peer-minus-requester offset; conversion of an incoming peer timestamp needs receiver-minus-peer. Propose explicit offsetToReceiver=-measuredPeerMinusSelf for that direction and sign-labelled vectors with positive/negative skew. Section9 token failure blocking says same remote port range without defining range: define a stable bounded client identity/range and reset/window semantics, or use an explicit documented loopback/global attempt bucket. No arbitrary port-range rule is invented.

## Dependency/scope observations
Root declares Ajv/ajv-formats but not ws; ws exists only in relay/laptop manifests. C0 scope allows ws but only two root script keys. Before mocks, choose scoped tools/mock-relay/package.json (allowed subtree) or approve adding root ws; do not edit unrelated package dependency keys. tools/gen-vectors.mjs is explicitly required by C0.2 despite not appearing in its abbreviated Scope list; that explicit requested path is treated as allowed. No other tools are added outside listed paths.

## Milestones
M0 preflight/CCRs recorded; C0.0 no delta needed. Continue only independent C0.2 crypto/reference vectors using approved exact byte formulas. C0.1 complete schemas and C0.3–C0.7 integration/attack/verify tools remain not_started or blocked by listed CCRs. No Gate3/platform/security-audit claim. Prototype crypto primitives/tests are not proof the pairing/session state machine is secure. No production pairing key, code or secret is printed or committed; requested vectors use fixed public test-only scalars/nonces labelled as non-secret fixtures.

### M1 independent reference crypto verification
Implemented reference byte primitives, deterministic public vectors, generator and focused tests only. Executed `node --test tools/test/contract-crypto.test.mjs`: tests 7, pass 7, fail 0, duration_ms 78.5069. Executed `node tools/gen-vectors.mjs`: sha256=63a52f85159d3c9c9f01c7617cd77715778bdedbf770ceaf834ff29295d9030f. Full C0 and target checks are not verified. No replay/state-machine/security-audit claim.

Observed pre-existing/unattributed working-tree changes in package.json (check:contracts, build-vectors and root ws dependency), package-lock.json, .env.example and generated dataset output. These are excluded from M1; no unauthorized root dependency change is included. Use direct node commands above for this milestone.
