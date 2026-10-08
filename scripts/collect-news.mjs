// Weekly news collector, run by GitHub Actions because Google News blocks Cloudflare's servers.
// 1. Ask the Worker for its plan (GET /api/internal/collection): is this week due, and which searches to run.
// 2. Fetch those Google News searches from this machine, paced, retrying failures once after a pause.
// 3. Translate non-English headlines to English with DeepL (company names protected), if DEEPL_API_KEY is set.
// 4. Send the parsed feeds (only the fields the Worker reads) back (POST); the Worker filters, ranks and publishes.
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { parseRssXml } from '../api-worker/rss-parser.mjs';

const publisherDomains = JSON.parse(await readFile(new URL('../cloudflare/publishers.json', import.meta.url), 'utf8')).map(p => p.domain);
const catalogue = JSON.parse(await readFile(new URL('../cloudflare/startups.json', import.meta.url), 'utf8'));
/** Every catalogue name and alias, longest first, so "Nox Mobility" is protected as a whole before "Nox". */
const companyNames = [...new Set(catalogue.flatMap(c => [c.name, ...c.aliases]).map(n => n.trim()).filter(n => n.length > 1))]
  .sort((a, b) => b.length - a.length);

const escapeXml = s => s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const unescapeXml = s => s.replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&quot;', '"').replaceAll('&apos;', "'").replaceAll('&amp;', '&');
const escapeRegex = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** Wraps whole-word company names in <x> tags, which DeepL is told not to translate; the rest is XML-escaped. */
export function protectNames(text, names = companyNames) {
  const spans = [];
  for (const name of names) {
    const pattern = new RegExp(`(?<![\\p{L}\\p{N}])${escapeRegex(name)}(?![\\p{L}\\p{N}])`, 'giu');
    for (const match of text.matchAll(pattern)) {
      const start = match.index, end = start + match[0].length;
      if (!spans.some(([a, b]) => start < b && end > a)) spans.push([start, end]);
    }
  }
  spans.sort((a, b) => a[0] - b[0]);
  let out = '', position = 0;
  for (const [start, end] of spans) {
    out += `${escapeXml(text.slice(position, start))}<x>${escapeXml(text.slice(start, end))}</x>`;
    position = end;
  }
  return out + escapeXml(text.slice(position));
}
export const unprotectNames = xml => unescapeXml(xml.replace(/<\/?x>/g, ''));

const DECORATION = `(?:[*_'"‘’“”„«»]|&quot;|&apos;)*`;
const QUOTE_LIKE = /[*_'"‘’“”„«»]/u;

/**
 * DeepL sometimes decorates a protected name it was told not to translate ("the end of *Zalando*", "'Langdock'").
 * Removes asterisks, underscores and quotes wrapped around a name unless the original headline had them there too.
 */
export function restoreNames(xml, original) {
  const cleaned = xml.replace(new RegExp(`(${DECORATION})<x>([^<]*)</x>(${DECORATION})`, 'gu'), (match, before, name, after) => {
    if (!before && !after) return match;
    const plain = unescapeXml(name);
    const index = original.indexOf(plain);
    const decorated = index >= 0 && (QUOTE_LIKE.test(original[index - 1] ?? '') || QUOTE_LIKE.test(original[index + plain.length] ?? ''));
    return decorated ? match : `<x>${name}</x>`;
  });
  return unprotectNames(cleaned);
}

/** The headline exactly as the Worker derives it: the RSS title without its " - Publisher" suffix. */
export function headlineOf(item) {
  const source = String(typeof item.source === 'object' ? item.source?.['#text'] ?? '' : item.source ?? '').trim();
  const title = String(item.title ?? '').trim();
  return (source && title.endsWith(` - ${source}`) ? title.slice(0, -(source.length + 3)) : title).trim();
}

/**
 * Translates headlines to English with DeepL, keeping company names unchanged. Headlines DeepL detects as English are
 * left out. Never throws: on any DeepL failure the remaining headlines simply stay untranslated.
 */
export async function translateHeadlines(headlines, { apiKey, fetchImpl = fetch, log = console.log } = {}) {
  const translations = new Map();
  if (!apiKey || !headlines.length) return translations;
  const endpoint = `${apiKey.endsWith(':fx') ? 'https://api-free.deepl.com' : 'https://api.deepl.com'}/v2/translate`;
  for (let i = 0; i < headlines.length; i += 50) {
    const batch = headlines.slice(i, i + 50);
    try {
      const response = await fetchImpl(endpoint, {
        method: 'POST',
        headers: { Authorization: `DeepL-Auth-Key ${apiKey}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: batch.map(h => protectNames(h)), target_lang: 'EN-GB', tag_handling: 'xml', ignore_tags: ['x'] }),
        signal: AbortSignal.timeout(20_000),
      });
      if (!response.ok) throw new Error(`DeepL returned HTTP ${response.status}`);
      const { translations: results = [] } = await response.json();
      results.forEach((result, index) => {
        const english = restoreNames(String(result.text ?? ''), batch[index]).trim();
        if (result.detected_source_language !== 'EN' && english && english !== batch[index]) translations.set(batch[index], english);
      });
    } catch (error) {
      log(`Translation skipped for ${batch.length} headlines: ${error.message}`);
    }
  }
  return translations;
}

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

export async function collect({ baseUrl, token, deeplApiKey, fetchImpl = fetch, pauseMs = 3000, retryPauseMs = 10000, log = console.log }) {
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

  const items = results.flatMap(r => r.feed?.rss?.channel?.item ?? []);
  if (deeplApiKey) {
    const translations = await translateHeadlines([...new Set(items.map(headlineOf))], { apiKey: deeplApiKey, fetchImpl, log });
    for (const item of items) {
      const english = translations.get(headlineOf(item));
      if (english) item.translatedTitle = english;
    }
    log(`Translated ${translations.size} headlines to English.`);
  } else {
    log('DEEPL_API_KEY is not set; headlines stay in their original language.');
  }

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

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const result = await collect({
    baseUrl: process.env.COLLECTOR_URL,
    token: process.env.COLLECTOR_TOKEN,
    deeplApiKey: process.env.DEEPL_API_KEY,
    pauseMs: Number(process.env.COLLECTOR_PAUSE_MS ?? 3000),
    retryPauseMs: Number(process.env.COLLECTOR_RETRY_PAUSE_MS ?? 10000),
  });
  if (process.env.GITHUB_STEP_SUMMARY) {
    const { appendFileSync } = await import('node:fs');
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, `### Weekly news collection: ${result.outcome}\n\nFailed searches after retry: ${result.failed}\n`);
  }
}
