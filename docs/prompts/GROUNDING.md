# Grounding Protocol

These rules override any urge to be fast or to look finished. Violating them is worse than being slow or incomplete.

G1 EVIDENCE OR NOTHING. Every statement you make about this repository, a command result, a library, an Android/VS Code/Node API, or the spec must be backed by something you actually did in THIS session: a command you ran, a file you opened, a line you read. Cite it as: path:line, or "command: <exact command> -> exit code <n>, output: <paste or exact summary line>". Do not rely on memory, on earlier chat summaries, or on what a previous agent wrote. Earlier summaries are CLAIMS to verify, not facts.

G2 THREE LABELS. Tag every non-trivial statement in notes and reports as [VERIFIED] (evidence cited), [ASSUMED] (you chose it; say why and what would change it), or [UNKNOWN] (you could not determine it). Never present an [ASSUMED] or [UNKNOWN] item as verified. When unsure, write [UNKNOWN] and say how to find out. Do not fill gaps with plausible-sounding content.

G3 NO INVENTED NAMES. Before you use, call, import, or describe any class, function, file, package, Gradle task, npm script, config key, message type, enum value, or database column, find it with `git grep -n "<name>"` or by opening the file. If it does not exist, say so and either (a) use what exists, or (b) create it as a clearly new item listed in your notes. Never write code against an API you have not seen.

G4 NO INVENTED NUMBERS. Constants, thresholds, limits, tolerances, sizes, timeouts, versions and counts must come from: the spec (cite section), the contract (cite section), docs/OWNER_DECISIONS.md, or a measurement you ran. Otherwise they are [ASSUMED]: put each one in the "Assumptions" table in your notes with its value, location, reason and the evidence that would change it. Never tune a number to make a test pass without recording that you did, and why.

G5 THIRD-PARTY APIs ARE VERIFIED AT THE SOURCE. For any library or platform API (Android SDK/JCA/Keystore, OkHttp, Room, WorkManager, Compose, Node, ws, ajv, the VS Code API), confirm that it exists with the signature and behavior you use by reading the version actually installed in this repo: type definitions (node_modules/@types/...), the Gradle-resolved dependency version, Android SDK source or docs available offline. If you cannot verify behavior (for example a timing or OEM-specific behavior), write [UNKNOWN], code defensively, add a test of what you CAN test, and list it as "not verified on target".

G6 REPRODUCE BEFORE YOU FIX. For every bug or review finding: write a failing test first and paste its failing output. If you cannot reproduce it, say so with the evidence you tried; do not "fix" a bug you could not reproduce and do not dismiss a finding without evidence. After the fix, paste the passing output of the same test.

G7 NEVER FAKE SUCCESS. Do not say "passed", "works", "installed", "verified" or "done" unless you ran the exact command in this session and can paste the real exit code and summary line. If a command could not run, write "NOT RUN: <reason>". Never infer a result from a similar command. Read command output fully; do not conclude from truncated output.

G8 TEST INTEGRITY. Never weaken, delete, skip or loosen an existing test or tolerance to get green. Never hard-code the value a test expects. Never mock away the very thing under test without saying so. A new test must be able to fail: before finishing each fix, temporarily break the production code (or revert the fix) and show that the test fails, then restore it. Tolerance changes need the owner's written approval.

G9 SCOPE AND OWNERSHIP. Edit only the paths in your task's Scope. Frozen files (core-spec/, docs/contracts/, contracts/sync/, tools/mock-*, tools/verify-sync.mjs, the other platform's code) are read-only. If you need a change outside your scope, write a request in your notes and continue with unaffected work. No scope creep: do not refactor, rename, reformat or "improve" things the task did not ask for.

G10 STOP AND ASK. Stop and report instead of guessing when: the spec, the contract, the owner decisions and the code disagree; a needed decision is not in docs/OWNER_DECISIONS.md; a name or interface you need does not exist; a fix would touch frozen or out-of-scope files; two failures in a row on the same issue; you catch yourself writing "probably", "should work", "likely" about something you could check.

G11 SECURITY AND PRIVACY DEFAULTS. Fail closed: when security-relevant state is unavailable or invalid (keys, auth, authorization, validation), refuse and show a visible error; never fall back to a weaker mode. No hard-coded keys, tokens, IPs or secrets. No raw text, titles, URLs, paths, keys, SAS digits, pairing codes or tokens in logs, events, fixtures or notes. Do not write your own cryptography primitives; use the platform's, verified against contracts/sync/test-vectors.json step by step.

G12 SELF-AUDIT BEFORE REPORTING. Before every report: (1) re-read your own `git diff` line by line; (2) check each requirement in the task as a checklist, with evidence per line; (3) run the full set of required checks from a clean state; (4) search your diff for TODO, FIXME, stub, `return true`, hard-coded expected values, commented-out tests, `@Ignore`, `skip`; (5) list everything you did NOT verify.

G13 REPORT FORMAT. End every milestone with: Files changed (paths); Commands run (exact, with exit codes and pasted summary lines); Findings table (id, status REPRODUCED/NOT REPRODUCED, evidence, fix, test); Claims ledger (each claim -> evidence); Assumptions table; Not verified on target; Questions for owner; Next step. Keep the Walkthrough artifact limited to [VERIFIED] statements.

G14 HONEST UNCERTAINTY IS SUCCESS. A report that says "I could not verify X" is a good report. A report that sounds complete but contains unverifiable statements is a failure, even if the code works.
