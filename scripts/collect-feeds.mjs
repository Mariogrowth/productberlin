// Publisher-feed collector for the weekly news, run by GitHub Actions after the news collector.
// 1. Ask the Worker which feeds to read and which items it already has (GET /api/internal/feeds).
// 2. Read each feed, paging back over the Worker's window (deeper with FEED_BACKFILL_PAGES), and keep only new items.
// 3. Translate non-English titles with DeepL when DEEPL_API_KEY is set.
// 4. Post the items (POST). The Worker validates them; the news judges them like Google News articles (catalogue
//    names in the headline, keyword categories).
import { pathToFileURL } from 'node:url';
import { parseRssXml } from '../api-worker/rss-parser.mjs';
import { translateHeadlines } from './collect-news.mjs';

/** The same article linked with tracking parameters or fragments is one item (as the Worker computes it). */
export const itemKey = link => link.split('#')[0].split('?')[0].replace(/\/+$/, '');

const decode = s => s.replaceAll('&amp;', '&').replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&quot;', '"')
  .replaceAll('&#039;', "'").replaceAll('&#8217;', '’').replaceAll('&#8216;', '‘').replaceAll('&#8220;', '“').replaceAll('&#8221;', '”')
  .replaceAll('&#8211;', '–').replaceAll('&#8212;', '—').replaceAll('&nbsp;', ' ').replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)));
const text = value => decode(String(typeof value === 'object' && value !== null ? value['#text'] ?? '' : value ?? ''))
  .replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();

/** A parsed feed's items as { feed, title, link, publishedAt, teaser }; items without a usable link or date are skipped. */
export function feedItems(parsed, feed) {
  return (parsed?.rss?.channel?.item ?? []).flatMap(item => {
    const link = text(item.link) || text(item.guid);
    const time = Date.parse(text(item.pubDate));
    const title = text(item.title);
    if (!/^https:\/\//.test(link) || Number.isNaN(time) || !title) return [];
    const teaser = text(item['content:encoded'] || item.description).slice(0, 1500);
    return [{ feed: feed.id, title: title.slice(0, 300), link, publishedAt: new Date(time).toISOString(), teaser }];
  });
}

/**
 * Reads each feed's first `feed.pages` pages; `backfillPages` reads deeper for a one-off backfill of the Worker's whole
 * window (paging still stops once a page is older than the window), pausing between pages so publishers do not
 * rate-limit it.
 */
export async function collectFeeds({
  baseUrl, token, deeplApiKey, backfillPages = 0, pageDelay = backfillPages ? 3_000 : 0, fetchImpl = fetch, log = console.log,
}) {
  if (!baseUrl || !/^https?:\/\//.test(baseUrl)) throw new Error('Set COLLECTOR_URL to the site origin, e.g. https://product.berlin');
  if (!token) throw new Error('Set COLLECTOR_TOKEN');
  const endpoint = new URL('/api/internal/feeds', baseUrl);
  const headers = { Authorization: `Bearer ${token}` };
  const planResponse = await fetchImpl(endpoint, { headers });
  if (!planResponse.ok) throw new Error(`Feed plan request failed: HTTP ${planResponse.status} ${await planResponse.text()}`);
  const plan = await planResponse.json();
  const known = new Set(plan.known);
  const since = Date.parse(plan.since);

  const found = new Map();
  let read = 0;
  for (const feed of plan.feeds) {
    for (let page = 1; page <= Math.max(feed.pages, backfillPages); page++) {
      if (page > 1 && pageDelay) await new Promise(resolve => setTimeout(resolve, pageDelay));
      const url = page === 1 ? feed.url : `${feed.url}${feed.url.includes('?') ? '&' : '?'}paged=${page}`;
      try {
        const response = await fetchImpl(url, {
          headers: { 'User-Agent': 'ProductBerlinNews/1.0 (+https://product.berlin)', Accept: 'application/rss+xml, application/xml' },
          signal: AbortSignal.timeout(20_000),
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const items = feedItems(parseRssXml(await response.text()), feed);
        read += items.length;
        for (const item of items) {
          const key = itemKey(item.link);
          if (Date.parse(item.publishedAt) >= since && !known.has(key) && !found.has(key)) found.set(key, item);
        }
        // Older pages only matter while this page still reaches into the window.
        if (!items.length || items.every(item => Date.parse(item.publishedAt) < since)) break;
      } catch (error) {
        log(`${feed.name} page ${page}: ${error.message}`);
        break;
      }
    }
  }
  const items = [...found.values()];
  log(`Read ${read} feed items; ${items.length} new.`);
  if (!items.length) return { read, new: 0, stored: 0 };

  const translations = deeplApiKey ? await translateHeadlines(items.map(item => item.title), { apiKey: deeplApiKey, fetchImpl, log }) : new Map();
  let stored = 0;
  for (let i = 0; i < items.length; i += 100) {
    const chunk = items.slice(i, i + 100).map(({ feed, title, link, publishedAt }) => ({ feed, title, link, publishedAt, translatedTitle: translations.get(title) }));
    const response = await fetchImpl(endpoint, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ items: chunk }),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(`Storing feed items failed: HTTP ${response.status} ${body.error ?? ''}`.trim());
    stored += body.stored ?? 0;
  }
  log(`Stored ${stored} items.`);
  return { read, new: items.length, stored };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const result = await collectFeeds({
    baseUrl: process.env.COLLECTOR_URL,
    token: process.env.COLLECTOR_TOKEN,
    deeplApiKey: process.env.DEEPL_API_KEY,
    backfillPages: Math.min(Number(process.env.FEED_BACKFILL_PAGES) || 0, 30),
  });
  if (process.env.GITHUB_STEP_SUMMARY) {
    const { appendFileSync } = await import('node:fs');
    appendFileSync(process.env.GITHUB_STEP_SUMMARY,
      `### Publisher feeds\n\nRead ${result.read} items, ${result.new} new, ${result.stored} stored.\n`);
  }
}
