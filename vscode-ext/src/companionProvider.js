/**
 * Dhyan VS Code Companion Webview Provider
 * Renders the Sidebar Webview with the Frosted Cloud Spirit, Semicircular Dial,
 * 2x2 Pastel Squircle Presets, and Weekly Attention Bento cards.
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
      --dhyan-border: var(--vscode-panel-border, rgba(0, 0, 0, 0.08));
      --dhyan-text-main: var(--vscode-foreground, #121417);
      --dhyan-text-muted: var(--vscode-descriptionForeground, #64748B);
      --dhyan-lime: #7AD656;
      --dhyan-mint-bg: rgba(122, 214, 86, 0.16);
      --dhyan-lavender-bg: rgba(99, 102, 241, 0.16);
      --dhyan-sand-bg: rgba(217, 119, 6, 0.16);
      --dhyan-sky-bg: rgba(56, 189, 248, 0.16);
      --dhyan-radius-lg: 20px;
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
      padding: 14px;
      box-shadow: 0 4px 14px rgba(0, 0, 0, 0.04);
      transition: transform 0.2s cubic-bezier(0.22, 1, 0.36, 1);
    }

    /* Card 1: Frosted Cloud Companion Stage */
    .dhyan-pet-stage {
      display: flex;
      flex-direction: column;
      align-items: center;
      position: relative;
      padding: 10px 8px;
      background: linear-gradient(145deg, var(--dhyan-card) 60%, var(--dhyan-mint-bg) 100%);
    }

    .dhyan-status-badge {
      position: absolute;
      top: 8px;
      right: 8px;
      font-size: 10px;
      font-weight: 800;
      color: var(--dhyan-lime);
      display: flex;
      align-items: center;
      gap: 5px;
      background: var(--dhyan-mint-bg);
      padding: 3px 10px;
      border-radius: var(--dhyan-radius-pill);
      letter-spacing: 0.3px;
    }

    .dhyan-status-badge .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--dhyan-lime);
      box-shadow: 0 0 6px rgba(122, 214, 86, 0.8);
    }

    .dhyan-pet-caption {
      font-size: 11px;
      color: var(--dhyan-text-muted);
      margin-top: 2px;
      font-style: italic;
    }

    /* Card 2: Dial & 2x2 Squircle Presets */
    .dhyan-dial-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      cursor: ns-resize;
      padding: 4px 0 10px 0;
    }

    .dhyan-readout {
      font-size: 44px;
      font-weight: 900;
      letter-spacing: -2px;
      line-height: 1;
      margin-top: -22px;
      color: var(--dhyan-text-main);
    }

    .dhyan-unit {
      font-size: 10px;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 1px;
      color: var(--dhyan-text-muted);
      margin-top: 2px;
    }

    /* 2x2 Pastel Squircle Preset Grid */
    .dhyan-presets {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 8px;
      margin: 10px 0 14px 0;
      width: 100%;
    }

    .dhyan-squircle-btn {
      border: 1.5px solid transparent;
      border-radius: 14px;
      padding: 10px 12px;
      font-size: 11px;
      font-weight: 700;
      cursor: pointer;
      text-align: left;
      transition: all 0.18s cubic-bezier(0.22, 1, 0.36, 1);
      color: var(--dhyan-text-main);
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      min-height: 62px;
    }

    .dhyan-squircle-btn:hover {
      transform: translateY(-1px);
    }

    .dhyan-squircle-btn.coding { background: var(--dhyan-mint-bg); }
    .dhyan-squircle-btn.debug { background: var(--dhyan-lavender-bg); }
    .dhyan-squircle-btn.docs { background: var(--dhyan-sand-bg); }
    .dhyan-squircle-btn.review { background: var(--dhyan-sky-bg); }

    .dhyan-squircle-btn.active {
      border-color: var(--dhyan-text-main);
      box-shadow: 0 0 0 1.5px var(--dhyan-text-main);
    }

    .dhyan-preset-badge {
      font-size: 9px;
      font-weight: 800;
      opacity: 0.85;
      align-self: flex-end;
    }

    .dhyan-cta-pill {
      width: 100%;
      background: var(--vscode-button-background, #121417);
      color: var(--vscode-button-foreground, #FFFFFF);
      border: none;
      border-radius: var(--dhyan-radius-pill);
      padding: 12px 18px;
      font-size: 13px;
      font-weight: 800;
      letter-spacing: 0.3px;
      cursor: pointer;
      transition: all 0.15s ease;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
    }

    .dhyan-cta-pill:hover {
      opacity: 0.94;
      transform: translateY(-1px);
    }

    /* Card 3: Weekly Bar Chart & Waveform Sparkline */
    .dhyan-chart-title {
      font-size: 11px;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      color: var(--dhyan-text-muted);
      margin-bottom: 10px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .dhyan-bars {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      height: 75px;
      gap: 6px;
      padding-top: 14px;
    }

    .dhyan-bar-col {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      height: 100%;
      justify-content: flex-end;
    }

    .dhyan-bar-pct {
      font-size: 8px;
      font-weight: 800;
      color: var(--dhyan-text-muted);
      margin-bottom: 3px;
    }

    .dhyan-bar-fill {
      width: 100%;
      max-width: 18px;
      background: var(--dhyan-mint-bg);
      border-radius: 5px;
      transition: height 0.3s ease;
    }

    .dhyan-bar-fill.today {
      background: var(--dhyan-lime);
      box-shadow: 0 2px 8px rgba(122, 214, 86, 0.4);
    }

    .dhyan-bar-day {
      font-size: 9px;
      font-weight: 700;
      color: var(--dhyan-text-muted);
      margin-top: 5px;
    }

    .dhyan-waveform-box {
      margin-top: 12px;
      padding-top: 10px;
      border-top: 1px solid var(--dhyan-border);
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .dhyan-waveform-box span {
      font-size: 10px;
      font-weight: 700;
      color: var(--dhyan-text-muted);
    }

    .dhyan-waveform-svg {
      width: 80px;
      height: 18px;
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
      <canvas id="spirit-canvas" width="240" height="130"></canvas>
      <div class="dhyan-pet-caption" id="spirit-quote">"Watching your code flow"</div>
    </div>

    <!-- Card 2: Dial & 2x2 Squircle Presets -->
    <div class="dhyan-card">
      <div class="dhyan-dial-box" id="dial-scrubber">
        <svg id="dial-svg" width="200" height="100" viewBox="0 0 200 100"></svg>
        <div class="dhyan-readout" id="dial-minutes">25</div>
        <div class="dhyan-unit">Minutes Planned</div>
      </div>

      <!-- 2x2 Pastel Squircle Preset Grid -->
      <div class="dhyan-presets">
        <button class="dhyan-squircle-btn coding active" data-task="coding" data-min="45">
          <span>💻 Coding</span>
          <span class="dhyan-preset-badge">45m</span>
        </button>
        <button class="dhyan-squircle-btn debug" data-task="debug" data-min="25">
          <span>⚡ Debug</span>
          <span class="dhyan-preset-badge">25m</span>
        </button>
        <button class="dhyan-squircle-btn docs" data-task="docs" data-min="20">
          <span>📝 Docs</span>
          <span class="dhyan-preset-badge">20m</span>
        </button>
        <button class="dhyan-squircle-btn review" data-task="review" data-min="15">
          <span>🔍 Review</span>
          <span class="dhyan-preset-badge">15m</span>
        </button>
      </div>

      <button class="dhyan-cta-pill" id="start-btn">Start Focus Session 🚀</button>
    </div>

    <!-- Card 3: Today & Weekly Focus -->
    <div class="dhyan-card">
      <div class="dhyan-chart-title">
        <span>Weekly Distribution</span>
        <span id="streak-text" style="color: var(--dhyan-lime);">● 6d Streak</span>
      </div>
      <div class="dhyan-bars">
        <div class="dhyan-bar-col"><span class="dhyan-bar-pct">40%</span><div class="dhyan-bar-fill" style="height: 40%"></div><span class="dhyan-bar-day">M</span></div>
        <div class="dhyan-bar-col"><span class="dhyan-bar-pct">60%</span><div class="dhyan-bar-fill" style="height: 60%"></div><span class="dhyan-bar-day">T</span></div>
        <div class="dhyan-bar-col"><span class="dhyan-bar-pct">80%</span><div class="dhyan-bar-fill" style="height: 80%"></div><span class="dhyan-bar-day">W</span></div>
        <div class="dhyan-bar-col"><span class="dhyan-bar-pct">50%</span><div class="dhyan-bar-fill" style="height: 50%"></div><span class="dhyan-bar-day">T</span></div>
        <div class="dhyan-bar-col"><span class="dhyan-bar-pct">92%</span><div class="dhyan-bar-fill today" style="height: 92%"></div><span class="dhyan-bar-day">F</span></div>
        <div class="dhyan-bar-col"><span class="dhyan-bar-pct">35%</span><div class="dhyan-bar-fill" style="height: 35%"></div><span class="dhyan-bar-day">S</span></div>
        <div class="dhyan-bar-col"><span class="dhyan-bar-pct">40%</span><div class="dhyan-bar-fill" style="height: 40%"></div><span class="dhyan-bar-day">S</span></div>
      </div>
      <div class="dhyan-waveform-box">
        <span>STABILITY WAVEFORM</span>
        <svg class="dhyan-waveform-svg" viewBox="0 0 80 18">
          <path d="M 0 10 Q 20 2 40 10 T 80 10" fill="none" stroke="var(--dhyan-lime)" stroke-width="2" stroke-linecap="round" />
        </svg>
      </div>
    </div>
  </div>

  <script>
    const vscode = acquireVsCodeApi();
    let currentEmotion = 'CURIOUS';
    let lookX = 0, lookY = 0;
    let plannedMinutes = 45;
    let selectedPreset = 'coding';

    // 1. Interactive Canvas with Spring Physics & Particles
    const canvas = document.getElementById('spirit-canvas');
    const ctx = canvas.getContext('2d');
    const w = canvas.width;
    const h = canvas.height;

    let scaleX = 1, scaleY = 1;
    let velX = 0, velY = 0;
    let isMouseDown = false;
    let particles = [];

    function spawnParticle(type, count = 2) {
      for (let i = 0; i < count; i++) {
        particles.push({
          x: (Math.random() - 0.5) * 35,
          y: -10 + (Math.random() - 0.5) * 15,
          vx: (Math.random() - 0.5) * 1.5,
          vy: -0.8 - Math.random() * 1.2,
          size: type === 'zzz' ? 9 + Math.random() * 4 : 4 + Math.random() * 4,
          alpha: 1.0,
          life: 0,
          maxLife: 35 + Math.random() * 25,
          type
        });
      }
    }

    function renderCloudSpirit(ctx, w, h, lookX, lookY, scaleX, scaleY, emotion) {
      ctx.clearRect(0, 0, w, h);
      ctx.save();
      ctx.translate(w / 2, h / 2);
      ctx.scale(scaleX, scaleY);

      // Aura
      const aura = ctx.createRadialGradient(0, 0, 8, 0, 0, w * 0.44);
      if (emotion === 'FOCUSED') {
        aura.addColorStop(0, 'rgba(122, 214, 86, 0.42)');
      } else if (emotion === 'HAPPY') {
        aura.addColorStop(0, 'rgba(251, 191, 36, 0.40)');
      } else if (emotion === 'SLEEPY') {
        aura.addColorStop(0, 'rgba(147, 197, 253, 0.30)');
      } else {
        aura.addColorStop(0, 'rgba(124, 200, 248, 0.35)');
      }
      aura.addColorStop(1, 'rgba(0, 0, 0, 0)');
      ctx.fillStyle = aura;
      ctx.beginPath();
      ctx.arc(0, 0, w * 0.44, 0, Math.PI * 2);
      ctx.fill();

      // Cloud Silhouette
      const bodyGrad = ctx.createRadialGradient(-w * 0.08, -h * 0.1, 4, 0, 4, w * 0.34);
      bodyGrad.addColorStop(0, '#FFFFFF');
      if (emotion === 'FOCUSED') {
        bodyGrad.addColorStop(0.55, '#9FE855');
        bodyGrad.addColorStop(1, '#4E9E26');
      } else if (emotion === 'HAPPY') {
        bodyGrad.addColorStop(0.55, '#FDE047');
        bodyGrad.addColorStop(1, '#CA8A04');
      } else if (emotion === 'SLEEPY') {
        bodyGrad.addColorStop(0.55, '#93C5FD');
        bodyGrad.addColorStop(1, '#2563EB');
      } else {
        bodyGrad.addColorStop(0.55, '#38BDF8');
        bodyGrad.addColorStop(1, '#0284C7');
      }

      ctx.fillStyle = bodyGrad;
      ctx.beginPath();
      ctx.arc(-18, 5, 20, 0, Math.PI * 2);
      ctx.arc(18, 5, 20, 0, Math.PI * 2);
      ctx.arc(0, -10, 24, 0, Math.PI * 2);
      if (ctx.roundRect) {
        ctx.roundRect(-28, -5, 56, 28, 14);
      } else {
        ctx.rect(-28, -5, 56, 28);
      }
      ctx.fill();

      // Eyes & Pupil tracking
      const dx = Math.max(-5.5, Math.min(5.5, lookX * 5.5));
      const dy = Math.max(-3.5, Math.min(3.5, lookY * 3.5));

      [-11, 11].forEach(eyeX => {
        ctx.fillStyle = '#FFFFFF';
        ctx.beginPath();
        ctx.ellipse(eyeX + dx * 0.35, -2 + dy * 0.35, 7.2, emotion === 'SLEEPY' ? 2.2 : 8.2, 0, 0, Math.PI * 2);
        ctx.fill();

        if (emotion !== 'SLEEPY') {
          ctx.fillStyle = '#121417';
          ctx.beginPath();
          ctx.arc(eyeX + dx, -2 + dy, 4.0, 0, Math.PI * 2);
          ctx.fill();

          ctx.fillStyle = '#FFFFFF';
          ctx.beginPath();
          ctx.arc(eyeX + dx - 1.3, -4 + dy, 1.4, 0, Math.PI * 2);
          ctx.fill();
        }
      });

      // Smile
      ctx.strokeStyle = '#121417';
      ctx.lineWidth = 2.2;
      ctx.lineCap = 'round';
      ctx.beginPath();
      if (emotion === 'HAPPY') {
        ctx.arc(dx * 0.4, 6 + dy * 0.2, 5, 0.1 * Math.PI, 0.9 * Math.PI);
      } else if (emotion === 'SLEEPY') {
        ctx.arc(dx * 0.4, 7 + dy * 0.2, 2.5, 0, Math.PI);
      } else {
        ctx.arc(dx * 0.4, 6 + dy * 0.2, 4.2, 0.15 * Math.PI, 0.85 * Math.PI);
      }
      ctx.stroke();

      ctx.restore();

      // Render Particles
      if (particles.length > 0) {
        ctx.save();
        ctx.translate(w / 2, h / 2);
        particles.forEach(p => {
          ctx.save();
          ctx.globalAlpha = Math.max(0, p.alpha);
          ctx.translate(p.x, p.y);
          if (p.type === 'sparkle') {
            ctx.fillStyle = '#FBBF24';
            const r = p.size;
            ctx.beginPath();
            ctx.moveTo(0, -r);
            ctx.quadraticCurveTo(0, 0, r, 0);
            ctx.quadraticCurveTo(0, 0, 0, r);
            ctx.quadraticCurveTo(0, 0, -r, 0);
            ctx.quadraticCurveTo(0, 0, 0, -r);
            ctx.fill();
          } else if (p.type === 'zzz') {
            ctx.fillStyle = '#60A5FA';
            ctx.font = \`bold \${Math.round(p.size)}px sans-serif\`;
            ctx.fillText('z', 0, 0);
          }
          ctx.restore();
        });
        ctx.restore();
      }
    }

    let time = 0;
    function animate() {
      time += 0.04;

      // Spring physics
      const targetScaleX = isMouseDown ? 1.25 : 1.0;
      const targetScaleY = isMouseDown ? 0.78 : 1.0;
      const k = 0.16;
      const damping = 0.78;

      velX = (velX + (targetScaleX - scaleX) * k) * damping;
      velY = (velY + (targetScaleY - scaleY) * k) * damping;
      scaleX += velX;
      scaleY += velY;

      const idleBreathing = Math.sin(time) * 0.024;
      const finalX = scaleX + idleBreathing;
      const finalY = scaleY - idleBreathing;

      if (Math.random() < 0.03) {
        if (currentEmotion === 'HAPPY') spawnParticle('sparkle', 1);
        if (currentEmotion === 'SLEEPY') spawnParticle('zzz', 1);
      }

      particles.forEach(p => {
        p.x += p.vx;
        p.y += p.vy;
        p.life += 1;
        p.alpha = 1 - p.life / p.maxLife;
      });
      particles = particles.filter(p => p.life < p.maxLife);

      renderCloudSpirit(ctx, w, h, lookX, lookY, finalX, finalY, currentEmotion);
      requestAnimationFrame(animate);
    }
    animate();

    // Canvas Petting / Clicking
    canvas.addEventListener('mousedown', () => {
      isMouseDown = true;
      velX = 0.1;
      velY = -0.1;
      spawnParticle('sparkle', 3);
    });

    window.addEventListener('mouseup', () => {
      if (isMouseDown) {
        isMouseDown = false;
        velX = -0.1;
        velY = 0.14;
      }
    });

    // 2. Semicircular Tick Dial Generator
    function renderDialTicks(minutes) {
      const svg = document.getElementById('dial-svg');
      svg.innerHTML = '';
      const totalTicks = 23;
      const radius = 74;
      const cx = 100;
      const cy = 88;

      for (let i = 0; i < totalTicks; i++) {
        const frac = i / (totalTicks - 1);
        const angle = Math.PI + frac * Math.PI;
        const x1 = cx + (radius - 7) * Math.cos(angle);
        const y1 = cy + (radius - 7) * Math.sin(angle);
        const x2 = cx + radius * Math.cos(angle);
        const y2 = cy + radius * Math.sin(angle);
        const active = frac <= (minutes / 120);

        const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        line.setAttribute('x1', x1);
        line.setAttribute('y1', y1);
        line.setAttribute('x2', x2);
        line.setAttribute('y2', y2);
        line.setAttribute('stroke', active ? '#7AD656' : '#E2E8F0');
        line.setAttribute('stroke-width', i % 4 === 0 ? '2.8' : '1.6');
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
      spawnParticle('sparkle', 5);
    });

    // Message Listener from Extension Host
    window.addEventListener('message', (event) => {
      const msg = event.data;
      if (msg.type === 'emotion') {
        currentEmotion = msg.emotion;
        if (msg.eyeDx !== undefined) lookX = msg.eyeDx;
        if (msg.eyeDy !== undefined) lookY = msg.eyeDy;
        const quoteEl = document.getElementById('spirit-quote');
        if (currentEmotion === 'FOCUSED') quoteEl.textContent = '"Locked into coding flow"';
        else if (currentEmotion === 'HAPPY') quoteEl.textContent = '"Great progress! ✨"';
        else if (currentEmotion === 'SLEEPY') quoteEl.textContent = '"Zzz... taking a quiet break"';
        else quoteEl.textContent = '"Watching your code flow"';
      } else if (msg.type === 'focusMinutes') {
        document.getElementById('status-text').textContent = \`Focused \${msg.minutes}m\`;
      }
    });
  </script>
</body>
</html>`;
  }
}
