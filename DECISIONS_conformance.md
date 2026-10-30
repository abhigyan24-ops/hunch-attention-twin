# Conformance follow-up decisions

Base: d06addb. Owner requested immediate work after the delayed follow-up; the timer is paused. Work occurs on codex/readiness-conformance in its own worktree, preserving Antigravity's committed branch and avoiding shared edits.

- Section25.4 explicitly overrides the older7.1 instruction to count superseded notifications as ignores. No reviewed observation rule establishes a negative outcome here, so SUPERSEDED remains unresolved and does not update ordinary Beta failures. Never restore incorrect updates simply to make the recovery test pass.
- Preserve the Stage4B recovery test, fixed seed and0.08 tolerance unchanged. If it fails under correct attribution, report that model-validation failure rather than weakening the test or changing the estimand without review. The model design request is documented separately.
- Require UUID id, exact device/schemaVersion and a bound installation identity before partial settlement. Identifiable invalid payloads receive metadata-only tombstones; a legacy kind field never bypasses schema/privacy checks.
- Historical rows lacking a validated durable canonical envelope remain locally stored. Export must not fabricate identities, payloads or sources. A historical mapping is still an open reviewed task, not a compatibility fallback.
- The phone and laptop installation identifiers must differ. Sequence0 is valid; an empty durable ledger is represented internally by-1, while the externally specified empty ACK cursor stays0.
- Android OS Build.TYPE identifies the operating-system image, not the application's debug/release variant. Relay policy uses generated application BuildConfig.DEBUG. Debug cleartext is restricted to explicit loopback/emulator endpoints; release requires WSS. Cloud and physical-device behavior remain not verified on target.
