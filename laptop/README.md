# Dhyan laptop scaffold

From this directory run `npm install`, `npm run typecheck`, `npm run build`, and `npm test`. Run `npm exec install-electron` to install the GUI runtime, then `npm run dev` to open the scaffold. The runtime download was not completed in this session. The main process reads the unchanged sibling `core-spec/SCHEMA_VERSION`; keep that folder with the repository build. This is a repository scaffold, not a packaged installer.

The transparent Pip window has an isolated sandboxed preload and the spec's mouse-forwarding IPC/hitbox hooks. It displays “Dhyan Laptop Scaffold” and the schema version. Interactive Windows click-through and fullscreen behavior are not verified on target. Collectors, learning, JSONL persistence/recovery, sync and seven Pip animations remain planned; typed stubs explicitly indicate incomplete behavior.

The normalizer matches process/domain names using the canonical categories map, with case-insensitive process matching and unknown fallback. Raw adapter events expose no notification or window content. `npm run check:aw` requests only ActivityWatch info and bucket metadata, prints bucket IDs, and exits 1 if the required window/AFK buckets cannot be confirmed. It never requests events. [ActivityWatch](https://activitywatch.net/) is the planned collection backbone.

Rollup uses the WebAssembly package override to avoid native Node addons. No better-sqlite3 or node-gyp is used. Electron and esbuild are required standalone executables, not `.node` addons. See ../DECISIONS_PENDING.md (consolidated Stage 1C notes) and the handoff for actual checks and environment limits.

The patched Electron installer extractor is overridden with a Windows-only adapter backed by the built-in archive tool, avoiding native .node files. Its small archive round-trip is unit tested. See vendor/electron-extractor/index.cjs.
