import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  classifyUrl,
  isWorkRelatedPage,
  classifyUrlWithContext,
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
