/**
 * Maps hostname patterns to one of the 14 Dhyan taxonomy categories.
 * Strict privacy: returns ONLY the category string. Never leaks domain or URL.
 */

const DOMAIN_MAP = {
  // coding
  'github.com': 'coding',
  'gitlab.com': 'coding',
  'stackoverflow.com': 'coding',
  'stackexchange.com': 'coding',
  'npmjs.com': 'coding',
  'pypi.org': 'coding',
  'leetcode.com': 'coding',
  'codepen.io': 'coding',
  'replit.com': 'coding',

  // docs
  'developer.mozilla.org': 'docs',
  'docs.google.com': 'docs',
  'notion.so': 'docs',
  'wikipedia.org': 'docs',
  'w3schools.com': 'docs',
  'readthedocs.io': 'docs',

  // communication
  'slack.com': 'communication',
  'discord.com': 'communication',
  'teams.microsoft.com': 'communication',
  'web.whatsapp.com': 'communication',
  'web.telegram.org': 'communication',
  'mail.google.com': 'communication',
  'outlook.com': 'communication',

  // social
  'x.com': 'social',
  'twitter.com': 'social',
  'reddit.com': 'social',
  'instagram.com': 'social',
  'facebook.com': 'social',
  'linkedin.com': 'social',
  'threads.net': 'social',

  // video & short_video
  'youtube.com': 'video',
  'netflix.com': 'video',
  'twitch.tv': 'video',
  'hulu.com': 'video',
  'disneyplus.com': 'video',
  'primevideo.com': 'video',
  'tiktok.com': 'short_video',

  // games
  'store.steampowered.com': 'games',
  'steamcommunity.com': 'games',
  'roblox.com': 'games',
  'epicgames.com': 'games',
  'chess.com': 'games',

  // shopping
  'amazon.com': 'shopping',
  'amazon.in': 'shopping',
  'flipkart.com': 'shopping',
  'ebay.com': 'shopping',
  'aliexpress.com': 'shopping',

  // news
  'news.ycombinator.com': 'news',
  'bbc.com': 'news',
  'nytimes.com': 'news',
  'cnn.com': 'news',
  'reuters.com': 'news',

  // music
  'spotify.com': 'music',
  'soundcloud.com': 'music',
  'music.apple.com': 'music',
  'music.youtube.com': 'music',

  // learning
  'coursera.org': 'learning',
  'edx.org': 'learning',
  'udemy.com': 'learning',
  'khanacademy.org': 'learning',

  // utility & system
  'calendar.google.com': 'utility',
  'drive.google.com': 'utility',
  'localhost': 'utility'
};

export function classifyUrl(urlStr) {
  if (!urlStr || typeof urlStr !== 'string') return 'unknown';

  let hostname = '';
  try {
    const parsed = new URL(urlStr);
    hostname = parsed.hostname.toLowerCase();
  } catch (_) {
    return 'unknown';
  }

  // Exact match
  if (DOMAIN_MAP[hostname]) {
    return DOMAIN_MAP[hostname];
  }

  // Subdomain suffix match (e.g. sub.github.com -> github.com)
  for (const [pattern, category] of Object.entries(DOMAIN_MAP)) {
    if (hostname.endsWith('.' + pattern)) {
      return category;
    }
  }

  return 'unknown';
}

export const TECHNICAL_SUBREDDITS = [
  'r/programming',
  'r/rust',
  'r/typescript',
  'r/javascript',
  'r/reactjs',
  'r/node',
  'r/python',
  'r/learnpython',
  'r/cpp',
  'r/java',
  'r/androiddev',
  'r/kotlin',
  'r/golang',
  'r/devops',
  'r/docker',
  'r/kubernetes',
  'r/linux',
  'r/neovim',
  'r/vim',
  'r/vscode',
  'r/cscareerquestions',
  'r/experienceddevs',
  'r/machinelearning',
  'r/localllama',
  'r/artificial',
  'r/compsci',
  'r/algorithms',
  'r/leetcode',
  'r/askcomputer',
  'r/gate',
  'r/indianacademia',
  'r/engineeringstudents',
  'r/webdev',
  'r/datascience',
  'r/coding',
  'r/learnprogramming'
];

