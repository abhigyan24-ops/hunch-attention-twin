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
