# Rename Dhyan to Hunch: final pack

Contents: 1) brand kit, 2) timing and preconditions, 3) owner steps, 4) protected identifiers (do not change), 5) the rename prompt (Codex and Antigravity variants), 6) owner verification, 7) later optional deep rename.

Status: not run. I have not seen your repo, so every file and identifier below must be **discovered by the agent in M0**, not assumed. Domain availability of `usehunch.dev` is **unverified** (check section 3).

---

## 1. Brand kit (source of truth; commit as `docs/BRAND.md`)

| Item | Value |
|---|---|
| Product name | **Hunch** (capital H, never HUNCH) |
| Descriptor (always next to the name on first run, About, README, slides) | **your attention digital twin** |
| Tagline | **Knows what that notification will cost you.** |
| Disclaimer (README, About screens) | **Unrelated to other products named Hunch.** A working name for a prototype. |
| Mascot | **Pip** (unchanged) |
| Domain | `usehunch.dev` (to be registered by the owner; do not hard-code a URL until the owner confirms) |
| Repository | `hunch-attention-twin` (owner renames it on GitHub) |
| Social and store handle | `@gethunch` (owner checks availability per platform) |
| Short store line | "Predicts what a notification will cost your focus." |
| Codename | `dhyan` stays inside identifiers and protocol constants for compatibility |

README first lines:

```markdown
# Hunch: your attention digital twin

> Knows what that notification will cost you.

Unrelated to other products named Hunch. Hunch is a working name for a prototype.

Codename note: internal package names, protocol constants and storage names still say `dhyan` so existing data and pairings keep working.
```

Credits stay: ActivityWatch credit line and asset credits are unchanged.

---

## 2. When to run it

Run **after Gate 3** and after these are merged: `fix-sync-blockers`, 9P, Pip v2. Reason: a rename touches user-visible strings on every platform and will conflict with all of them.

Check first (PowerShell, in the main worktree):
```
git --no-pager branch --no-merged main
```
Every branch listed must be either merged, abandoned on purpose, or listed as an exception by you. Until then, update only the pitch, slides and README.

---

## 3. Owner steps (about 20 minutes, no agent needed)

1. **Domain:** `nslookup usehunch.dev` (non-existent domain suggests unregistered). Then check `lookup.icann.org` and a registrar's search. Buy it only after both look free. Do not point anything at it yet.
2. **GitHub repo:** Settings, Repository name, `hunch-attention-twin`. GitHub redirects the old URL. Then in each worktree: `git remote set-url origin <new url>` (check with `git remote -v`). Local folder names (`dhyan-s8p` and so on) can stay.
3. **Handles:** check `@gethunch` on GitHub, X, Instagram, LinkedIn and your store developer account; take what is free. Record what you got in `docs/BRAND.md`.
4. **Trademark:** before any public launch (not required for the demo), search IP India classes 9, 35 and 42 and WIPO. Not done here.
5. Commit `docs/BRAND.md` with the table above and the actual domain and handles you secured.

---

## 4. Protected identifiers (the agent must prove it did not change them)

| Area | Do not change | Why |
|---|---|---|
| Android | `applicationId` (`com.dhyan.app`), Kotlin packages, `rootProject.name` for now | New id is a different app and wipes your real-data log |
| Android storage | Room DB name, DataStore/preferences names, Keystore alias, FileProvider authorities, deep-link schemes, notification **channel ids** | Data and pairing key would be lost |
| Laptop | `package.json` `name` **and** `productName` (Electron derives `userData` from the app name, so changing either can move your settings, pairing key and JSONL history); electron `appId` | If a display name must change, set it in code (window titles, tray) and, if needed, pin the old path with `app.setPath('userData', <existing path>)` **before** the app is ready |
| Protocol | everything under `contracts/sync/` and `docs/contracts/`, HKDF info strings (`dhyan-session-v1`, `dhyan-pairing-key-v1`, `dhyan-room-v1`, `dhyan-hello-mac-v1`, `dhyan-pair-v1`, `dhyan-rendezvous-v1`, `dhyan-pair-confirm-v1`), test vectors, AAD prefix `dhyan|1|` | Interop and tests break; pairing breaks |
| Schemas | `core-spec/` identifiers and JSON keys | Frozen |
| Extensions | VS Code extension `name`, `publisher`, command ids and setting keys (`dhyan.*`); browser extension id (derived from the manifest key) | User settings and the laptop's allowed-origin list would break |
| Repo tooling | root `package.json` `name`, script names, `tools/**` names | Not user-visible |
| History | commit messages, branch names, notes files, spec file names | Historical |

