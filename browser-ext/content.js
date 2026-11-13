// Hunch Content Script: Measures activity metrics and intercepts rabbit-hole drift.
// Privacy: Zero document text, titles, URLs or search strings are emitted in telemetry.
// Tab parking only transmits the user's explicitly selected parked tab to the local sidecar.

(function () {
  let scrollCount = 0;
  let activeDwellSeconds = 0;
  let bannerShown = false;
  let snoozeUntil = 0;
  const RABBIT_HOLE_DOMAINS = [
    'youtube.com', 'x.com', 'twitter.com', 'reddit.com',
    'instagram.com', 'tiktok.com', 'netflix.com', 'facebook.com', 'twitch.tv'
  ];

  const currentHostname = (window.location.hostname || '').toLowerCase();
  const isRabbitHole = RABBIT_HOLE_DOMAINS.some(d => currentHostname.endsWith(d));

  function detectSurface() {
    const pathname = window.location.pathname || '';
    const hostname = window.location.hostname || '';

    // Shorts / Reels detection
    if (pathname.startsWith('/shorts') || pathname.startsWith('/reel')) {
      return 'shorts';
    }

    // Video watch surface
    if (pathname.startsWith('/watch') || hostname.includes('netflix.com') || hostname.includes('twitch.tv')) {
      return 'watch';
    }

    // Feed surface
    if (pathname === '/' || pathname.startsWith('/feed') || pathname.startsWith('/home')) {
      return 'feed';
    }

    // Chat surface
    if (hostname.includes('whatsapp.com') || hostname.includes('telegram.org') || pathname.startsWith('/channels/')) {
      return 'chat';
    }

    // Editor surface
    if (pathname.includes('/edit') || hostname.includes('docs.google.com') || hostname.includes('notion.so')) {
      return 'editor';
    }

    return 'other';
  }

  function detectVideoState() {
    const videos = document.querySelectorAll('video');
    if (videos.length === 0) return 'none';

    for (let i = 0; i < videos.length; i++) {
      const v = videos[i];
      if (!v.paused && !v.ended && v.currentTime > 0) {
        return 'playing';
      }
    }
    return 'paused';
  }

  // Count scroll & wheel events
  window.addEventListener('wheel', () => {
    scrollCount++;
  }, { passive: true });

  window.addEventListener('scroll', () => {
    scrollCount++;
  }, { passive: true });

  // Periodic report to background script
  function reportMetrics() {
    const surface = detectSurface();
    const videoState = detectVideoState();
    const currentScrolls = scrollCount;
    scrollCount = 0; // reset window count

    try {
      if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.sendMessage) {
        chrome.runtime.sendMessage({
          type: 'content_update',
          surface,
          videoState,
          scrollCount: currentScrolls
        });
      }
    } catch (_) {}
  }

  // Send update every 5 seconds
  setInterval(reportMetrics, 5000);
  setTimeout(reportMetrics, 500);

  // ── Rabbit-Hole Interceptor & 1-Click Tab Parking (Upgrade 5) ───────────────

  async function checkRabbitHoleDrift() {
    if (!isRabbitHole || bannerShown || Date.now() < snoozeUntil) {
      return;
    }

    // Only count dwell time if document is visible
    if (document.visibilityState === 'visible') {
      activeDwellSeconds += 1;
    }

    // Check drift threshold: > 240 seconds (4 minutes)
    if (activeDwellSeconds >= 240) {
      try {
        const res = await fetch('http://127.0.0.1:8765/v1/flow/active-coding');
        if (res.ok) {
          const data = await res.json();
          if (data && data.is_coding) {
            showParkingBanner(data.active_workspace || 'code workspace');
          }
        }
      } catch (_) {
        // Engine sidecar offline or unreachable
      }
    }
  }

  function showParkingBanner(workspaceName) {
    if (bannerShown) return;
    bannerShown = true;

    const banner = document.createElement('div');
    banner.id = 'hunch-tab-parking-banner';
    banner.style.cssText = `
      position: fixed;
      top: 16px;
      left: 50%;
      transform: translateX(-50%);
      z-index: 2147483647;
      background: rgba(18, 18, 24, 0.96);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      color: #f4f4f5;
      padding: 10px 18px;
      border-radius: 9999px;
      box-shadow: 0 12px 30px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.12);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 13px;
      display: flex;
      align-items: center;
      gap: 14px;
      animation: hunchSlideDown 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    `;

    const keyframes = document.createElement('style');
    keyframes.textContent = `
      @keyframes hunchSlideDown {
        from { transform: translate(-50%, -100%); opacity: 0; }
        to { transform: translate(-50%, 0); opacity: 1; }
      }
    `;
    document.head.appendChild(keyframes);

    const message = document.createElement('span');
    message.innerHTML = `<strong>Hunch Flow:</strong> You were coding in <em>${workspaceName}</em> 4 mins ago.`;

    const parkBtn = document.createElement('button');
    parkBtn.textContent = 'Park Tab for Break';
    parkBtn.style.cssText = `
      background: #6366f1;
      color: #ffffff;
      border: none;
      padding: 6px 14px;
      border-radius: 9999px;
      font-weight: 600;
      font-size: 12px;
      cursor: pointer;
      transition: background 0.15s;
    `;
    parkBtn.onmouseover = () => { parkBtn.style.background = '#4f46e5'; };
    parkBtn.onmouseout = () => { parkBtn.style.background = '#6366f1'; };

    parkBtn.onclick = async () => {
      parkBtn.textContent = 'Parking...';
      parkBtn.disabled = true;
      try {
        await fetch('http://127.0.0.1:8765/v1/flow/park-tab', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            url: window.location.href,
            title: document.title || currentHostname,
            from_workspace: workspaceName,
          }),
        });

        banner.innerHTML = `<span style="color: #34d399; font-weight: 500;">✓ Tab saved to your Hunch break digest. Ready to dive back into ${workspaceName}!</span>`;
        setTimeout(() => {
          banner.remove();
        }, 3000);
      } catch (_) {
        banner.innerHTML = '<span style="color: #f87171;">Failed to save tab.</span>';
        setTimeout(() => banner.remove(), 2500);
      }
    };

    const backBtn = document.createElement('button');
    backBtn.textContent = 'Return to Code';
    backBtn.style.cssText = `
      background: rgba(255, 255, 255, 0.1);
      color: #e4e4e7;
      border: 1px solid rgba(255, 255, 255, 0.15);
      padding: 6px 14px;
      border-radius: 9999px;
      font-size: 12px;
      cursor: pointer;
    `;
    backBtn.onclick = () => {
      snoozeUntil = Date.now() + 15 * 60 * 1000;
      banner.remove();
      bannerShown = false;
    };

    const closeBtn = document.createElement('button');
    closeBtn.textContent = '✕';
    closeBtn.style.cssText = `
      background: transparent;
      color: #a1a1aa;
      border: none;
      font-size: 14px;
      cursor: pointer;
      padding: 2px 6px;
    `;
    closeBtn.onclick = () => {
      snoozeUntil = Date.now() + 15 * 60 * 1000;
      banner.remove();
      bannerShown = false;
    };

    banner.appendChild(message);
    banner.appendChild(parkBtn);
    banner.appendChild(backBtn);
    banner.appendChild(closeBtn);
    document.body.appendChild(banner);
  }

  if (isRabbitHole) {
    setInterval(checkRabbitHoleDrift, 1000);
  }
})();
