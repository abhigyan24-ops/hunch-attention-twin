import { classifyUrl } from './domain-classifier.js';

export class DhyanBrowserSensor {
  constructor(options = {}) {
    this.port = options.port || 47821;
    this.token = options.token || '';
    this.extensionId = options.extensionId || 'dhyan-mock-extension-id';
    this.clientVersion = options.clientVersion || '1.0.0';
    this.WebSocketClass = options.WebSocketClass || (typeof WebSocket !== 'undefined' ? WebSocket : null);

    this.stopped = false;
    this.ws = null;
    this.authenticated = false;
    this.heartbeatTimer = null;
    this.reconnectTimer = null;
    this.reconnectAttempts = 0;

    // Current state (privacy: categories and counts only)
    this.currentDomainCat = 'unknown';
    this.currentSurface = 'other';
    this.currentVideoState = 'none';
    this.accumulatedScrolls = 0;
    this.tabActive = true;
  }

  start() {
    this.connect();
  }

  stop() {
    this.stopped = true;
    clearInterval(this.heartbeatTimer);
    clearTimeout(this.reconnectTimer);
    if (this.ws) {
      try {
        if (typeof this.ws.removeAllListeners === 'function') {
          this.ws.removeAllListeners();
        }
        this.ws.close();
      } catch (_) {}
    }
  }

  connect() {
    if (this.stopped) return;
    clearTimeout(this.reconnectTimer);
    const url = `ws://127.0.0.1:${this.port}/ingest`;

    try {
      const originHeader = `chrome-extension://${this.extensionId}`;
      this.ws = new this.WebSocketClass(url, {
        headers: {
          'Origin': originHeader
        }
      });
    } catch (err) {
      if (!this.stopped) this.scheduleReconnect();
      return;
    }

    this.ws.onopen = () => {
      this.reconnectAttempts = 0;
      // Step 1: Send auth within 3 seconds
      this.send({
        kind: 'auth',
        token: this.token,
        client: 'browser',
        clientVersion: this.clientVersion
      });
    };

    this.ws.onmessage = (event) => {
      let msg;
      try {
        msg = JSON.parse(event.data);
      } catch (_) {
        return;
      }

      if (msg.kind === 'auth_ok') {
        this.authenticated = true;
        this.startHeartbeat(msg.config && msg.config.heartbeatSec ? msg.config.heartbeatSec : 5);
        this.emitTabCategory();
      }
    };

    this.ws.onclose = () => {
      this.authenticated = false;
      clearInterval(this.heartbeatTimer);
      if (!this.stopped) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = () => {
      // ws close event will trigger reconnect
    };
  }

  scheduleReconnect() {
    const delay = Math.min(1000 * Math.pow(1.5, this.reconnectAttempts++), 30000);
    this.reconnectTimer = setTimeout(() => {
      this.connect();
    }, delay);
  }

  send(msg) {
    if (this.ws && (this.ws.readyState === 1 || this.ws.readyState === (this.WebSocketClass && this.WebSocketClass.OPEN))) {
      this.ws.send(JSON.stringify(msg));
    }
  }

  startHeartbeat(heartbeatSec) {
    clearInterval(this.heartbeatTimer);
    this.heartbeatTimer = setInterval(() => {
      if (this.authenticated) {
        this.send({
          kind: 'ext_heartbeat',
          ts: Date.now()
        });
      }
    }, heartbeatSec * 1000);
  }

  onTabUrlChange(urlStr, active = true) {
    this.tabActive = active;
    // Categorize domain locally; NEVER keep or emit the raw url!
    this.currentDomainCat = classifyUrl(urlStr);
    this.emitTabCategory();
  }

  onContentUpdate(update) {
    if (update.surface) this.currentSurface = update.surface;
    if (update.videoState) this.currentVideoState = update.videoState;
    if (typeof update.scrollCount === 'number') this.accumulatedScrolls += update.scrollCount;
    this.emitTabCategory();
  }

  emitTabCategory() {
    if (!this.authenticated) return;

    const frame = {
      kind: 'tab_category',
      ts: Date.now(),
      domainCat: this.currentDomainCat,
      surface: this.currentSurface,
      videoState: this.currentVideoState,
      scrollCount: this.accumulatedScrolls,
      tabActive: this.tabActive
    };

    this.send(frame);
    this.accumulatedScrolls = 0; // reset scroll counter after flush
  }
}

// Browser MV3 Lifecycle
if (typeof chrome !== 'undefined' && chrome.runtime) {
  let sensor = null;

  async function init() {
    const data = await chrome.storage.local.get(['ingestPort', 'ingestToken']);
    const port = data.ingestPort || 47821;
    const token = data.ingestToken || '';

    sensor = new DhyanBrowserSensor({
      port,
      token,
      extensionId: chrome.runtime.id,
      WebSocketClass: WebSocket
    });
    sensor.start();
  }

  init();

  // Listen to tab changes
  if (chrome.tabs) {
    chrome.tabs.onActivated.addListener(async (activeInfo) => {
      if (!sensor) return;
      try {
        const tab = await chrome.tabs.get(activeInfo.tabId);
        if (tab && tab.url) {
          sensor.onTabUrlChange(tab.url, true);
        }
      } catch (_) {}
    });

    chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
      if (!sensor || !tab.active || !changeInfo.url) return;
      sensor.onTabUrlChange(changeInfo.url, true);
    });
  }

  // Listen to content script updates
  chrome.runtime.onMessage.addListener((message) => {
    if (message.type === 'content_update' && sensor) {
      sensor.onContentUpdate(message);
    }
  });
}