Everything else visible to a person should say **Hunch**: app label, onboarding, About, settings text, notification channel **names**, foreground-service notification text, window and tray titles, panel and Pip bubbles (names only, never data), extension display names and command **titles**, browser extension name and action title, README, pitch, slides, demo script, Q&A sheet.

---

## 5. The rename prompt

Paste first: the coordination preamble (`DHYAN_STAGE789_PROMPTS.md`) and the Grounding Protocol (Part A of `DHYAN_ANTIGRAVITY_GROUNDED_PROMPTS.md`). Run the two variants in separate worktrees, at the same time.

### Common body (both agents)

```text
Task RENAME-HUNCH: change the user-visible product name from "Dhyan" to "Hunch" without breaking any identifier, protocol constant, stored data, pairing, or test. Source of truth: docs/BRAND.md (name, descriptor, tagline, disclaimer, Pip unchanged). The protected-identifier table in docs/prompts/HUNCH_RENAME_PACK.md section 4 is binding.

GROUNDING RULES FOR THIS TASK
 R1 No mass find-and-replace, ever. Every change is justified by a row in your M0 table.
 R2 You may not rename files, directories, packages, database or preference names, ids, keys, channel ids, constants or JSON properties. If you believe an identifier must change, stop and ask.
 R3 Do not invent a URL, handle, email or brand asset. Use only values present in docs/BRAND.md; if a value is blank there, leave the placeholder out and list it as a question for the owner.
 R4 Never claim a device or installation result. The owner verifies on the Reno 14 and Windows.
 R5 Show before/after for every user-visible string group (path:line, old, new).

MILESTONE M0 (inventory, then STOP for owner approval)
1. git status, git log --oneline -5, and confirm the branch and that all in-flight branches the owner named are merged.
2. Run: git grep -n -i "dhyan" and git grep -n -i "ध्यान" over YOUR SCOPE; also check resource files, manifests, HTML, JSON, markdown and test fixtures.
3. Produce a table of EVERY hit: file:line, the text, and ONE class: USER-VISIBLE (change), IDENTIFIER (keep), PROTOCOL CONSTANT (keep), STORED-DATA NAME (keep), TEST FIXTURE (keep unless asserting visible text), DOC (change only if user-facing), HISTORICAL (keep).
4. Extract, by command and not by memory, the exact current values of the protected identifiers in your scope (applicationId, Room database name, Keystore alias, DataStore names, channel ids, package.json name and productName, Electron appId, extension ids, HKDF strings) and write them to docs/rename/protected-identifiers.<platform>.json. These are the baseline the guard compares against.
5. For the laptop: report the current Electron userData path (verify by reading code and, if you can run it, by logging the path), and state how the display name change avoids moving it.
6. State how user-visible strings will be centralised (phone: strings.xml and one Kotlin object for non-resource text; laptop: one brand constants module used by tray, windows, panel, bubbles, About; VS Code: displayName and command titles; browser: manifest name/description/action title).
STOP after M0. Paste the table summary counts per class and every USER-VISIBLE hit.

M1 apply (after approval): change only USER-VISIBLE hits and centralise them. Add the descriptor "your attention digital twin" on first-run/onboarding, About, and README-level text; add the disclaimer to About screens and README. Do not touch anything classed IDENTIFIER, PROTOCOL CONSTANT or STORED-DATA NAME.

M2 safeguards: add tools/rename-guard.mjs (or a Gradle test for phone, plus the node script) that (a) re-extracts every protected identifier and fails if any differs from docs/rename/protected-identifiers.<platform>.json; (b) scans user-visible resource files (strings.xml, manifest labels, panel HTML/TSX text, package displayName fields, extension manifest names) and fails on the word "Dhyan" unless the line is on an explicit allowlist with a reason; (c) asserts the BRAND values appear where required (About text contains descriptor and disclaimer). Add a root script key check:rename. Make it fail first (revert one change to prove the guard works), then pass.

M3 verification (real output only): run the full unit suites for your platform, npm run checks, npm run check:contracts (test vectors must pass UNCHANGED), the Room migration tests (phone), the laptop checks, and check:rename. Paste exit codes and summary lines. Report git diff --stat and confirm no protected file changed (git diff --name-only against the protected list). List what is NOT verified (device install over existing data, pairing survival, Electron userData, real UI).
Stop points: after M0 and at the end.
```

