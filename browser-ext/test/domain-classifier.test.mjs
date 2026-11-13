import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  classifyUrl,
  isWorkRelatedPage,
  classifyUrlWithContext,
  classifyBrowserTab,
  TECHNICAL_SUBREDDITS,
  WORK_LEARNING_KEYWORDS
} from '../domain-classifier.js';

test('technical YouTube tutorial is classified as Work learning', () => {
  const url = 'https://www.youtube.com/watch?v=ms9p_1j6j7w';
  const title = 'Rust Async Programming Tutorial - Tokio Deep Dive';
  assert.equal(isWorkRelatedPage(url, title), true);
  assert.equal(classifyUrlWithContext(url, title), 'learning');
});

test('entertainment YouTube video is classified as Drift (not work)', () => {
  const url = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';
  const title = 'Top 10 Funny Fails 2026';
  assert.equal(isWorkRelatedPage(url, title), false);
  assert.equal(classifyUrlWithContext(url, title), 'video');
});

test('technical subreddit is classified as Work learning', () => {
  const url = 'https://www.reddit.com/r/rust/comments/12345/optimizing_simd/';
  const title = 'Optimizing SIMD loops in Rust';
  assert.equal(isWorkRelatedPage(url, title), true);
  assert.equal(classifyUrlWithContext(url, title), 'learning');
});

test('entertainment subreddit is classified as Drift social', () => {
  const url = 'https://www.reddit.com/r/funny/comments/67890/cats_falling/';
  const title = 'Hilarious cat compilation';
  assert.equal(isWorkRelatedPage(url, title), false);
  assert.equal(classifyUrlWithContext(url, title), 'social');
});

test('session allowlist overrides default drift classification', () => {
  const url = 'https://www.youtube.com/watch?v=ambient_music';
  const title = 'Chill Lo-fi Beats';
  assert.equal(isWorkRelatedPage(url, title, ['https://www.youtube.com/watch?v=ambient_music']), true);
  assert.equal(classifyUrlWithContext(url, title, ['https://www.youtube.com/watch?v=ambient_music']), 'learning');
});

test('classifyBrowserTab classifies always-allowed research and docs sites', () => {
  assert.equal(classifyBrowserTab('http://localhost:3000/dashboard'), 'ALWAYS_ALLOWED');
  assert.equal(classifyBrowserTab('https://developer.mozilla.org/en-US/docs/Web/API'), 'ALWAYS_ALLOWED');
  assert.equal(classifyBrowserTab('https://github.com/torvalds/linux'), 'ALWAYS_ALLOWED');
  assert.equal(classifyBrowserTab('https://stackoverflow.com/questions/11227809'), 'ALWAYS_ALLOWED');
  assert.equal(classifyBrowserTab('https://cs.stanford.edu/people/karpathy/'), 'ALWAYS_ALLOWED');
  assert.equal(classifyBrowserTab('https://docs.python.org/3/library/asyncio.html'), 'ALWAYS_ALLOWED');
});

test('classifyBrowserTab honors active study mode and whitelist', () => {
  const url = 'https://www.youtube.com/watch?v=some_random_video';
  assert.equal(classifyBrowserTab(url, 'Random Vlog', false, true), 'PRODUCTIVE_STUDY');
  assert.equal(classifyBrowserTab(url, 'Random Vlog', false, false, [url]), 'PRODUCTIVE_STUDY');
});

test('classifyBrowserTab surgically isolates YouTube Shorts loops', () => {
  const url = 'https://www.youtube.com/shorts/3iZk3p';
  // Even with educational title or coding, Shorts are flagged as high-dopamine
  assert.equal(classifyBrowserTab(url, 'Learn Python in 15s #shorts', true), 'HIGH_DOPAMINE_SHORTS');
  assert.equal(classifyBrowserTab(url, 'Funny dance #shorts', false), 'HIGH_DOPAMINE_SHORTS');
});

test('classifyBrowserTab distinguishes educational vs ambiguous YouTube watch', () => {
  const watchUrl = 'https://www.youtube.com/watch?v=Vw9L0B_Rk';
  // Educational keyword in title -> PRODUCTIVE_STUDY
  assert.equal(classifyBrowserTab(watchUrl, 'Distributed Systems Lecture 1: Introduction'), 'PRODUCTIVE_STUDY');
  // Recent IDE coding -> PRODUCTIVE_STUDY (Code-Along)
  assert.equal(classifyBrowserTab(watchUrl, 'Random Ambient Synth', true), 'PRODUCTIVE_STUDY');
  // Ambiguous passive viewing without coding -> ASK_STUDY_CONFIRMATION
  assert.equal(classifyBrowserTab(watchUrl, 'Top 10 Movie Bloopers', false), 'ASK_STUDY_CONFIRMATION');
  // YouTube search results or channels -> PRODUCTIVE_STUDY
  assert.equal(classifyBrowserTab('https://www.youtube.com/results?search_query=rust+wasm'), 'PRODUCTIVE_STUDY');
});

test('classifyBrowserTab surgically handles Reddit subreddits and coding horizon', () => {
  const techUrl = 'https://www.reddit.com/r/programming/comments/987/release';
  assert.equal(classifyBrowserTab(techUrl, 'New Compiler Architecture'), 'PRODUCTIVE_STUDY');

  const memeUrl = 'https://www.reddit.com/r/memes/comments/abc/funny';
  // Without coding -> PURE_SOCIAL_DRIFT
  assert.equal(classifyBrowserTab(memeUrl, 'Funny dog', false), 'PURE_SOCIAL_DRIFT');
  // With active coding -> PRODUCTIVE_STUDY
  assert.equal(classifyBrowserTab(memeUrl, 'Funny dog', true), 'PRODUCTIVE_STUDY');
});

test('classifyBrowserTab flags pure dopamine social feeds', () => {
  assert.equal(classifyBrowserTab('https://www.instagram.com/reels/xyz/'), 'PURE_SOCIAL_DRIFT');
  assert.equal(classifyBrowserTab('https://www.tiktok.com/@user/video/123'), 'PURE_SOCIAL_DRIFT');
  assert.equal(classifyBrowserTab('https://twitter.com/home'), 'PURE_SOCIAL_DRIFT');
  assert.equal(classifyBrowserTab('https://x.com/explore'), 'PURE_SOCIAL_DRIFT');
});
