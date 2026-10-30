import { BrowserWindow, screen, ipcMain } from 'electron'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import type { PetDirective } from '../../shared/types'

const overlays = new Map<number, BrowserWindow>()
let ipcInitialized = false

function resolvePreloadPath(): string {
  try {
    return fileURLToPath(new URL('../preload/overlay.cjs', import.meta.url))
  } catch {
    return path.join(__dirname, '../preload/overlay.cjs')
  }
}

function resolveOverlayHtmlPath(): string {
  try {
    return fileURLToPath(new URL('../renderer/overlay/index.html', import.meta.url))
  } catch {
    return path.join(__dirname, '../renderer/overlay/index.html')
  }
}

export function createOverlays(): Map<number, BrowserWindow> {
  initIpc()
  screen.getAllDisplays().forEach(createOverlayFor)

  screen.on('display-metrics-changed', (_e, d) => {
    overlays.get(d.id)?.setBounds(d.workArea)
  })

  screen.on('display-removed', (_e, d) => {
    const win = overlays.get(d.id)
    if (win) {
      if (!win.isDestroyed()) win.destroy()
      overlays.delete(d.id)
    }
  })

  screen.on('display-added', (_e, d) => {
    if (!overlays.has(d.id)) {
      createOverlayFor(d)
    }
  })

  return overlays
}

export function getOverlayWindows(): BrowserWindow[] {
  return Array.from(overlays.values()).filter((w) => !w.isDestroyed())
}

export function broadcastDirective(directive: PetDirective) {
  for (const win of overlays.values()) {
    if (!win.isDestroyed() && win.webContents) {
      win.webContents.send('pet:directive', directive)
    }
  }
}

function createOverlayFor(display: Electron.Display): BrowserWindow {
  const existing = overlays.get(display.id)
  if (existing && !existing.isDestroyed()) return existing

  const win = new BrowserWindow({
    x: display.workArea.x,
    y: display.workArea.y,
    width: display.workArea.width,
    height: display.workArea.height,
    transparent: true,
    frame: false,
    hasShadow: false,
    resizable: false,
    movable: false,
    skipTaskbar: true,
    focusable: false,
    alwaysOnTop: true,
    fullscreenable: false,
    show: false,
    webPreferences: {
      preload: resolvePreloadPath(),
      contextIsolation: true,
      sandbox: true,
      backgroundThrottling: false
    }
  })

  win.setAlwaysOnTop(true, 'screen-saver')
  win.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true })
  win.setIgnoreMouseEvents(true, { forward: true })

  win.once('ready-to-show', () => {
    win.showInactive()
  })

  win.on('closed', () => {
    overlays.delete(display.id)
  })

  if (process.env.ELECTRON_RENDERER_URL) {
    const url = new URL(process.env.ELECTRON_RENDERER_URL)
    url.pathname = '/overlay/index.html'
    void win.loadURL(url.href)
  } else {
    void win.loadFile(resolveOverlayHtmlPath())
  }

  overlays.set(display.id, win)
  return win
}

function initIpc() {
  if (ipcInitialized) return
  ipcInitialized = true

  ipcMain.on('pet:ignore-mouse', (e, ignore: unknown) => {
    if (typeof ignore !== 'boolean') return
    const win = BrowserWindow.fromWebContents(e.sender)
    if (win && !win.isDestroyed()) {
      win.setIgnoreMouseEvents(ignore, { forward: true })
    }
  })
}
