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
