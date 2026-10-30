/**
 * Dhyan VS Code Companion Webview Provider
 * Renders the Sidebar Webview with the Frosted Cloud Spirit, Semicircular Dial,
 * and Weekly Attention Bento cards.
 */

export class DhyanCompanionViewProvider {
  constructor(extensionUri, sensorInstance) {
    this.extensionUri = extensionUri;
    this.sensor = sensorInstance;
    this.view = null;
    this.currentEmotion = 'CURIOUS';
  }

  resolveWebviewView(webviewView, context, token) {
    this.view = webviewView;

    webviewView.webview.options = {
      enableScripts: true,
      localResourceRoots: [this.extensionUri]
    };

    webviewView.webview.html = this.getHtmlForWebview(webviewView.webview);

    webviewView.webview.onDidReceiveMessage((message) => {
      switch (message.command) {
        case 'startSession':
          if (this.sensor) {
            this.sensor.startSession(message.preset, message.minutes);
          }
          break;
        case 'openInsights':
          if (this.sensor && this.sensor.openInsights) {
            this.sensor.openInsights();
          }
          break;
      }
    });
  }

  updateEmotion(emotion, eyeDx = 0, eyeDy = 0) {
    this.currentEmotion = emotion;
    if (this.view) {
      this.view.webview.postMessage({
        type: 'emotion',
        emotion,
        eyeDx,
        eyeDy
      });
    }
  }

  updateFocusMinutes(minutes) {
    if (this.view) {
      this.view.webview.postMessage({
        type: 'focusMinutes',
        minutes
      });
    }
  }

