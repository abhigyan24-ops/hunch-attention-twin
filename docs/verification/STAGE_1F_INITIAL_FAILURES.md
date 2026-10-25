# Initial Stage 1F root test failure

Command: npm test, repository root, exit 1. These failures were resolved by a source-guard fix and approved filesystem execution. Final verification is recorded in STAGE_GATES.md.

```text

> test
> node --test tools/test/*.test.mjs

✔ valid fixture: app_background.json (19.7369ms)
✔ valid fixture: app_foreground.json (19.0463ms)
✔ valid fixture: declared_session.json (2.0104ms)
✔ valid fixture: edit_burst.json (1.664ms)
✔ valid fixture: feedback.json (9.5767ms)
✔ valid fixture: gap.json (4.9627ms)
✔ valid fixture: idle_end.json (9.3639ms)
✔ valid fixture: idle_start.json (1.6434ms)
✔ valid fixture: notif_posted.json (1.4719ms)
✔ valid fixture: notif_removed.json (1.9469ms)
✔ valid fixture: pickup.json (2.9091ms)
✔ valid fixture: resume_first_edit.json (1.639ms)
✔ valid fixture: screen_off.json (1.5801ms)
✔ valid fixture: screen_on.json (1.642ms)
✔ valid fixture: scroll_burst.json (1.7753ms)
✔ valid fixture: state_snapshot.json (1.6833ms)
✔ valid fixture: surface_change.json (1.8563ms)
✔ valid fixture: tab_category.json (1.867ms)
✔ valid fixture: unlock.json (1.405ms)
✔ valid fixture: window_change.json (1.1832ms)
✔ invalid fixture: bad-schema-version.json (2.0106ms)
✔ invalid fixture: extra-payload-property.json (1.8002ms)
✔ invalid fixture: missing-field.json (2.937ms)
✔ invalid fixture: negative-duration.json (2.6616ms)
✔ invalid fixture: nested-forbidden-key.json (1.0789ms)
✔ invalid fixture: oversized-payload.json (1.786ms)
✔ invalid fixture: unknown-enum.json (1.7159ms)
✔ invalid fixture: urgency-out-of-range.json (2.1052ms)
✔ exactly 20 distinct event types and 8 negative fixtures (20.966ms)
✔ required envelope and payload fields cannot be omitted for any event type (47.3133ms)
✔ all forbidden keys detected recursively, including arrays; diagnostics contain no content (28.0047ms)
✔ 8 KB boundary measures UTF-8 bytes of the entire event (1.5722ms)
✔ negative durations, counts, timestamps, enum values, and extra fields rejected (33.8902ms)
✔ ingestedTs optional at emission; source-specific payload and UUID enforced (3.4212ms)
✔ ContextSnapshot exact shape, enums, active declaration, and closed nested properties (2.5129ms)
✔ exact taxonomy and starter maps (1.1877ms)
✔ all 20 Appendix A rows, symbols, units, ranges, and priors preserved (11.1193ms)
✔ relay strict frame variants and command-specific allowlists (178.0998ms)
✖ all three checks pass on repository and reject violations in isolated scratch trees (30882.8359ms)
✔ root hygiene and governance preserve stage boundaries and complete spec inventory (115.1678ms)
✔ validator CLI returns failure without leaking input values (1662.7512ms)
✔ hidden persona rahul validates (4.6881ms)
✔ hidden persona anika validates (1.8794ms)
✔ hidden persona kabir validates (1.4918ms)
✔ Rahul exactly preserves every 15.1 parameter (2.9874ms)
✔ assumed personas disclose assumptions and intended synthetic regimes (9.4322ms)
✔ persona invalid probabilities, sigmas, hours, multipliers, extras and omissions fail (20.5484ms)
✖ production trees do not reference hidden ground truth (32566.5614ms)
✖ dry run makes zero network calls without credentials and writes 20 covering rows (43.3222ms)
✔ dry-run and validator CLIs work from another cwd with credentials absent (857.7991ms)
✔ dedup normalizes case/whitespace and rejects conflicting labels or ID collisions (1.3997ms)
✔ dataset rejects non-synthetic rows, unknown labels/senders/categories, extra fields and missing coverage (0.3727ms)
✖ mocked Responses API uses fixed synthetic prompt, chosen model and no stored response (10.5369ms)
✔ live mode validates credentials/model and rejects invalid responses without writing (6.5098ms)
ℹ tests 54
ℹ suites 0
ℹ pass 50
ℹ fail 4
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 34030.1402

✖ failing tests:

test at tools\test\core-spec.test.mjs:113:1
✖ all three checks pass on repository and reject violations in isolated scratch trees (30882.8359ms)
  Error: EBUSY: resource busy or locked, read
      at Object.readFileSync (node:fs:439:20)
      at visit (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/check-no-persona-imports.mjs:14:45)
      at visit (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/check-no-persona-imports.mjs:11:32)
      at visit (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/check-no-persona-imports.mjs:11:32)
      at visit (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/check-no-persona-imports.mjs:11:32)
      at checkNoPersonaImports (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/check-no-persona-imports.mjs:18:3)
      at TestContext.<anonymous> (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/test/core-spec.test.mjs:114:106)
      at Test.runInAsyncScope (node:async_hooks:214:14)
      at Test.run (node:internal/test_runner/test:1106:25)
      at Test.processPendingSubtests (node:internal/test_runner/test:788:18) {
    errno: -4082,
    code: 'EBUSY',
    syscall: 'read'
  }

test at tools\test\personas-and-dataset.test.mjs:38:1
✖ production trees do not reference hidden ground truth (32566.5614ms)
  Error: EBUSY: resource busy or locked, read
      at Object.readFileSync (node:fs:439:20)
      at visit (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/check-no-persona-imports.mjs:14:45)
      at visit (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/check-no-persona-imports.mjs:11:32)
      at visit (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/check-no-persona-imports.mjs:11:32)
      at visit (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/check-no-persona-imports.mjs:11:32)
      at checkNoPersonaImports (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/check-no-persona-imports.mjs:18:3)
      at TestContext.<anonymous> (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/test/personas-and-dataset.test.mjs:38:79)
      at Test.runInAsyncScope (node:async_hooks:214:14)
      at Test.run (node:internal/test_runner/test:1106:25)
      at Test.processPendingSubtests (node:internal/test_runner/test:788:18) {
    errno: -4082,
    code: 'EBUSY',
    syscall: 'read'
  }

test at tools\test\personas-and-dataset.test.mjs:39:1
✖ dry run makes zero network calls without credentials and writes 20 covering rows (43.3222ms)
  Error: EPERM: operation not permitted, rename 'C:\Users\abhig\AppData\Local\Packages\sandbox.{c8ee7d4f-8987-4688-89ec-c77b765502ef}\AC\Temp\dhyan-1e-Ito9xL\notifications.jsonl.8176.tmp' -> 'C:\Users\abhig\AppData\Local\Packages\sandbox.{c8ee7d4f-8987-4688-89ec-c77b765502ef}\AC\Temp\dhyan-1e-Ito9xL\notifications.jsonl'
      at async Object.rename (node:internal/fs/promises:782:10)
      at async generateNotifications (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/dataset/generate_notifications.mjs:69:95)
      at async TestContext.<anonymous> (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/test/personas-and-dataset.test.mjs:41:16)
      at async Test.run (node:internal/test_runner/test:1113:7)
      at async Test.processPendingSubtests (node:internal/test_runner/test:788:7) {
    errno: -4048,
    code: 'EPERM',
    syscall: 'rename',
    path: 'C:\\Users\\abhig\\AppData\\Local\\Packages\\sandbox.{c8ee7d4f-8987-4688-89ec-c77b765502ef}\\AC\\Temp\\dhyan-1e-Ito9xL\\notifications.jsonl.8176.tmp',
    dest: 'C:\\Users\\abhig\\AppData\\Local\\Packages\\sandbox.{c8ee7d4f-8987-4688-89ec-c77b765502ef}\\AC\\Temp\\dhyan-1e-Ito9xL\\notifications.jsonl'
  }

test at tools\test\personas-and-dataset.test.mjs:71:1
✖ mocked Responses API uses fixed synthetic prompt, chosen model and no stored response (10.5369ms)
  Error: EPERM: operation not permitted, rename 'C:\Users\abhig\AppData\Local\Packages\sandbox.{c8ee7d4f-8987-4688-89ec-c77b765502ef}\AC\Temp\dhyan-1e-S9iMnE\notifications.jsonl.8176.tmp' -> 'C:\Users\abhig\AppData\Local\Packages\sandbox.{c8ee7d4f-8987-4688-89ec-c77b765502ef}\AC\Temp\dhyan-1e-S9iMnE\notifications.jsonl'
      at async Object.rename (node:internal/fs/promises:782:10)
      at async generateNotifications (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/dataset/generate_notifications.mjs:69:95)
      at async TestContext.<anonymous> (file:///C:/Users/abhig/OneDrive/Desktop/IIITN/tools/test/personas-and-dataset.test.mjs:73:16)
      at async Test.run (node:internal/test_runner/test:1113:7)
      at async Test.processPendingSubtests (node:internal/test_runner/test:788:7) {
    errno: -4048,
    code: 'EPERM',
    syscall: 'rename',
    path: 'C:\\Users\\abhig\\AppData\\Local\\Packages\\sandbox.{c8ee7d4f-8987-4688-89ec-c77b765502ef}\\AC\\Temp\\dhyan-1e-S9iMnE\\notifications.jsonl.8176.tmp',
    dest: 'C:\\Users\\abhig\\AppData\\Local\\Packages\\sandbox.{c8ee7d4f-8987-4688-89ec-c77b765502ef}\\AC\\Temp\\dhyan-1e-S9iMnE\\notifications.jsonl'
  }
```