export const WORK_LEARNING_KEYWORDS = [
  'tutorial',
  'lecture',
  'course',
  'crash course',
  'programming',
  'algorithm',
  'data structures',
  'dsa',
  'system design',
  'distributed systems',
  'operating systems',
  'dbms',
  'compiler',
  'machine learning',
  'deep learning',
  'neural network',
  'math',
  'calculus',
  'linear algebra',
  'probability',
  'physics',
  'engineering',
  'gate',
  'nptel',
  'mit',
  'stanford',
  'harvard',
  'cs50',
  'leetcode',
  'codeforces',
  'python',
  'rust',
  'typescript',
  'javascript',
  'react',
  'nextjs',
  'android',
  'kotlin',
  'java',
  'c++',
  'golang',
  'docker',
  'kubernetes',
  'aws',
  'linux',
  'git',
  'debugging',
  'documentation',
  'conference',
  'keynote',
  'walkthrough',
  'refactoring',
  'deep dive',
  'benchmark',
  'stack trace',
  'how to',
  'api'
];

export const EDUCATIONAL_TITLE_KEYWORDS = WORK_LEARNING_KEYWORDS;

export function matchesEducationalTitle(title = '', urlStr = '') {
  const combined = ((title || '') + ' ' + (urlStr || '')).toLowerCase();
  return EDUCATIONAL_TITLE_KEYWORDS.some(kw => combined.includes(kw.toLowerCase()));
}

/**
 * Determines whether a URL or page represents productive technical/work activity.
 */
export function isWorkRelatedPage(urlStr, pageTitle = '', whitelistedUrls = []) {
  if (!urlStr || typeof urlStr !== 'string') return false;

  // 1. Session-level user whitelist check
  if (Array.isArray(whitelistedUrls)) {
    for (const allowed of whitelistedUrls) {
      if (allowed && (urlStr.startsWith(allowed) || allowed.startsWith(urlStr))) {
        return true;
      }
    }
  }

  let parsed;
  try {
    parsed = new URL(urlStr);
  } catch (_) {
    return false;
  }

  const hostname = parsed.hostname.toLowerCase();
  const pathname = parsed.pathname.toLowerCase();

  // 2. Technical subreddit check
  if (hostname.includes('reddit.com')) {
    for (const sub of TECHNICAL_SUBREDDITS) {
      if (pathname.includes('/' + sub.toLowerCase())) {
        return true;
      }
    }
  }

  // 3. Technical & educational video/article check
  if (hostname.includes('youtube.com') || hostname.includes('reddit.com')) {
    if (matchesEducationalTitle(pageTitle, urlStr)) {
      return true;
    }
  }

  return false;
}

/**
 * Enhanced classifier that takes page context into account for work-aware categorization.
 */
export function classifyUrlWithContext(urlStr, pageTitle = '', whitelistedUrls = []) {
  if (isWorkRelatedPage(urlStr, pageTitle, whitelistedUrls)) {
    return 'learning';
  }
  return classifyUrl(urlStr);
}

/**
 * Surgical classification of browser tabs for dual-use study mode, Shorts filter,
 * and Code-Along IDE synchronization.
 *
 * @param {string} urlStr Full URL of the page.
 * @param {string} pageTitle Document title of the page.
 * @param {boolean} isCodingActiveRecently Whether user actively coded in VS Code within 180s.
 * @param {boolean} isStudyModeActive Whether study pass is active.
 * @param {Set<string>|Array<string>} sessionWhitelistedUrls User-approved session whitelist.
 * @returns {'HIGH_DOPAMINE_SHORTS'|'PRODUCTIVE_STUDY'|'ASK_STUDY_CONFIRMATION'|'PURE_SOCIAL_DRIFT'|'ALWAYS_ALLOWED'}
 */
