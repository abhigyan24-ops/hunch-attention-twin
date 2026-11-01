import { WebSocketServer, WebSocket } from 'ws';
import type { IncomingMessage } from 'node:http';

export interface MockEngineOptions {
  port?: number;
  expectedToken?: string;
  initialAckedUpTo?: number;
  dropMidBatch?: boolean;
  rejectToken?: boolean;
  sendMalformed?: boolean;
  delayAckMs?: number;
}

export class MockEngineServer {
  public port: number = 0;
  public expectedToken: string = 'test-secret-token';
  public ackedUpTo: number = 0;

  public options: MockEngineOptions;
  public receivedHellos: any[] = [];
  public receivedBatches: any[] = [];
  public receivedEvents: any[] = [];
  public activeSockets: Set<WebSocket> = new Set();

  private wss: WebSocketServer | null = null;

  constructor(options?: MockEngineOptions) {
    this.options = options || {};
    if (this.options.expectedToken) this.expectedToken = this.options.expectedToken;
    if (this.options.initialAckedUpTo !== undefined) this.ackedUpTo = this.options.initialAckedUpTo;
  }

  async start(): Promise<number> {
    return new Promise((resolve, reject) => {
      this.wss = new WebSocketServer({
        port: this.options.port || 0,
        verifyClient: (info: { req: IncomingMessage }, cb: (res: boolean, code?: number) => void) => {
          if (this.options.rejectToken) {
            cb(false, 401);
            return;
          }
          const auth = info.req.headers['authorization'];
          if (this.expectedToken && auth !== `Bearer ${this.expectedToken}`) {
            cb(false, 401);
            return;
          }
          cb(true);
        }
      });

      this.wss.on('listening', () => {
        const addr = this.wss?.address();
        if (addr && typeof addr === 'object') {
          this.port = addr.port;
          resolve(this.port);
        } else {
          reject(new Error('Failed to bind MockEngineServer'));
        }
      });

      this.wss.on('connection', (ws: WebSocket) => {
        this.activeSockets.add(ws);

        ws.on('message', (data: Buffer | string) => {
          let msg: any;
          try {
            msg = JSON.parse(data.toString('utf8'));
          } catch {
            return;
          }

          if (msg.kind === 'hello') {
            this.receivedHellos.push(msg);

            if (this.options.sendMalformed) {
              ws.send('INVALID_MALFORMED_JSON_STRING{{{');
              return;
            }

            // Respond with welcome
            ws.send(JSON.stringify({
              kind: 'welcome',
              ackedUpTo: this.ackedUpTo,
              cfg: { heartbeatSec: 5 }
            }));
          } else if (msg.kind === 'events') {
            if (this.options.dropMidBatch) {
              // Simulate connection drop mid-batch
              ws.terminate();
              this.options.dropMidBatch = false; // drop once
              return;
            }

            this.receivedBatches.push(msg);
            if (Array.isArray(msg.events)) {
              this.receivedEvents.push(...msg.events);
              const maxSeq = Math.max(...msg.events.map((e: any) => e.seq));

              const sendAckResponse = () => {
                if (ws.readyState === WebSocket.OPEN) {
                  this.ackedUpTo = Math.max(this.ackedUpTo, maxSeq);
                  ws.send(JSON.stringify({
                    kind: 'ack',
                    batchId: msg.batchId,
                    upTo: this.ackedUpTo
                  }));
                }
              };

              if (this.options.delayAckMs) {
                setTimeout(sendAckResponse, this.options.delayAckMs);
              } else {
                sendAckResponse();
              }
            }
          } else if (msg.kind === 'ping') {
            if (ws.readyState === WebSocket.OPEN) {
              ws.send(JSON.stringify({ kind: 'pong' }));
            }
          }
        });

        ws.on('close', () => {
          this.activeSockets.delete(ws);
        });
      });
    });
  }

  broadcast(msg: any): void {
    const payload = JSON.stringify(msg);
    for (const ws of this.activeSockets) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(payload);
      }
    }
  }

  async close(): Promise<void> {
    for (const ws of this.activeSockets) {
      try {
        ws.terminate();
      } catch {}
    }
    this.activeSockets.clear();

    if (this.wss) {
      await new Promise<void>((resolve) => {
        this.wss?.close(() => resolve());
      });
      this.wss = null;
    }
  }
}
