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
  'r/python',
  'r/devops',
  'r/machinelearning',
  'r/neovim',
  'r/golang',
  'r/webdev',
  'r/cpp',
  'r/learnprogramming',
  'r/coding',
  'r/datascience',
  'r/linux'
];

export const WORK_LEARNING_KEYWORDS = [
  'tutorial',
  'lecture',
  'course',
  'documentation',
  'crash course',
  'system design',
  'debugging',
  'stack trace',
  'how to',
  'api',
  'conference',
  'refactoring',
  'deep dive',
  'benchmark',
  'walkthrough'
];

/**
 * Determines whether a URL or page represents productive technical/work activity.
 * Prevents false-positive drift warnings on technical tutorials or engineering subreddits.
 *
 * @param {string} urlStr Full URL of the page.
 * @param {string} pageTitle Document title of the page.
 * @param {string[]} whitelistedUrls User-approved session whitelist URLs.
 * @returns {boolean} True if the page should be classified as Work, false if potential drift.
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
  const titleLower = (pageTitle || '').toLowerCase();

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
    for (const kw of WORK_LEARNING_KEYWORDS) {
      if (titleLower.includes(kw.toLowerCase())) {
        return true;
      }
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