### Variant A: Codex scope
```text
Scope (only these): laptop/** (user-visible strings, tray, window titles, panel, Pip bubbles and About, brand constants module), relay/** (log banner and health text only, no protocol strings), README.md, docs/BRAND.md (read-only unless values are missing), tools/rename-guard.mjs and docs/rename/protected-identifiers.laptop.json. Not allowed: phone/, vscode-ext/, browser-ext/, core-spec/, docs/contracts/, contracts/sync/, tools/mock-*, governance docs.
Branch rename-hunch-laptop from main. Notes NOTES_rename-hunch-laptop.md.
Extra laptop rules: do NOT change package.json "name" or "productName" or electron "appId". If the product name shown in the OS (window title, tray tooltip, taskbar) needs to say Hunch, set it in code and pin userData to the existing path with app.setPath('userData', <existing path>) before app.whenReady(), with a test that fails if the userData path changes. README.md must start with exactly the five-line block in docs/BRAND.md section 1 (first line "# Hunch: your attention digital twin"). Pip keeps its name.
```

### Variant B: Antigravity scope
```text
Scope (only these): phone/** (strings.xml app_name and all visible strings, onboarding, About, Settings text, foreground notification text, notification channel NAMES, Compose text), vscode-ext/** (displayName, command titles, status bar text, README), browser-ext/** (manifest name, description, action title, options page text), the pitch document and slides text, docs/DEMO_SCRIPT.md and docs/QA_CHEATSHEET.md if present, tools/rename-guard.mjs additions and docs/rename/protected-identifiers.phone-ext.json. Not allowed: laptop/, relay/, twin/**, actuator/**, safety/** except visible text constants if the M0 table proves they are user-visible (ask first), core-spec/, docs/contracts/, contracts/sync/, tools/mock-*.
Branch rename-hunch-phone from main. Notes NOTES_rename-hunch-phone.md.
Extra rules: applicationId, Kotlin packages, Room DB name, DataStore names, Keystore alias, channel IDs, VS Code extension name/publisher/command ids/setting keys, and the browser extension manifest key and id stay unchanged. The launcher label and app_name become "Hunch". Add the descriptor and the disclaimer to the About/Settings screen. Pitch and slides: keep every claim and number as is (this task changes names only); mark "Dhyan" origin only in a single "Codename" footnote. Do not generate a new logo or icon; if the owner supplies assets later, that is a separate task.
```

---

## 6. Owner verification after the rename (Reno 14 and Windows)

1. **Before installing**, export the phone events (debug export) and note the count; note the laptop JSONL size and the paired state.
2. Install **over** the existing app: `adb install -r app-debug.apk`. Do not uninstall.
3. Phone: launcher shows **Hunch**; open the app: events count unchanged, personas and settings intact, listener still connected, **still paired** (no re-pair prompt), About shows name, descriptor and disclaimer.
4. Laptop: start Hunch; the same `userData` folder is used (settings, JSONL history and pairing key present); tray and window titles say Hunch; pairing still works without re-pairing.
5. Extensions: still connect to the laptop with the existing token; display names say Hunch; no setting resets.
6. Run a quick sync smoke: reconnect and confirm `node tools/verify-sync.mjs` on fresh exports passes.
7. Run `npm run check:rename` and `npm run check:contracts` at the repo root and confirm the vectors are unchanged.
8. Record the result in `STAGE_GATES.md` as "Rename smoke (owner-run)" with the commit hash.

If anything fails: do not "fix forward" by renaming more things. Revert the branch and use the Resume prompt naming the failed step.

---

## 7. Later, optional: deep rename (after the demo)

Not before. Needs its own plan because it touches data:
- A one-time export/import path so a new `applicationId` can adopt the old Room data and re-pair; or keep `com.dhyan.app` permanently (nothing forces you to change it).
- Electron: migrate `userData` to the new folder with a copy-then-verify step.
- New extension ids need new settings migration and a new allowed-origin entry on the laptop.
- Protocol constants change only with a new `PROTOCOL_VERSION` and regenerated test vectors.
My recommendation: keep `dhyan` as a permanent internal codename and never do the deep rename unless you ship the product.
