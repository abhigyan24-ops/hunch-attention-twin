// Hunch Content Script: Measures activity metrics and intercepts rabbit-hole drift.
// Dual-use study mode, surgical YouTube Shorts filter, and IDE Code-Along synchronization.
// Privacy: Zero document text, titles, URLs or search strings are emitted in telemetry.
// Tab parking only transmits the user's explicitly selected parked tab to the local sidecar.

(function () {
  let scrollCount = 0;
  let activeDwellSeconds = 0;
  let bannerShown = false;
  let snoozeUntil = 0;
  let studyAllowedUntil = 0;
  let lastContextFetchTime = 0;
  let cachedIsCodingRecently = false;
  let cachedIsStudyMode = false;
  let activeWorkspaceName = 'code workspace';

  const sessionWorkAllowlist = new Set();

  const TECHNICAL_SUBREDDITS = [
    'r/programming', 'r/rust', 'r/typescript', 'r/javascript', 'r/reactjs', 'r/node',
    'r/python', 'r/learnpython', 'r/cpp', 'r/java', 'r/androiddev', 'r/kotlin',
    'r/golang', 'r/devops', 'r/docker', 'r/kubernetes', 'r/linux', 'r/neovim',
    'r/vim', 'r/vscode', 'r/cscareerquestions', 'r/experienceddevs', 'r/machinelearning',
    'r/localllama', 'r/artificial', 'r/compsci', 'r/algorithms', 'r/leetcode',
    'r/askcomputer', 'r/gate', 'r/indianacademia', 'r/engineeringstudents', 'r/webdev',
    'r/datascience', 'r/coding', 'r/learnprogramming'
  ];

  const EDUCATIONAL_TITLE_KEYWORDS = [
    'tutorial', 'lecture', 'course', 'crash course', 'programming', 'algorithm',
    'data structures', 'dsa', 'system design', 'distributed systems', 'operating systems',
    'dbms', 'compiler', 'machine learning', 'deep learning', 'neural network',
    'math', 'calculus', 'linear algebra', 'probability', 'physics', 'engineering',
    'gate', 'nptel', 'mit', 'stanford', 'harvard', 'cs50', 'leetcode', 'codeforces',
    'python', 'rust', 'typescript', 'javascript', 'react', 'nextjs', 'android',
    'kotlin', 'java', 'c++', 'golang', 'docker', 'kubernetes', 'aws', 'linux',
    'git', 'debugging', 'documentation', 'conference', 'keynote', 'walkthrough',
    'refactoring', 'deep dive', 'benchmark', 'stack trace', 'how to', 'api'
  ];

  const currentHostname = (window.location.hostname || '').toLowerCase();
  const isYouTube = currentHostname.includes('youtube.com') || currentHostname === 'youtu.be';
  const isReddit = currentHostname.includes('reddit.com');
  const isPureSocial = [
    'instagram.com', 'tiktok.com', 'x.com', 'twitter.com', 'facebook.com', 'netflix.com'
  ].some(d => currentHostname.endsWith(d));

  const isMonitoredSurface = isYouTube || isReddit || isPureSocial;

  function matchesEducationalTitle(title = '', urlStr = '') {
    const combined = ((title || '') + ' ' + (urlStr || '')).toLowerCase();
    return EDUCATIONAL_TITLE_KEYWORDS.some(kw => combined.includes(kw));
  }

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

  function getVideoCurrentTimestampSeconds() {
    const video = document.querySelector('video');
    if (video && !isNaN(video.currentTime) && video.currentTime > 0) {
      return Math.floor(video.currentTime);
    }
    return 0;
  }

  function formatTime(totalSeconds) {
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${mins}:${String(secs).padStart(2, '0')}`;
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

  // ── Engine Context Synchronization (Code-Along & Study Mode) ───────────

  async function syncEngineContext() {
    const now = Date.now();
    if (now - lastContextFetchTime < 10000) return; // cache for 10 seconds
    lastContextFetchTime = now;

    try {
      const res = await fetch('http://127.0.0.1:8765/v1/flow/active-context');
      if (res.ok) {
        const data = await res.json();
        cachedIsCodingRecently = Boolean(data.is_coding_recently);
        cachedIsStudyMode = Boolean(data.study_mode_active);
      }
    } catch (_) {
      // Fallback to active-coding if active-context is unavailable
      try {
        const legacyRes = await fetch('http://127.0.0.1:8765/v1/flow/active-coding');
        if (legacyRes.ok) {
          const data = await legacyRes.json();
          cachedIsCodingRecently = Boolean(data && data.is_coding);
          if (data && data.active_workspace) {
            activeWorkspaceName = data.active_workspace;
          }
        }
      } catch (_) {}
    }
  }

  // ── Surgical Page Classifier ──────────────────────────────────────────

  function classifyCurrentPage() {
    const url = window.location.href;
    const pathname = (window.location.pathname || '').toLowerCase();
    const title = document.title || '';

    // Active session whitelist or active study mode
    if (
      Date.now() < studyAllowedUntil ||
      cachedIsStudyMode ||
      sessionWorkAllowlist.has(url)
    ) {
      return 'PRODUCTIVE_STUDY';
    }

    // YouTube Classification
    if (isYouTube) {
      if (pathname.startsWith('/shorts')) {
        return 'HIGH_DOPAMINE_SHORTS';
      }
      if (pathname.startsWith('/watch') || pathname.startsWith('/playlist')) {
        if (cachedIsCodingRecently || matchesEducationalTitle(title, url)) {
          return 'PRODUCTIVE_STUDY';
        }
        return 'ASK_STUDY_CONFIRMATION';
      }
      if (pathname.startsWith('/results') || pathname.includes('/channel/') || pathname.includes('/@')) {
        return 'PRODUCTIVE_STUDY';
      }
      return 'PURE_SOCIAL_DRIFT';
    }

    // Reddit Classification
    if (isReddit) {
      if (pathname.includes('/r/')) {
        const isTech = TECHNICAL_SUBREDDITS.some(sub => pathname.includes('/' + sub));
        if (isTech || cachedIsCodingRecently || matchesEducationalTitle(title, url)) {
          return 'PRODUCTIVE_STUDY';
        }
        return 'PURE_SOCIAL_DRIFT';
      }
      return 'PURE_SOCIAL_DRIFT';
    }

    // Pure Social
    if (isPureSocial) {
      return 'PURE_SOCIAL_DRIFT';
    }

    return 'ALWAYS_ALLOWED';
  }

  // ── Drift Evaluation Loop ─────────────────────────────────────────────

  async function evaluateDrift() {
    if (!isMonitoredSurface || bannerShown || Date.now() < snoozeUntil) {
      return;
    }

    if (document.visibilityState === 'visible') {
      activeDwellSeconds += 1;
    } else {
      return;
    }

    await syncEngineContext();
    const category = classifyCurrentPage();

    if (category === 'PRODUCTIVE_STUDY' || category === 'ALWAYS_ALLOWED') {
      return; // 100% silent during productive work or study
    }

    // 1. YouTube Shorts: 90s threshold
    if (category === 'HIGH_DOPAMINE_SHORTS' && activeDwellSeconds >= 90) {
      showShortsInterceptionBanner();
      return;
    }

    // 2. Ambiguous YouTube Long-form Watch: 480s (8 minutes) choice pill
    if (category === 'ASK_STUDY_CONFIRMATION' && activeDwellSeconds >= 480) {
      showStudyChoicePill();
      return;
    }

    // 3. Pure Social Drift: 240s (4 minutes) with recent coding
    if (category === 'PURE_SOCIAL_DRIFT' && activeDwellSeconds >= 240 && cachedIsCodingRecently) {
      showParkingBanner(activeWorkspaceName);
      return;
    }
  }

  // ── UI Components (Non-Blocking & Glassmorphism) ───────────────────────

  function injectKeyframesOnce() {
    if (document.getElementById('hunch-ui-keyframes')) return;
    const style = document.createElement('style');
    style.id = 'hunch-ui-keyframes';
    style.textContent = `
      @keyframes hunchSlideDown {
        from { transform: translate(-50%, -100%); opacity: 0; }
        to { transform: translate(-50%, 0); opacity: 1; }
      }
      @keyframes hunchSlideInRight {
        from { transform: translateX(120%); opacity: 0; }
        to { transform: translateX(0); opacity: 1; }
      }
      @keyframes hunchFadeOut {
        from { opacity: 1; }
        to { opacity: 0; }
      }
    `;
    document.head.appendChild(style);
  }

  function showShortsInterceptionBanner() {
    if (bannerShown) return;
    bannerShown = true;
    injectKeyframesOnce();

    const banner = document.createElement('div');
    banner.id = 'hunch-shorts-interceptor';
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
      box-shadow: 0 12px 30px rgba(0, 0, 0, 0.45), 0 0 0 1px rgba(255, 255, 255, 0.12);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 13px;
      display: flex;
      align-items: center;
      gap: 12px;
      animation: hunchSlideDown 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    `;

    const label = document.createElement('span');
    label.innerHTML = `<strong>Hunch Flow:</strong> Shorts loop detected — Jump back to your task or switch to long-form search?`;

    const searchBtn = document.createElement('button');
    searchBtn.textContent = 'Switch to Search';
    searchBtn.style.cssText = `
      background: #6366f1;
      color: #ffffff;
      border: none;
      padding: 6px 14px;
      border-radius: 9999px;
      font-weight: 600;
      font-size: 12px;
      cursor: pointer;
    `;
    searchBtn.onclick = () => {
      window.location.href = 'https://www.youtube.com/results?search_query=programming';
    };

    const parkBtn = document.createElement('button');
    parkBtn.textContent = 'Park for Break';
    parkBtn.style.cssText = `
      background: rgba(255, 255, 255, 0.1);
      color: #e4e4e7;
      border: 1px solid rgba(255, 255, 255, 0.15);
      padding: 6px 14px;
      border-radius: 9999px;
      font-size: 12px;
      cursor: pointer;
    `;
    parkBtn.onclick = async () => {
      await parkTabWithResumeTime();
      banner.remove();
      bannerShown = false;
    };

    const dismissBtn = document.createElement('button');
    dismissBtn.textContent = '✕';
    dismissBtn.style.cssText = `
      background: transparent;
      color: #a1a1aa;
      border: none;
      font-size: 14px;
      cursor: pointer;
      padding: 2px 6px;
    `;
    dismissBtn.onclick = () => {
      snoozeUntil = Date.now() + 15 * 60 * 1000;
      banner.remove();
      bannerShown = false;
    };

    banner.appendChild(label);
    banner.appendChild(searchBtn);
    banner.appendChild(parkBtn);
    banner.appendChild(dismissBtn);
    document.body.appendChild(banner);
  }

  function showStudyChoicePill() {
    if (bannerShown) return;
    bannerShown = true;
    injectKeyframesOnce();

    const pill = document.createElement('div');
    pill.id = 'hunch-study-choice-pill';
    pill.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      z-index: 2147483647;
      background: rgba(20, 20, 28, 0.96);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      color: #f4f4f5;
      padding: 12px 18px;
      border-radius: 16px;
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(99, 102, 241, 0.35);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 13px;
      display: flex;
      flex-direction: column;
      gap: 10px;
      animation: hunchSlideInRight 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    `;

    const titleRow = document.createElement('div');
    titleRow.style.cssText = 'display: flex; justify-content: space-between; align-items: center; gap: 12px;';
    titleRow.innerHTML = `<span style="font-weight: 600; color: #a5b4fc;">📚 Studying from this video, or drifting?</span>`;

    const closeBtn = document.createElement('button');
    closeBtn.textContent = '✕';
    closeBtn.style.cssText = 'background: transparent; color: #71717a; border: none; font-size: 13px; cursor: pointer;';
    closeBtn.onclick = () => {
      snoozeUntil = Date.now() + 15 * 60 * 1000;
      pill.remove();
      bannerShown = false;
    };
    titleRow.appendChild(closeBtn);

    const btnRow = document.createElement('div');
    btnRow.style.cssText = 'display: flex; align-items: center; gap: 8px;';

    const studyBtn = document.createElement('button');
    studyBtn.textContent = 'Yes, Studying (Allow 60m)';
    studyBtn.style.cssText = `
      background: #4f46e5;
      color: #ffffff;
      border: none;
      padding: 7px 14px;
      border-radius: 8px;
      font-weight: 600;
      font-size: 12px;
      cursor: pointer;
      transition: background 0.15s;
    `;
    studyBtn.onclick = async () => {
      studyBtn.textContent = 'Enabling...';
      studyBtn.disabled = true;
      try {
        await fetch('http://127.0.0.1:8765/v1/flow/study-mode', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            active: true,
            duration_minutes: 60,
            topic: 'YouTube Study'
          })
        });
      } catch (_) {}

      sessionWorkAllowlist.add(window.location.href);
      studyAllowedUntil = Date.now() + 60 * 60 * 1000;
      cachedIsStudyMode = true;

      pill.innerHTML = `<span style="color: #34d399; font-weight: 500;">✓ Study mode active for 60m. Enjoy your lecture!</span>`;
      setTimeout(() => {
        pill.remove();
        bannerShown = false;
      }, 2500);
    };

    const parkBtn = document.createElement('button');
    const resumeSeconds = getVideoCurrentTimestampSeconds();
    const resumeFormatted = formatTime(resumeSeconds);
    parkBtn.textContent = resumeSeconds > 0 ? `Park Video at ${resumeFormatted}` : 'Park Video for Break';
    parkBtn.style.cssText = `
      background: rgba(255, 255, 255, 0.08);
      color: #e4e4e7;
      border: 1px solid rgba(255, 255, 255, 0.15);
      padding: 7px 12px;
      border-radius: 8px;
      font-size: 12px;
      cursor: pointer;
    `;
    parkBtn.onclick = async () => {
      parkBtn.textContent = 'Saving...';
      parkBtn.disabled = true;
      await parkTabWithResumeTime(resumeSeconds);
      pill.innerHTML = `<span style="color: #a5b4fc; font-weight: 500;">✓ Saved to Hunch break digest (paused at ${resumeFormatted})</span>`;
      setTimeout(() => {
        pill.remove();
        bannerShown = false;
        try {
          window.close();
        } catch (_) {}
      }, 2000);
    };

    btnRow.appendChild(studyBtn);
    btnRow.appendChild(parkBtn);
    pill.appendChild(titleRow);
    pill.appendChild(btnRow);
    document.body.appendChild(pill);
  }

  async function parkTabWithResumeTime(seconds) {
    const resumeTimestampSeconds = typeof seconds === 'number' ? seconds : getVideoCurrentTimestampSeconds();
    try {
      await fetch('http://127.0.0.1:8765/v1/flow/park-tab', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          url: window.location.href,
          title: document.title || currentHostname,
          from_workspace: activeWorkspaceName,
          resumeTimestampSeconds: resumeTimestampSeconds
        })
      });
    } catch (_) {}
  }

  function showParkingBanner(workspaceName) {
    if (bannerShown) return;
    bannerShown = true;
    injectKeyframesOnce();

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
    parkBtn.onclick = async () => {
      parkBtn.textContent = 'Parking...';
      parkBtn.disabled = true;
      await parkTabWithResumeTime();
      banner.innerHTML = `<span style="color: #34d399; font-weight: 500;">✓ Tab saved to your Hunch break digest. Ready to dive back into ${workspaceName}!</span>`;
      setTimeout(() => {
        banner.remove();
        bannerShown = false;
      }, 3000);
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

    const allowBtn = document.createElement('button');
    allowBtn.textContent = 'Allow for Work';
    allowBtn.style.cssText = `
      background: transparent;
      color: #a1a1aa;
      border: 1px solid rgba(255, 255, 255, 0.2);
      padding: 6px 12px;
      border-radius: 9999px;
      font-size: 11px;
      cursor: pointer;
    `;
    allowBtn.onclick = () => {
      sessionWorkAllowlist.add(window.location.href);
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
    banner.appendChild(allowBtn);
    banner.appendChild(closeBtn);
    document.body.appendChild(banner);
  }

  if (isMonitoredSurface) {
    setInterval(evaluateDrift, 1000);
  }
})();