  getHtmlForWebview(webview) {
    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Dhyan Companion</title>
  <style>
    :root {
      --dhyan-bg: var(--vscode-sideBar-background, #F7F8FA);
      --dhyan-card: color-mix(in srgb, var(--vscode-editor-background, #FFFFFF) 94%, #000000 6%);
      --dhyan-border: var(--vscode-panel-border, #EAEEF4);
      --dhyan-text-main: var(--vscode-foreground, #121417);
      --dhyan-text-muted: var(--vscode-descriptionForeground, #64748B);
      --dhyan-lime: #7AD656;
      --dhyan-mint-bg: rgba(122, 214, 86, 0.16);
      --dhyan-lavender-bg: rgba(99, 102, 241, 0.16);
      --dhyan-sand-bg: rgba(217, 119, 6, 0.16);
      --dhyan-sky-bg: rgba(56, 189, 248, 0.16);
      --dhyan-radius-lg: 18px;
      --dhyan-radius-pill: 9999px;
      font-family: var(--vscode-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto);
    }

    body {
      padding: 12px;
      margin: 0;
      background: var(--dhyan-bg);
      color: var(--dhyan-text-main);
      box-sizing: border-box;
      user-select: none;
    }

    .dhyan-stack {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .dhyan-card {
      background: var(--dhyan-card);
      border: 1px solid var(--dhyan-border);
      border-radius: var(--dhyan-radius-lg);
      padding: 12px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
    }

    /* Card 1: Frosted Cloud Companion Stage */
    .dhyan-pet-stage {
      display: flex;
      flex-direction: column;
      align-items: center;
      position: relative;
      padding: 6px;
    }

    .dhyan-status-badge {
      position: absolute;
      top: 4px;
      right: 4px;
      font-size: 10px;
      font-weight: 700;
      color: var(--dhyan-lime);
      display: flex;
      align-items: center;
      gap: 4px;
      background: var(--dhyan-mint-bg);
      padding: 2px 8px;
      border-radius: var(--dhyan-radius-pill);
    }

    .dhyan-status-badge .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--dhyan-lime);
    }

    /* Card 2: Dial & Presets */
    .dhyan-dial-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      cursor: ns-resize;
      padding: 6px 0;
    }

    .dhyan-readout {
      font-size: 40px;
      font-weight: 900;
      letter-spacing: -1.5px;
      line-height: 1;
      margin-top: -18px;
    }

    .dhyan-unit {
      font-size: 10px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      color: var(--dhyan-text-muted);
      margin-top: 2px;
    }

    .dhyan-presets {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 6px;
      margin: 10px 0;
      width: 100%;
    }

    .dhyan-squircle-btn {
      border: 1px solid transparent;
      border-radius: 12px;
      padding: 8px 10px;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      text-align: left;
      transition: all 0.15s ease;
      color: var(--dhyan-text-main);
    }

    .dhyan-squircle-btn.coding { background: var(--dhyan-lavender-bg); }
    .dhyan-squircle-btn.debug { background: var(--dhyan-sand-bg); }
    .dhyan-squircle-btn.docs { background: var(--dhyan-mint-bg); }
    .dhyan-squircle-btn.review { background: var(--dhyan-sky-bg); }
    .dhyan-squircle-btn.active {
      box-shadow: 0 0 0 1.5px var(--dhyan-text-main);
      font-weight: 700;
    }

    .dhyan-cta-pill {
      width: 100%;
      background: var(--vscode-button-background, #121417);
      color: var(--vscode-button-foreground, #FFFFFF);
      border: none;
      border-radius: var(--dhyan-radius-pill);
      padding: 10px 14px;
      font-size: 12px;
      font-weight: 700;
      cursor: pointer;
      transition: opacity 0.15s ease;
    }

    .dhyan-cta-pill:hover {
      opacity: 0.9;
    }

    /* Card 3: Weekly Bar Chart */
    .dhyan-chart-title {
      font-size: 10px;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 0.6px;
      color: var(--dhyan-text-muted);
      margin-bottom: 8px;
      display: flex;
      justify-content: space-between;
    }

    .dhyan-bars {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      height: 60px;
      gap: 4px;
    }

    .dhyan-bar-col {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      height: 100%;
      justify-content: flex-end;
    }

    .dhyan-bar-fill {
      width: 100%;
      max-width: 16px;
      background: var(--dhyan-mint-bg);
      border-radius: 4px;
      transition: height 0.3s ease;
    }

    .dhyan-bar-fill.today {
      background: var(--dhyan-lime);
    }

    .dhyan-bar-day {
      font-size: 9px;
      font-weight: 600;
      color: var(--dhyan-text-muted);
      margin-top: 4px;
    }
  </style>
</head>
<body>
  <div class="dhyan-stack">
    <!-- Card 1: Frosted Cloud Companion Stage -->
    <div class="dhyan-card dhyan-pet-stage">
      <div class="dhyan-status-badge">
        <span class="dot"></span>
        <span id="status-text">Connected</span>
      </div>
      <canvas id="spirit-canvas" width="220" height="120"></canvas>
    </div>

    <!-- Card 2: Dial & Presets -->
    <div class="dhyan-card">
      <div class="dhyan-dial-box" id="dial-scrubber">
        <svg id="dial-svg" width="180" height="90" viewBox="0 0 180 90"></svg>
        <div class="dhyan-readout" id="dial-minutes">25</div>
        <div class="dhyan-unit">Minutes</div>
      </div>

      <div class="dhyan-presets">
        <button class="dhyan-squircle-btn coding active" data-task="coding" data-min="45">💻 Coding</button>
        <button class="dhyan-squircle-btn debug" data-task="debug" data-min="25">🐞 Debug</button>
        <button class="dhyan-squircle-btn docs" data-task="docs" data-min="20">📝 Docs</button>
        <button class="dhyan-squircle-btn review" data-task="review" data-min="15">🔍 Review</button>
      </div>

      <button class="dhyan-cta-pill" id="start-btn">Start Focus Session</button>
    </div>

    <!-- Card 3: Today & Weekly Focus -->
    <div class="dhyan-card">
      <div class="dhyan-chart-title">
        <span>Weekly Distribution</span>
        <span id="streak-text">6d Streak</span>
      </div>
      <div class="dhyan-bars">
        <div class="dhyan-bar-col"><div class="dhyan-bar-fill" style="height: 40%"></div><span class="dhyan-bar-day">M</span></div>
        <div class="dhyan-bar-col"><div class="dhyan-bar-fill" style="height: 60%"></div><span class="dhyan-bar-day">T</span></div>
        <div class="dhyan-bar-col"><div class="dhyan-bar-fill" style="height: 80%"></div><span class="dhyan-bar-day">W</span></div>
        <div class="dhyan-bar-col"><div class="dhyan-bar-fill" style="height: 50%"></div><span class="dhyan-bar-day">T</span></div>
        <div class="dhyan-bar-col"><div class="dhyan-bar-fill today" style="height: 90%"></div><span class="dhyan-bar-day">F</span></div>
        <div class="dhyan-bar-col"><div class="dhyan-bar-fill" style="height: 30%"></div><span class="dhyan-bar-day">S</span></div>
        <div class="dhyan-bar-col"><div class="dhyan-bar-fill" style="height: 35%"></div><span class="dhyan-bar-day">S</span></div>
      </div>
    </div>
  </div>

  <script>
    const vscode = acquireVsCodeApi();
    let currentEmotion = 'CURIOUS';
    let lookX = 0, lookY = 0;
    let plannedMinutes = 25;
    let selectedPreset = 'coding';

    // 1. Canvas Rendering
    const canvas = document.getElementById('spirit-canvas');
    const ctx = canvas.getContext('2d');
    const w = canvas.width;
    const h = canvas.height;

    function renderCloudSpirit(ctx, w, h, lookX, lookY, scaleX, scaleY, emotion) {
      ctx.clearRect(0, 0, w, h);
      ctx.save();
      ctx.translate(w / 2, h / 2);
      ctx.scale(scaleX, scaleY);

      // Aura
      const aura = ctx.createRadialGradient(0, 0, 8, 0, 0, w * 0.42);
      aura.addColorStop(0, emotion === 'FOCUSED' ? 'rgba(124, 232, 158, 0.4)' : 'rgba(124, 200, 248, 0.35)');
      aura.addColorStop(1, 'rgba(124, 232, 158, 0)');
      ctx.fillStyle = aura;
      ctx.beginPath();
      ctx.arc(0, 0, w * 0.42, 0, Math.PI * 2);
      ctx.fill();

      // Cloud Silhouette
      const bodyGrad = ctx.createRadialGradient(-w * 0.08, -h * 0.1, 4, 0, 4, w * 0.32);
      bodyGrad.addColorStop(0, '#E8FFF0');
      bodyGrad.addColorStop(0.55, emotion === 'FOCUSED' ? '#7CE89E' : '#89D4FA');
      bodyGrad.addColorStop(1, emotion === 'FOCUSED' ? '#34B371' : '#3B9EDB');

      ctx.fillStyle = bodyGrad;
      ctx.beginPath();
      ctx.arc(-16, 5, 18, 0, Math.PI * 2);
      ctx.arc(16, 5, 18, 0, Math.PI * 2);
      ctx.arc(0, -9, 22, 0, Math.PI * 2);
      if (ctx.roundRect) {
        ctx.roundRect(-26, -4, 52, 26, 13);
      } else {
        ctx.rect(-26, -4, 52, 26);
      }
      ctx.fill();

      // Eyes
      const dx = Math.max(-5, Math.min(5, lookX * 5));
      const dy = Math.max(-3, Math.min(3, lookY * 3));

      [-10, 10].forEach(eyeX => {
        ctx.fillStyle = '#FFFFFF';
        ctx.beginPath();
        ctx.ellipse(eyeX + dx * 0.4, -2 + dy * 0.4, 6.5, emotion === 'SLEEPY' ? 2 : 7.5, 0, 0, Math.PI * 2);
        ctx.fill();

        if (emotion !== 'SLEEPY') {
          ctx.fillStyle = '#121417';
          ctx.beginPath();
          ctx.arc(eyeX + dx, -2 + dy, 3.8, 0, Math.PI * 2);
          ctx.fill();

          ctx.fillStyle = '#FFFFFF';
          ctx.beginPath();
          ctx.arc(eyeX + dx - 1.2, -3.5 + dy, 1.2, 0, Math.PI * 2);
          ctx.fill();
        }
      });

      // Smile
      ctx.strokeStyle = '#121417';
      ctx.lineWidth = 2;
      ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.arc(dx * 0.5, 5 + dy * 0.3, 4, 0.15 * Math.PI, 0.85 * Math.PI);
      ctx.stroke();

      ctx.restore();
    }

    let time = 0;
    function animate() {
      time += 0.04;
      const breathe = Math.sin(time) * 0.03;
      renderCloudSpirit(ctx, w, h, lookX, lookY, 1 + breathe, 1 - breathe, currentEmotion);
      requestAnimationFrame(animate);
    }
    animate();

    // 2. Semicircular Tick Dial Generator
    function renderDialTicks(minutes) {
      const svg = document.getElementById('dial-svg');
      svg.innerHTML = '';
      const totalTicks = 21;
      const radius = 68;
      const cx = 90;
      const cy = 80;

      for (let i = 0; i < totalTicks; i++) {
        const frac = i / (totalTicks - 1);
        const angle = Math.PI + frac * Math.PI;
        const x1 = cx + (radius - 6) * Math.cos(angle);
        const y1 = cy + (radius - 6) * Math.sin(angle);
        const x2 = cx + radius * Math.cos(angle);
        const y2 = cy + radius * Math.sin(angle);
        const active = frac <= (minutes / 120);

        const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        line.setAttribute('x1', x1);
        line.setAttribute('y1', y1);
        line.setAttribute('x2', x2);
        line.setAttribute('y2', y2);
        line.setAttribute('stroke', active ? '#7AD656' : '#E2E8F0');
        line.setAttribute('stroke-width', i % 4 === 0 ? '2.5' : '1.5');
        line.setAttribute('stroke-linecap', 'round');
        svg.appendChild(line);
      }
    }
    renderDialTicks(plannedMinutes);

    // Wheel and click-drag on dial
    const dialBox = document.getElementById('dial-scrubber');
    dialBox.addEventListener('wheel', (e) => {
      e.preventDefault();
      const delta = e.deltaY < 0 ? 5 : -5;
      plannedMinutes = Math.max(5, Math.min(120, plannedMinutes + delta));
      document.getElementById('dial-minutes').textContent = plannedMinutes;
      renderDialTicks(plannedMinutes);
    });

    // Preset buttons
    document.querySelectorAll('.dhyan-squircle-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.dhyan-squircle-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        selectedPreset = btn.getAttribute('data-task');
        plannedMinutes = parseInt(btn.getAttribute('data-min'), 10);
        document.getElementById('dial-minutes').textContent = plannedMinutes;
        renderDialTicks(plannedMinutes);
      });
    });

    // Start Button
    document.getElementById('start-btn').addEventListener('click', () => {
      vscode.postMessage({
        command: 'startSession',
        preset: selectedPreset,
        minutes: plannedMinutes
      });
    });

    // Message Listener from Extension Host
    window.addEventListener('message', (event) => {
      const msg = event.data;
      if (msg.type === 'emotion') {
        currentEmotion = msg.emotion;
        if (msg.eyeDx !== undefined) lookX = msg.eyeDx;
        if (msg.eyeDy !== undefined) lookY = msg.eyeDy;
      } else if (msg.type === 'focusMinutes') {
        document.getElementById('status-text').textContent = \`Focused \${msg.minutes}m\`;
      }
    });
  </script>
</body>
</html>`;
  }
}