export function classifyBrowserTab(
  urlStr,
  pageTitle = '',
  isCodingActiveRecently = false,
  isStudyModeActive = false,
  sessionWhitelistedUrls = []
) {
  if (!urlStr || typeof urlStr !== 'string') return 'ALWAYS_ALLOWED';

  let parsed;
  try {
    parsed = new URL(urlStr);
  } catch (_) {
    return 'ALWAYS_ALLOWED';
  }

  const hostname = parsed.hostname.toLowerCase();
  const pathname = parsed.pathname.toLowerCase();

  // Tier 1: Always-Allowed Research, Documentation & Dev Environments
  const ALWAYS_ALLOWED_HOSTS = [
    'localhost',
    '127.0.0.1',
    'github.com',
    'gitlab.com',
    'stackoverflow.com',
    'stackexchange.com',
    'developer.mozilla.org',
    'arxiv.org',
    'leetcode.com',
    'codeforces.com',
    'pypi.org',
    'npmjs.com'
  ];
  if (
    ALWAYS_ALLOWED_HOSTS.some(h => hostname === h || hostname.endsWith('.' + h)) ||
    hostname.endsWith('.edu') ||
    hostname.endsWith('.ac.in') ||
    hostname.startsWith('docs.') ||
    hostname.startsWith('developer.')
  ) {
    return 'ALWAYS_ALLOWED';
  }

  // User-approved session whitelist or active global study mode
  const whitelistList = Array.isArray(sessionWhitelistedUrls)
    ? sessionWhitelistedUrls
    : Array.from(sessionWhitelistedUrls || []);
  if (
    isStudyModeActive ||
    whitelistList.some(allowed => allowed && (urlStr.startsWith(allowed) || allowed.startsWith(urlStr)))
  ) {
    return 'PRODUCTIVE_STUDY';
  }

  // Tier 2: YouTube Surgical Classification
  if (hostname.includes('youtube.com') || hostname === 'youtu.be') {
    // 1. YouTube Shorts loop
    if (pathname.startsWith('/shorts')) {
      return 'HIGH_DOPAMINE_SHORTS';
    }

    // 2. Long-form video or playlist
    if (pathname.startsWith('/watch') || pathname.startsWith('/playlist')) {
      if (isCodingActiveRecently || matchesEducationalTitle(pageTitle, urlStr)) {
        return 'PRODUCTIVE_STUDY';
      }
      return 'ASK_STUDY_CONFIRMATION';
    }

    // Search results or channel pages during research
    if (pathname.startsWith('/results') || pathname.includes('/channel/') || pathname.includes('/@')) {
      return 'PRODUCTIVE_STUDY';
    }

    // Root home feed
    return 'PURE_SOCIAL_DRIFT';
  }

  // Tier 2: Reddit Subreddit Classification
  if (hostname.includes('reddit.com')) {
    if (pathname.includes('/r/')) {
      const isTechSubreddit = TECHNICAL_SUBREDDITS.some(sub => pathname.includes('/' + sub.toLowerCase()));
      if (isTechSubreddit || isCodingActiveRecently || matchesEducationalTitle(pageTitle, urlStr)) {
        return 'PRODUCTIVE_STUDY';
      }
      return 'PURE_SOCIAL_DRIFT';
    }
    // Root reddit feed
    return 'PURE_SOCIAL_DRIFT';
  }

  // Tier 3: Pure Social Feeds
  const PURE_SOCIAL_DOMAINS = [
    'instagram.com',
    'tiktok.com',
    'x.com',
    'twitter.com',
    'facebook.com',
    'netflix.com'
  ];
  if (PURE_SOCIAL_DOMAINS.some(d => hostname.endsWith(d))) {
    return 'PURE_SOCIAL_DRIFT';
  }

  return 'ALWAYS_ALLOWED';
}
