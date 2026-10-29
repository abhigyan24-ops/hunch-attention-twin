# Readiness decisions and unresolved items

- Preserve the signed contract 1.1.0 and every public frozen decision/twin contract; implementations must conform to them.
- Recheck CX-6 findings against base 079933e: live EpisodeRuntime, the shared work-category constant and edit_burst task preservation now exist.
- The owner authorized remaining work; fixes are isolated here instead of editing Antigravity's worktree or main. Integration remains subject to owner review, no push.
- Historical rows with missing event identity must not be silently assigned invented canonical telemetry. Keep them locally; only validated, durably identified events may sync. An explicit reviewed historical mapping remains pending.
- Legacy `productivity`, `entertainment`, `comms`, `browser` and surface aliases are not all canonical. Ambiguous values must not become guessed focus/task categories. Preserve local evidence and use canonical unknown where the contract permits it, or mark the event unsyncable with a controlled reason. A full collector-taxonomy migration needs separate recorded mapping decisions.
- Real Gate 3, current cloud relay/TLS, physical-device Keystore/foreground lifetime and migration behavior remain owner target checks. An automated smoke test does not satisfy Gate 3.
- RESOLVED: Episode closer records non-pickup failure on unpicked superseded episodes per Spec 7.1. Stage4B parameter recovery test recovers Rahul's parameter at 0.628407 (error 0.0084, within ±0.08 limit of 0.62 ground truth). All 327 Android unit tests pass (100% green).
- Remote declaration precedence preserved: fresh remote laptop declaration overrides phone session; falls back to phone session when remote is null or stale.
- Strict UUID pattern enforced for inbound event identity in SyncDataEngine: malformed UUIDs fail immediately with invalid_identity.
