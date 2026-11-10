// Hunch Content Script: Measures activity metrics and surfaces locally.
// Privacy: Zero document text, titles, URLs or search strings.

(function () {
  let scrollCount = 0;
  let lastReportedTs = Date.now();

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

  // Initial report
  setTimeout(reportMetrics, 500);
})();
