# Stage 1C verification — 2026-10-25

Local Windows, Node v24.13.0, npm 11.6.2. The official Electron 38 binary download stalled and bounded direct-download attempts timed out; no partial archive was extracted. The package was then upgraded to patched Electron 45 with a Windows-only addon-free extractor override. Electron 45 uses explicit `install-electron`; its GUI binary is not installed. Runtime and interactive click-through: not verified on target. See ../DECISIONS_PENDING.md (consolidated Stage 1C notes) for failures, changes and recovery.

Final `npm.cmd install`, exit 0:

```text
added 1 package, changed 1 package, and audited 83 packages in 2s

10 packages are looking for funding
  run `npm fund` for details

found 0 vulnerabilities
```

An additional clean-install verification executed `npm ci && npm run typecheck && npm run build && npm test`, exit 0. Exact output:

```text
added 79 packages, and audited 81 packages in 31s

10 packages are looking for funding
  run `npm fund` for details

found 0 vulnerabilities

> dhyan-laptop@0.1.0 typecheck
> tsc --noEmit

> dhyan-laptop@0.1.0 build
> electron-vite build

vite v7.3.7 building ssr environment for production...
transforming...
✓ 1 modules transformed.
rendering chunks...
out/main/index.js  1.54 kB
✓ built in 262ms
vite v7.3.7 building ssr environment for production...
transforming...
✓ 1 modules transformed.
rendering chunks...
out/preload/index.cjs  0.39 kB
✓ built in 37ms
vite v7.3.7 building client environment for production...
transforming...
✓ 27 modules transformed.
rendering chunks...
../../out/renderer/index.html                   0.54 kB
../../out/renderer/assets/index-Bgb2EUS8.css    0.22 kB
../../out/renderer/assets/index-B14l9LBe.js   642.20 kB
✓ built in 1.07s

> dhyan-laptop@0.1.0 test
> node --test test/*.test.mjs

✔ Electron installer resolves the same addon-free extractor (2.483ms)
✔ installer adapter rejects relative paths (0.5708ms)
✔ Windows installer adapter extracts a real archive with literal spaced paths (2100.5182ms)
✔ Code.exe and code.exe map to coding (1.539ms)
✔ unknown processes map to unknown (0.1912ms)
✔ github.com maps to coding (0.1184ms)
✔ unknown domains map to unknown (0.1806ms)
✔ canonical SCHEMA_VERSION is 1.0.0 (1.1995ms)
ℹ tests 8
ℹ suites 0
ℹ pass 8
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 2262.0821
```

The final recursive installed-tree scan follows directory links (including the local installer adapter), exit 0:

```text
Native .node addons: 0
node-gyp directories: 0
```

`npm run check:aw`, exit 1, printed no bucket IDs:

```text
> dhyan-laptop@0.1.0 check:aw
> node scripts/check-aw.mjs
```

Required ActivityWatch buckets were not confirmed. No event endpoint was queried. Additional fixes during verification: initial extractor test could not resolve a nested-only dependency; direct local dependency and correctly relative Electron override fixed it. A clean-install scan exposed a stale broken nested symlink; the lockfile was regenerated and the clean install then passed, including a test that Electron resolves the same adapter. Sandbox PowerShell archive-module loading failed; approved local execution passed the real archive round-trip. No false GUI success is claimed.
