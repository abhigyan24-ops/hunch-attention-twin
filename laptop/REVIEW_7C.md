# Stage 7C review — 2026-10-29

Reviewed c79fb38 against SPEC12.4/13/25.8/25.9, signed DD-12 and sync contract9. Scope: laptop only; no phone/device operations. Existing untracked t is excluded.

## Findings and resolutions

| Finding | Evidence before correction | Resolution and regression |
|---|---|---|
| P2: queued frames processed after failed authentication | A wrong auth followed by a correct auth in one corked TCP write triggered a connection callback even though close1008 had begun. | IngestServer rejects all message callbacks unless the socket is OPEN. `review: rejected auth cannot recover on queued frames from the same closing socket`. |
| P2: queued frames processed after Origin revocation | A queued heartbeat reached onMessage after setAllowedExtensionIds removed its Origin and began closing the socket. | Same OPEN-state guard; `review: allowlist revocation drops already queued messages immediately`. |
| P2: unfocused updates fabricate a focus return | false→false→resume produced a resumeWindow without any true focus update. | VsCodeFeed requires windowFocused=true for the return transition. `review: repeated unfocused states cannot fabricate a focus return`. |
| P2: privileged IPC trusted window identity without checking its current document | Source review: a navigated main frame would retain the allowed webContents identity and could invoke ingest-config. No live malicious navigation or token disclosure was attempted. | Exact renderer URL allowlist for IPC; deny external navigation/redirects and new windows. `review: token IPC trusts only exact renderer documents in dev and production`. |

The first three regression tests were executed before fixes: tests3/pass0/fail3. After fixes, all four review regressions passed: tests4/pass4/fail0/duration_ms937.8051. The URL policy rejects lookalike hosts, changed ports/paths, other files, credential-bearing URLs, unexpected query parameters and malformed URLs; the intended Pip and panel documents remain allowed. No frozen contract/schema changes.

## Executed verification

- Root `npm test`: tests117/pass117/fail0/duration_ms3367.6745.
- Root `npm run checks`: schema-sync PASS1.1.0 (1 Kotlin version file); twin-purity PASS96files; no-persona-imports PASS334productionfiles. These are source scans, not device tests.
- `npm run check:s7`: typecheck/build exit0; laptop tests57/pass57/fail0/duration_ms2180.1166; selected privacy tests3/pass3/fail0/duration_ms727.8706. This includes shared ingest attack suite against the real server and all existing laptop regressions.
- Exact automated output: VERIFICATION_7C_REVIEW.txt and VERIFICATION_7C_REVIEW_ROOT.txt.
- Local Electron `npm run dev`: Pip displayed and opened a separate panel; panel state and masked-token configuration loaded through the stricter IPC checks; Local ingest is ready on47821 /ingest; both feeds Not connected. File > Exit ended the dev process with exit0. Token was not revealed or copied. Electron emitted sync-safeStorage API deprecation warnings; encrypted-token recovery succeeded. No malicious external page was opened.

## Remaining verification and limitations

Real VS Code/browser extensions, live ActivityWatch, Windows firewall behavior, tray controls, all visual animation previews, underlying-app transparent click-through and fullscreen behavior remain not verified on target. Cloud relay and phone pairing/device behavior are outside7C. Baseline collector-health ambiguity and missing persisted focus snapshots are unchanged and still use honest unknown/no-data wording. SafeStorage sync calls are deprecated in installed Electron45; migration can retain the injectable encrypted-storage contract in a later compatibility task.

Ready for owner review of these changes before merge/push. No merge or push was performed. No phone, Gradle, adb or Reno14 operation ran.
