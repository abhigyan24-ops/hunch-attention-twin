# Release recovery

[VERIFIED] Candidate started at 4e2b8eff7e43a53ffd7f52c9923f312c9ad0fbcf. Regression command `node --test tools/test-release-recovery.mjs` reproduced two failures before changes (pass 0, fail 2).

[ASSUMED] The supplied reference workflow is the requested release policy. Missing Android signing secrets produce a distinctly named debug-signed APK for testing. Debug keys on ephemeral runners do not provide upgrade continuity. Release distribution requires a stable owner-managed key.

[UNKNOWN] No project license was found. Packaging uses --skip-license pending an owner license decision, and does not assign MIT.

[UNKNOWN] GitHub run SHA, logs, repository visibility, signing secrets and remote tag state remain unavailable. Local changes are based on reproduced checks and inspected configuration. GitHub CI and target installers are not verified on target.

[VERIFIED] Existing Python 3.10 and Java 17 workflow versions are retained. pyproject.toml does not declare requires-python.

[VERIFIED] `npm test --prefix laptop` outside the sandbox exited 0 with tests 115, pass 115, fail 0. Extension integration exited 0 with 18 passing. Extension unit output reported 121 passing outside the sandbox. Sandboxed socket tests had failures.

[VERIFIED] Laptop typecheck reproduced TS5097 before adding allowImportingTsExtensions to its existing noEmit configuration, and passed afterward. Extension compile, lint, VSIX packaging, workflow YAML parse and recovery tests succeeded. Recovery tests report tests 2, pass 2, fail 0.

[VERIFIED] electron-builder 26.18.0 was resolved by npm and pinned in the manifest and lockfile. npm reported six moderate dependency vulnerabilities.

[VERIFIED] Real VS Code activation test failed because dhyan.diagnostics was absent. src/extension.ts only accepts an injected API and had no production runtime import. New src/entry.ts passes the real vscode API to the existing activation function.

[VERIFIED] Real VS Code 1.145.2 activation test now reports Exit code: 0. The rebuilt VSIX packages successfully. Laptop electron-vite build and both TypeScript checks exit 0. Remote main and v1.0.0 were read with git ls-remote and both resolve to the starting SHA above.

[UNKNOWN] Android build/signature verification, frozen engine packaging, NSIS installer generation and GitHub dry-run remain not verified on target. No CI run logs were available for this work.
