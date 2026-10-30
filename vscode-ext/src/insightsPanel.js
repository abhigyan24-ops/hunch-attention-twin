/**
 * Dhyan VS Code Full-Tab Insights Dashboard Panel
 * Opened via Command Palette ("Dhyan: Open Insights Dashboard")
 */

export class DhyanInsightsPanel {
  static currentPanel = null;

  static createOrShow(extensionUri, sensorInstance) {
    const column = sensorInstance?.vscode?.window?.activeTextEditor
      ? sensorInstance.vscode.window.activeTextEditor.viewColumn
      : undefined;

    if (DhyanInsightsPanel.currentPanel) {
      DhyanInsightsPanel.currentPanel.panel.reveal(column);
      return;
    }

    const vscode = sensorInstance?.vscode;
    if (!vscode) return;

    const panel = vscode.window.createWebviewPanel(
      'dhyanInsights',
      'Dhyan: Attention & Flow Insights',
      column || vscode.ViewColumn.One,
      {
        enableScripts: true,
        localResourceRoots: [extensionUri]
      }
    );

    DhyanInsightsPanel.currentPanel = new DhyanInsightsPanel(panel, extensionUri, sensorInstance);
  }

  constructor(panel, extensionUri, sensorInstance) {
    this.panel = panel;
    this.extensionUri = extensionUri;
    this.sensor = sensorInstance;

    this.panel.webview.html = this.getHtml();

    this.panel.onDidDispose(() => {
      DhyanInsightsPanel.currentPanel = null;
    });
  }

  getHtml() {
    const edits = this.sensor?.editsInWindow ?? 0;
    const saves = this.sensor?.savesInWindow ?? 0;
    const state = this.sensor?.currentState ?? 'flowing';

    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Dhyan Attention Insights</title>
  <style>
    :root {
      --dhyan-bg: var(--vscode-editor-background, #F7F8FA);
      --dhyan-card: color-mix(in srgb, var(--vscode-editor-background, #FFFFFF) 92%, #000000 8%);
      --dhyan-border: var(--vscode-panel-border, #EAEEF4);
      --dhyan-text: var(--vscode-editor-foreground, #121417);
      --dhyan-muted: var(--vscode-descriptionForeground, #64748B);
      --dhyan-lime: #7AD656;
      --dhyan-mint-bg: rgba(122, 214, 86, 0.16);
      --dhyan-lavender-bg: rgba(99, 102, 241, 0.16);
      --dhyan-radius-lg: 20px;
      font-family: var(--vscode-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto);
    }
    body {
      padding: 32px;
      margin: 0;
      background: var(--dhyan-bg);
      color: var(--dhyan-text);
    }
    .dhyan-container {
      max-width: 960px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
    .dhyan-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .dhyan-header h1 {
      font-size: 28px;
      font-weight: 800;
      margin: 0;
      letter-spacing: -0.5px;
    }
    .dhyan-bento-grid {
      display: grid;
      grid-template-columns: 1.2fr 1fr;
      gap: 16px;
    }
    .dhyan-card {
      background: var(--dhyan-card);
      border: 1px solid var(--dhyan-border);
      border-radius: var(--dhyan-radius-lg);
      padding: 20px;
      box-shadow: 0 6px 18px rgba(0, 0, 0, 0.04);
    }
    .dhyan-stats-row {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-top: 14px;
    }
    .dhyan-stat-box {
      background: var(--dhyan-mint-bg);
      border-radius: 12px;
      padding: 12px;
      display: flex;
      flex-direction: column;
    }
    .dhyan-stat-box span { font-size: 11px; font-weight: 600; color: var(--dhyan-muted); }
    .dhyan-stat-box strong { font-size: 20px; font-weight: 800; margin-top: 4px; }
  </style>
</head>
<body>
  <div class="dhyan-container">
    <div class="dhyan-header">
      <div>
        <h1>Attention & Flow Analytics</h1>
        <p style="color: var(--dhyan-muted); margin: 4px 0 0 0; font-size: 13px;">
          IDE Telemetry Bridge · Live Sensor State: <strong style="color: var(--dhyan-lime); text-transform: uppercase;">${state}</strong>
        </p>
      </div>
    </div>

    <div class="dhyan-bento-grid">
      <div class="dhyan-card">
        <h3 style="margin-top: 0; font-size: 16px;">Active Window Telemetry</h3>
        <p style="font-size: 12px; color: var(--dhyan-muted);">
          Audited privacy-preserving metrics. Zero file names, code contents, or keystrokes are recorded.
        </p>
        <div class="dhyan-stats-row">
          <div class="dhyan-stat-box">
            <span>Window Edits</span>
            <strong>${edits}</strong>
          </div>
          <div class="dhyan-stat-box">
            <span>Saves</span>
            <strong>${saves}</strong>
          </div>
          <div class="dhyan-stat-box">
            <span>Stability</span>
            <strong style="color: var(--dhyan-lime);">94% Flow</strong>
          </div>
        </div>
      </div>

      <div class="dhyan-card">
        <h3 style="margin-top: 0; font-size: 16px;">Desktop Sync</h3>
        <p style="font-size: 12px; color: var(--dhyan-muted);">
          Feeding local Dhyan ingest server on <strong>127.0.0.1:47821</strong>.
        </p>
        <div style="margin-top: 16px; padding: 12px; background: var(--dhyan-lavender-bg); border-radius: 12px;">
          <span style="font-size: 12px; font-weight: 600;">Status: Streaming to Desktop Companion</span>
        </div>
      </div>
    </div>
  </div>
</body>
</html>`;
  }
}
