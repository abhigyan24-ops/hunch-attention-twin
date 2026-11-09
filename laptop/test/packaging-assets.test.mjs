import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { build } from 'esbuild';

const laptopDir = fileURLToPath(new URL('../', import.meta.url));
const repoRoot = path.resolve(laptopDir, '..');

test('all sync contract schemas are statically bundled without runtime filesystem scans', async () => {
  const schemasDir = path.join(repoRoot, 'contracts', 'sync', 'schemas');
  const schemaFiles = (await fs.readdir(schemasDir)).filter((f) => f.endsWith('.schema.json')).sort();
  assert.ok(schemaFiles.length > 0);

  const validateSource = await fs.readFile(path.join(laptopDir, 'src', 'sync', 'validate.ts'), 'utf8');
  assert.equal(validateSource.includes('readdirSync'), false);
  for (const file of schemaFiles) {
    assert.ok(
      validateSource.includes(`contracts/sync/schemas/${file}`),
      `src/sync/validate.ts must statically import ${file}`
    );
  }
});

test('packaged main bundle loads cleanly outside repo without core-spec or contracts directories', async (t) => {
  const tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), 'dhyan-pkg-startup-'));
  t.after(() => fs.rm(tmpDir, { recursive: true, force: true }));

  const outMain = path.join(tmpDir, 'out', 'main', 'index.mjs');
  const stubPath = path.join(tmpDir, 'mock-electron.mjs');
  await fs.writeFile(
    stubPath,
    `
const noop = () => {};
export const app = {
  isPackaged: true,
  getVersion: () => '1.0.0',
  getAppPath: () => ${JSON.stringify(tmpDir)},
  getPath: () => ${JSON.stringify(path.join(tmpDir, 'userdata'))},
  setPath: noop,
  requestSingleInstanceLock: () => false,
  quit: noop,
  on: noop,
  whenReady: () => new Promise(() => {}),
  commandLine: { appendSwitch: noop }
};
export class Notification { static isSupported() { return false; } show() {} }
export const screen = { on: noop, getCursorScreenPoint: () => ({ x: 0, y: 0 }), getDisplayNearestPoint: () => ({ workArea: { x: 0, y: 0, width: 1920, height: 1080 } }), getAllDisplays: () => [] };
export const ipcMain = { on: noop, handle: noop };
export const powerMonitor = { getSystemIdleTime: () => 0 };
export const safeStorage = { isEncryptionAvailable: () => false };
export const dialog = { showErrorBox: noop };
export const globalShortcut = { register: () => true, unregisterAll: noop };
export function BrowserWindow() {}
export function Tray() {}
export const nativeImage = { createFromBitmap: () => ({}) };
export const Menu = { buildFromTemplate: () => ({}) };
export const clipboard = { writeText: noop };
export default {
  app, Notification, screen, ipcMain, powerMonitor, safeStorage, dialog, globalShortcut, BrowserWindow, Tray, nativeImage, Menu, clipboard,
  autoUpdater: { logger: null, autoDownload: true, autoInstallOnAppQuit: true, allowDowngrade: false, on: noop, checkForUpdates: async () => {} },
  info: noop, warn: noop, error: noop
};
`
  );

  const externalPackagesPlugin = {
    name: 'resolve-laptop-deps-and-stubs',
    setup(b) {
      b.onResolve({ filter: /^(electron|electron-updater|electron-log)$/ }, () => ({
        path: pathToFileURL(stubPath).href,
        external: true,
      }));
      b.onResolve({ filter: /^(ajv(?:\/.*)?|ajv-formats|ws|zod|@google\/genai)$/ }, (args) => ({
        path: import.meta.resolve(args.path),
        external: true,
      }));
    },
  };

  await build({
    entryPoints: [path.join(laptopDir, 'src', 'main', 'index.ts')],
    bundle: true,
    format: 'esm',
    platform: 'node',
    outfile: outMain,
    plugins: [externalPackagesPlugin],
  });

  const runnerPath = path.join(tmpDir, 'verify-load.mjs');
  await fs.writeFile(
    runnerPath,
    `import { SCHEMA_VERSION } from ${JSON.stringify(pathToFileURL(outMain).href)}; console.log(SCHEMA_VERSION);`
  );

  const expectedVersion = (await fs.readFile(path.join(repoRoot, 'core-spec', 'SCHEMA_VERSION'), 'utf8')).trim();
  const res = spawnSync(process.execPath, [runnerPath], { cwd: tmpDir, encoding: 'utf8' });
  assert.equal(res.status, 0, `bundle failed to load outside repo:\n${res.stderr}`);
  assert.equal(res.stdout.trim(), expectedVersion);
});
