# Electron 45.0.0 installed API audit

[VERIFIED] Source: installed `laptop/node_modules/electron/electron.d.ts` (reused existing laptop dependencies). These are declarations, not Windows behavioral acceptance.

| Use | d.ts line | Declaration |
|---|---:|---|
| app metrics | 1321 | `getAppMetrics(): ProcessMetric[];` |
| window transparency | 4511 | `transparent?: boolean;` |
| window frame | 4340 | `frame?: boolean;` |
| window focusability option | 4336 | `focusable?: boolean;` |
| skip taskbar option | 4462 | `skipTaskbar?: boolean;` |
| always-on-top option | 4273 | `alwaysOnTop?: boolean;` |
| mouse passthrough | 3814 | `setIgnoreMouseEvents(ignore: boolean, options?: IgnoreMouseEventsOptions): void;` |
| hover forwarding | 23031 | `forward?: boolean;` in IgnoreMouseEventsOptions |
| focusability method | 3764 | `setFocusable(focusable: boolean): void;` |
| inactive show | 4068 | `showInactive(): void;` |
| always-on-top levels | 3609 | `setAlwaysOnTop(flag: boolean, level?: 'normal' \| 'floating' \| 'torn-off-menu' \| 'modal-panel' \| 'main-menu' \| 'status' \| 'pop-up-menu' \| 'screen-saver' \| 'dock', relativeLevel?: number): void;` |
| window bounds | 3705 | `setBounds(bounds: Partial<Rectangle>, animate?: boolean): void;` |
| all displays | 12866 | `getAllDisplays(): Display[];` |
| cursor position | 12874 | `getCursorScreenPoint(): Point;` |
| cursor display | 12882 | `getDisplayNearestPoint(point: Point): Display;` |
| primary display | 12886 | `getPrimaryDisplay(): Display;` |
| display added | 12803 | `on(event: 'display-added', listener: (event: Event, newDisplay: Display) => void): this;` |
| display removed | 12836 | `on(event: 'display-removed', listener: (event: Event, oldDisplay: Display) => void): this;` |
| display metrics | 12818 | `on(event: 'display-metrics-changed', listener: (event: Event, display: Display, changedMetrics: string[]) => void): this;` |
| shortcut registration | 9043 | `register(accelerator: string, callback: () => void): boolean;` |
| shortcut unregister | 9076 | `unregister(accelerator: string): void;` |
| menu popup | 10232 | `popup(options?: PopupOptions): void;` |
| power suspend | 11680 | `on(event: 'suspend', listener: () => void): this;` |
| power resume | 11623 | `on(event: 'resume', listener: () => void): this;` |
| screen lock | 11559 | `on(event: 'lock-screen', listener: () => void): this;` |
| screen unlock | 11723 | `on(event: 'unlock-screen', listener: () => void): this;` |
| background throttling | 20248 | `backgroundThrottling?: boolean;` |
| OS animation settings | 14975 | `getAnimationSettings(): AnimationSettings;` |
| OS reduced motion | 21586 | `prefersReducedMotion: boolean;` |
| renderer process ID | 19041 | `getOSProcessId(): number;` |
| load local file | 6321 | `loadFile(filePath: string, options?: LoadFileOptions): Promise<void>;` |
| ready lifecycle | 2105 | `whenReady(): Promise<void>;` |
| app quit | 1671 | `quit(): void;` |
| profile isolation | 1974 | `setPath(name: string, path: string): void;` |
| window destroy | 3187 | `destroy(): void;` |
| window alive guard | 3374 | `isDestroyed(): boolean;` |
| window new-window denial | 19489 | `setWindowOpenHandler(handler: (details: HandlerDetails) => WindowOpenHandlerResponse): void;` |
| window option inheritance | 7135 | `interface BrowserWindowConstructorOptions extends BaseWindowConstructorOptions {` |
| base options | 4250 | `interface BaseWindowConstructorOptions {` |
| BrowserWindow inheritance | 4647 | `class BrowserWindow extends BaseWindow {` |
| IPC context isolation | 20263 | `contextIsolation?: boolean;` |
| renderer sandbox | 20420 | `sandbox?: boolean;` |
| renderer Node disabled | 20361 | `nodeIntegration?: boolean;` |
| CPU measurement property | 7867 | `percentCPUUsage: number;` |
| main-process message listener | 9466 | `on(channel: string, listener: (event: IpcMainEvent, ...args: any[]) => void): this;` |
| IPC sender | 9517 | `sender: WebContents;` |
| IPC frame | 9523 | `readonly senderFrame: (WebFrameMain) \| (null);` |
| isolated preload bridge | 7690 | `exposeInMainWorld(apiKey: string, api: any): void;` |
| renderer listener | 9709 | `on(channel: string, listener: (event: IpcRendererEvent, ...args: any[]) => void): this;` |
| renderer send | 9762 | `send(channel: string, ...args: any[]): void;` |
| webContents send | 19374 | `send(channel: string, ...args: any[]): void;` |
| webContents identity | 19641 | `readonly id: number;` |
| webContents frame guard | 19676 | `readonly mainFrame: WebFrameMain;` |

[VERIFIED] Platform notes: `IgnoreMouseEventsOptions.forward` is darwin/win32 only (23025–23031); forward=true applies only with ignore=true. On Windows, elevated foreground windows can temporarily stop forwarding due to UIPI (3804–3812). No elevation/UIAccess change is proposed. `setFocusable` is darwin/win32 (3758–3764). `skipTaskbar` is darwin/win32 (4458–4462). Transparent Windows windows require frame=false (4506–4511). `showInactive` says it does not focus the window (4066–4068). Display metrics may include bounds/workArea/scaleFactor/rotation (12814–12820).

[VERIFIED] CPUUsage.percentCPUUsage is interval-based; first call returns zero (7864–7867). app.getAppMetrics shares/reset intervals across processes and callers (1315–1319). The isolated harness primes the counter; no production process names or metrics are read.

[UNKNOWN] Real mouse hit-testing, fullscreen/game layering, taskbar interactions, multi-display scaling, sleep/wake and remote desktop remain owner checks, even though declarations exist.
