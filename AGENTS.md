# AGENTS.md: Dhyan

Master spec: `docs/SPEC.md` (sections 1–25). Section 25 overrides older wording on safety, measurement, privacy, and validation. No planned feature may be removed.

- Read relevant sections of `docs/SPEC.md` and `HANDOFF_STATUS.md` before editing.
- Make the smallest coherent change; add/update unit tests and run them.
- Claim builds, tests, and hardware checks only when executed in this session; otherwise say `not verified on target`.
- Record ambiguities in `DECISIONS_*.md`; do not guess.
- Edit `core-spec/` and root `package.json` only in Task 1A.
- Privacy: categories and counts only. Never persist or log raw notification text, titles, URLs, or keystrokes.
- Phone: Kotlin, Compose, Room, WorkManager, OkHttp, Koin. No `android.*` or `androidx.*` imports in `twin/`.
- Laptop: Electron, TypeScript, `ws`; no native C++ addons or `better-sqlite3`.
- Relay: Node 20, `ws`, in-memory.

Stage 1A must not modify `phone/`, `laptop/`, or `relay/`.

## Stage 4A rules (personas, generator, replay)
- Branch: stage4a-personas. Commit after each prompt. Never push. Never touch main.
- OWNED: phone/app/src/main/java/com/dhyan/app/personas/**, phone/app/src/test/java/com/dhyan/app/personas/**, tools/check-persona-data.mjs, tools/check-personas-purity.mjs, docs/PERSONA_WORLD.md, STAGE4A_NOTES.md, one script key check:personas in root package.json.
- FORBIDDEN: core-spec/ (read-only), twin/** (no imports), collectors/, ingest/, data/, ui/, sync/, safety/, actuator/, fusion/, inference/, Gradle files, AndroidManifest.xml, Koin/Hilt modules, shared governance documents.
- Pure Kotlin personas: no Android, AndroidX, Room or twin dependencies. No new dependencies.
- Determinism: all sampling uses personas/rng/GenRng; no ambient random or wall-clock input. Durations in minutes, hazard per hour.
- Synthetic events always synthetic:true; never raw notification text, titles or URLs. Never tune results to pitch numbers.
- The owner approved the 4A-0 assumptions and proceeding on 2026-10-25. Stage 3 is still uncommitted in the other checkout; start from Stage 1 HEAD and recheck the final Stage 3 contract before integration.
