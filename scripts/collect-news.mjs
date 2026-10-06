// Weekly news collector, run by GitHub Actions because Google News blocks Cloudflare's servers.
// 1. Ask the Worker for its plan (GET /api/internal/collection): is this week due, and which searches to run.
// 2. Fetch those Google News searches from this machine, paced, retrying failures once after a pause.
// 3. Send the parsed feeds (only the fields the Worker reads) back (POST); the Worker filters, ranks and publishes.
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { parseRssXml } from '../api-worker/rss-parser.mjs';

const publisherDomains = JSON.parse(await readFile(new URL('../cloudflare/publishers.json', import.meta.url), 'utf8')).map(p => p.domain);

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

const trusted = url => {
  const host = /^https?:\/\/([^/:?#]+)/i.exec(url ?? '')?.[1]?.toLowerCase().replace(/\.$/, '') ?? '';
  return publisherDomains.some(d => host === d || host.endsWith(`.${d}`));
};

/**
 * Keeps only in-week items from trusted publishers, and only the fields the Worker's parser reads, so the request
 * stays small and cheap for the Worker (free-plan CPU limits). The Worker re-applies every check itself.
 */
export function compactFeed(feed, { start, end } = {}) {
  const from = start ? Date.parse(start) : -Infinity;
  const to = end ? Date.parse(end) : Infinity;
  const items = (feed?.rss?.channel?.item ?? []).filter(item => {
    const time = Date.parse(item.pubDate);
    return time >= from && time < to && trusted(item.source?.['@_url']);
  });
  return { rss: { channel: { item: items.map(({ title, link, guid, pubDate, source }) => ({ title, link, guid, pubDate, source })) } } };
}

export async function collect({ baseUrl, token, fetchImpl = fetch, pauseMs = 3000, retryPauseMs = 10000, log = console.log }) {
  if (!baseUrl || !/^https?:\/\//.test(baseUrl)) throw new Error('Set COLLECTOR_URL to the site origin, e.g. https://product.berlin');
  if (!token) throw new Error('Set COLLECTOR_TOKEN');
  const endpoint = new URL('/api/internal/collection', baseUrl);
  const headers = { Authorization: `Bearer ${token}` };

  const planResponse = await fetchImpl(endpoint, { headers });
  if (!planResponse.ok) throw new Error(`Plan request failed: HTTP ${planResponse.status} ${await planResponse.text()}`);
  const plan = await planResponse.json();
  if (!plan.due) {
    log(`Nothing to collect (${plan.reason}${plan.collectionKey ? `, ${plan.collectionKey}` : ''}).`);
    return { outcome: plan.reason, failed: 0 };
  }
  log(`Collecting ${plan.collectionKey} (${plan.week}): ${plan.searches.length} searches.`);

  const results = plan.searches.map(search => ({ ...search }));
  const fetchOne = async result => {
    const url = 'https://news.google.com/rss/search?' + new URLSearchParams({
      q: result.query, hl: result.language, gl: 'DE', ceid: `DE:${result.language}`,
    });
    try {
      const response = await fetchImpl(url, { signal: AbortSignal.timeout(20_000) });
      if (!response.ok) throw new Error(`Google News returned HTTP ${response.status}`);
      result.feed = compactFeed(parseRssXml(await response.text()), plan);
      delete result.error;
    } catch (error) {
      result.error = error.message;
    }
  };
  for (const [index, result] of results.entries()) {
    if (index > 0) await sleep(pauseMs);
    await fetchOne(result);
  }
  const failedFirst = results.filter(r => r.error);
  if (failedFirst.length) {
    log(`${failedFirst.length} searches failed; retrying once after ${retryPauseMs / 1000} s.`);
    await sleep(retryPauseMs);
    for (const [index, result] of failedFirst.entries()) {
      if (index > 0) await sleep(pauseMs);
      await fetchOne(result);
    }
  }
  const failed = results.filter(r => r.error).length;
  log(`Fetched ${results.length - failed}/${results.length} searches.`);

  const response = await fetchImpl(endpoint, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify({ collectionKey: plan.collectionKey, results }),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`Collection failed: HTTP ${response.status} ${body.message ?? body.error ?? ''}`.trim());
  log(`Worker: ${body.outcome} (${body.collectionKey}).`);
  return { outcome: body.outcome, failed };
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const result = await collect({
    baseUrl: process.env.COLLECTOR_URL,
    token: process.env.COLLECTOR_TOKEN,
    pauseMs: Number(process.env.COLLECTOR_PAUSE_MS ?? 3000),
    retryPauseMs: Number(process.env.COLLECTOR_RETRY_PAUSE_MS ?? 10000),
  });
  if (process.env.GITHUB_STEP_SUMMARY) {
    const { appendFileSync } = await import('node:fs');
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, `### Weekly news collection: ${result.outcome}\n\nFailed searches after retry: ${result.failed}\n`);
  }
}
