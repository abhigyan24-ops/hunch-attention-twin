# Contract branch lead review — 2026-10-29

Reviewed cb2346a and the signed1.1.0 contract. Fixed within tools/mock-*/tests only; no frozen schema, vectors, phone, production relay, main or hardware changes.

| Finding | Resolution and regression |
|---|---|
| Persisted cursor could claim sequences not represented in recovered records | Fail-closed recovery checks cursor/record identities/type/allowed metadata and canonical event validity; corrupt cursor/negative cursor/forbidden tombstone regression. |
| Valid-shaped commands bypassed auth/expiry and durable idempotency | Enforce show_now direction, phone-only kill-switch disable, issued/expiry/max60sec; require durable pre-effect reservation; repeat/restart/storage-failure tests prove no unauthorized or duplicate injected effect. No command args persisted. |
| Tag-failure close rule absent | Third tag failure within60seconds closes1008; test uses injected peer clock. |
| Pair cancel could leave promises pending; malformed data could escape callback | Controlled rejection/timeout handling, timer cleared on completion; bad-SAS rejects both peers and yields no pairing key. |
| Rendezvous join-attempt rule absent |10/minute/socketIP implemented in mock only, fake-clock reset test. |

Fresh final checks before commit: npm test117/pass117/fail0/duration_ms3080.3147; npm run check:contracts59/pass59/fail0/duration_ms1075.0463; rootchecks schema-syncPASS1.1.0/twin96/no-persona284. Full stdout in verification/REVIEW-root-tests.txt and REVIEW-contract-tests.txt. Vector SHA256 canonicalLF remains3a3e92d7952ee3ed4a95787771c7732daf3699c86cd5f09b37e8a820142202a9. A final timer cleanup does not alter any schema/crypto formula and is rechecked with the same suites in the notes.

Verdict: scoped schema/mock branch is ready for owner review after these fixes, **not** a production security/deployment approval. See DECISIONS_REVIEW.md. Remaining lifecycle/capacity/migration work belongs7C/8L/8P1; protocol ambiguities already recorded are not silently resolved by mocks. The owner must review contracts before the stacked laptop branch. No merge/push performed. No Gradle/device work was started while Antigravity tested Reno14.
