import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

export interface EndpointInfo {
  v: number;
  port: number;
  token: string;
  pid?: number;
  startedAt?: number;
}

export type EndpointChangeCallback = (endpoint: EndpointInfo | null) => void;

export class EndpointDiscovery {
  private endpointFilePath: string;
  private watcher: fs.FSWatcher | null = null;
  private changeCallback: EndpointChangeCallback | null = null;
  private pollTimer: ReturnType<typeof setInterval> | null = null;

  constructor(customPath?: string) {
    this.endpointFilePath = customPath || path.join(os.homedir(), '.dhyan', 'endpoint.json');
  }

  getEndpointPath(): string {
    return this.endpointFilePath;
  }

  /**
   * Reads and parses ~/.dhyan/endpoint.json.
   * Silently returns null if the file does not exist, is empty, or is corrupt.
   */
  readEndpoint(): EndpointInfo | null {
    try {
      if (!fs.existsSync(this.endpointFilePath)) {
        return null;
      }

      const content = fs.readFileSync(this.endpointFilePath, 'utf8');
      if (!content.trim()) return null;

      const data = JSON.parse(content);
      if (typeof data.port === 'number' && typeof data.token === 'string') {
        return {
          v: data.v ?? 1,
          port: data.port,
          token: data.token,
          pid: data.pid,
          startedAt: data.startedAt
        };
      }
      return null;
    } catch {
      return null;
    }
  }

  /**
   * Starts watching for endpoint changes.
   */
  startWatching(callback: EndpointChangeCallback): void {
    this.changeCallback = callback;
    const dir = path.dirname(this.endpointFilePath);

    try {
      if (fs.existsSync(dir)) {
        this.watcher = fs.watch(dir, (_event, filename) => {
          if (!filename || filename === path.basename(this.endpointFilePath)) {
            const endpoint = this.readEndpoint();
            this.changeCallback?.(endpoint);
          }
        });
      }
    } catch {
      // Fallback to polling if fs.watch is unsupported on target path
    }

    // Safety poll check every 5 seconds
    this.pollTimer = setInterval(() => {
      const endpoint = this.readEndpoint();
      this.changeCallback?.(endpoint);
    }, 5000);
  }

  /**
   * Registers a VS Code URI handler for `vscode://<publisher>.<name>/connect?port=&token=`
   */
  registerUriHandler(vscodeApi: any, secretsStorage?: any): { dispose(): void } {
    if (!vscodeApi?.window?.registerUriHandler) {
      return { dispose: () => {} };
    }

    return vscodeApi.window.registerUriHandler({
      handleUri: (uri: any) => {
        try {
          if (uri.path === '/connect' && uri.query) {
            const params = new URLSearchParams(uri.query);
            const port = parseInt(params.get('port') || '', 10);
            const token = params.get('token') || '';

            if (port > 0 && token) {
              if (secretsStorage?.store) {
                secretsStorage.store('dhyan.token', token).catch(() => {});
              }
              const info: EndpointInfo = { v: 1, port, token };
              this.changeCallback?.(info);
            }
          }
        } catch {
          // Ignore invalid uri query
        }
      }
    });
  }

  dispose(): void {
    if (this.watcher) {
      this.watcher.close();
      this.watcher = null;
    }
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
    this.changeCallback = null;
  }
}
