// laptop/src/main/updater.ts
import { app, Notification } from 'electron';
import { autoUpdater } from 'electron-updater';
import log from 'electron-log';

export interface UpdaterDeps {
  isDeepFocus(): boolean; // from the twin's current state
  stopServices(): Promise<void>; // stop engine process tree, DB close, etc.
  onState(s: { phase: 'idle' | 'downloading' | 'ready' | 'error'; version?: string }): void; // drives the tray menu
  setQuitting(flag: boolean): void; // lets window-close handlers allow quit
  beta: boolean;
}

autoUpdater.logger = log;
autoUpdater.autoDownload = true;
autoUpdater.autoInstallOnAppQuit = true; // silent install next time the app quits
autoUpdater.allowDowngrade = false;

let timer: NodeJS.Timeout | undefined;
let readyVersion: string | undefined;

export function initAutoUpdater(deps: UpdaterDeps): void {
  if (!app.isPackaged) return; // development: skip (use forceDevUpdateConfig for local tests)
  autoUpdater.channel = deps.beta ? 'beta' : 'latest';
  autoUpdater.allowPrerelease = deps.beta;

  autoUpdater.on('checking-for-update', () => log.info('[updater] checking'));
  autoUpdater.on('update-available', (i) => {
    log.info('[updater] available', i.version);
    deps.onState({ phase: 'downloading', version: i.version });
  });
  autoUpdater.on('update-not-available', () => deps.onState({ phase: 'idle' }));
  autoUpdater.on('error', (e) => {
    log.warn('[updater] error', e?.message);
    deps.onState({ phase: 'error' });
  }); // never a popup

  autoUpdater.on('update-downloaded', (info) => {
    readyVersion = info.version;
    deps.onState({ phase: 'ready', version: info.version });
    if (!deps.isDeepFocus() && Notification.isSupported()) {
      new Notification({
        title: 'Dhyan update ready',
        body: `Version ${info.version} will install when you next quit.`,
        silent: true,
      }).show();
    }
  });

  const check = () => autoUpdater.checkForUpdates().catch((e) => log.warn('[updater] check failed', e?.message));
  setTimeout(check, 60_000); // not during the startup rush
  timer = setInterval(check, 6 * 3_600_000 + Math.random() * 600_000);
}

/** Called from the tray menu item "Restart to update". */
export async function installNow(deps: UpdaterDeps): Promise<void> {
  if (!readyVersion) return;
  deps.setQuitting(true); // tray apps usually block quit; allow it for this path
  await deps.stopServices(); // IMPORTANT: otherwise the installer hits "file in use"
  autoUpdater.quitAndInstall(false, true); // not silent UI, relaunch after install
}

export function disposeUpdater(): void {
  if (timer) clearInterval(timer);
}
