// Weekly catalogue suggestions, run by GitHub Actions in the collect workflow (Mondays after the edition).
// The news only covers companies in cloudflare/startups.json, so a Berlin startup missing from it never appears.
// This reads the past week's publisher feeds (cloudflare/feeds.json) and a few Google News searches, finds headlines
// and teasers that name a Berlin startup ("Berliner Startup X", "Berlin-based X", "X: Berliner Fintech …") that is
// not in the catalogue, and lists them in the workflow summary for a person to review. It never edits the catalogue
// and never contacts the Worker.
import { readFileSync, appendFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { parseRssXml } from '../api-worker/rss-parser.mjs';
import { feedItems } from './collect-feeds.mjs';

const DAY = 86_400_000;
// Quoted phrases plus two broad ones for compounds such as "Berliner Logistik-Startup erhält 20 Mio.".
const SEARCHES = ['"Berliner Startup"', '"Berliner Start-up"', '"Berlin-based"', '"Berliner Fintech"', 'Berliner Startup Millionen', 'Berlin startup raises'];

// A company name: one or two capitalised words ("Procuros", "Kuro Engineering", "1Komma5°" is not Berlin anyway).
const NAME = String.raw`([A-Z0-9][\p{L}\p{N}.&°+-]*(?:\s[A-Z][\p{L}\p{N}&+-]*)?)`;
const KIND = String.raw`[\p{L}-]*(?:Startup|Start-up|Fintech|Scaleup|Scale-up|Unternehmen|Plattform|Anbieter)`;
const PATTERNS = [
  new RegExp(String.raw`Berliner(?:\s[\p{L}-]+)?\s${KIND}\s${NAME}`, 'gu'),
  // Only at the start: "Procuros: Berliner Logistik-Startup …", not "Matcha Cookies & Carrot Cake: Berliner Start-up …".
  new RegExp(String.raw`^${NAME}:\s(?:Das\s)?Berliner\s${KIND}`, 'gu'),
  new RegExp(String.raw`Berlin-based\s(?:[\p{L}-]+\s){0,3}?${NAME}`, 'gu'),
  new RegExp(String.raw`Berlin(?:'s|’s| startup)\s${NAME}`, 'gu'),
];
const NOT_A_NAME = new Set(['ai', 'ki', 'the', 'startup', 'start-up', 'fintech', 'company', 'unternehmen', 'mit', 'und', 'für', 'with',
  'and', 'raises', 'erhält', 'sammelt', 'holt', 'secures', 'startet', 'launches', 'platform', 'plattform', 'gmbh', 'founder', 'gründer', 'ceo']);
// "10 Millionen" is an amount and "Cherry Ventures" an investor, not a startup to add.
const AMOUNT_OR_INVESTOR = /^(millionen|million|mio\.?|euro|ventures|capital|partners|vc|fund|fonds)$/;
const EVENT = /(raise|million|mio\b|millionen|€|\$\d|finanzierung|funding|series [a-e]|seed|acqui|übernimm|kauft|launch|startet|insolven)/i;

/** Lowercase names and aliases of the catalogue companies. */
export const catalogueNames = catalogue =>
  new Set(catalogue.flatMap(company => [company.name, ...(company.aliases ?? [])]).map(name => name.toLowerCase()));

/**
 * Berlin startups named in the items that the catalogue lacks, one entry per name with its most newsworthy report:
 * { name, title, source, link, publishedAt, event } sorted with funding/launch/deal news first, then newest.
 */
export function findCandidates(items, known) {
  const found = new Map();
  for (const item of items) {
    for (const text of [item.title, item.teaser ?? '']) {
      for (const pattern of PATTERNS) {
        for (const match of text.matchAll(pattern)) {
          const name = match[1].replace(/[.,:;]+$/, '').trim();
          const words = name.toLowerCase().split(/\s+/);
          if (name.length < 3 || NOT_A_NAME.has(words[0]) || words.some(word => AMOUNT_OR_INVESTOR.test(word))) continue;
          // "Kuro Engineering" is known when "Kuro" is.
          if (known.has(name.toLowerCase()) || known.has(words[0])) continue;
          if (name.toLowerCase() === String(item.source).toLowerCase()) continue;
          const candidate = { name, title: item.title, source: item.source, link: item.link, publishedAt: item.publishedAt, event: EVENT.test(item.title) };
          const previous = found.get(name.toLowerCase());
          if (!previous || (candidate.event && !previous.event)) found.set(name.toLowerCase(), candidate);
        }
      }
    }
  }
  return [...found.values()].sort((a, b) => Number(b.event) - Number(a.event) || b.publishedAt.localeCompare(a.publishedAt));
}

const googleNewsUrl = (query, from, until) => {
  const q = `${query} after:${from.toISOString().slice(0, 10)} before:${until.toISOString().slice(0, 10)}`;
  return `https://news.google.com/rss/search?${new URLSearchParams({ q, hl: 'de', gl: 'DE', ceid: 'DE:de' })}`;
};

/** Reads the past week's feed and search items; a source that fails is skipped. */
export async function readRecentItems({ feeds, now = Date.now(), fetchImpl = fetch, log = console.log, pause = 1_500 }) {
  const since = now - 7 * DAY;
  const items = [];
  const read = async (url, feed) => {
    try {
      const response = await fetchImpl(url, {
        headers: { 'User-Agent': 'ProductBerlinNews/1.0 (+https://product.berlin)', Accept: 'application/rss+xml, application/xml' },
        signal: AbortSignal.timeout(20_000),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const parsed = parseRssXml(await response.text());
      const got = feedItems(parsed, feed).map(item => {
        // Google News items carry their publisher in <source>; feeds are their own publisher.
        const raw = (parsed?.rss?.channel?.item ?? []).find(entry => String(entry.link ?? '').trim() === item.link);
        const source = typeof raw?.source === 'object' ? raw.source['#text'] : raw?.source;
        if (source === undefined) return { ...item, source: feed.name };
        // A Google News teaser only repeats the headline and publisher, and the headline ends in " - Publisher".
        const publisher = String(source).trim();
        return { ...item, title: item.title.replace(new RegExp(`\\s[-–]\\s${publisher.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`), ''), teaser: '', source: publisher };
      });
      for (const item of got) {
        if (Date.parse(item.publishedAt) >= since && !items.some(seen => seen.link === item.link)) items.push(item);
      }
      return got;
    } catch (error) {
      log(`${feed.name}: ${error.message}`);
      return [];
    }
  };
  for (const feed of feeds) {
    for (let page = 1; page <= feed.pages; page++) {
      const got = await read(page === 1 ? feed.url : `${feed.url}${feed.url.includes('?') ? '&' : '?'}paged=${page}`, feed);
      if (!got.length || got.every(item => Date.parse(item.publishedAt) < since)) break;
    }
  }
  for (const query of SEARCHES) {
    await new Promise(resolve => setTimeout(resolve, pause));
    await read(googleNewsUrl(query, new Date(since), new Date(now + DAY)), { id: 'google-news', name: 'Google News' });
  }
  return items;
}

/** The workflow summary: a short table to review, or a line saying there is nothing new. */
export function summary(candidates) {
  if (!candidates.length) return '### Catalogue suggestions\n\nNo Berlin startups outside the catalogue were named this week.\n';
  const cell = value => String(value).replaceAll('|', '\\|').replace(/\s+/g, ' ');
  const rows = candidates.slice(0, 25).map(c =>
    `| ${cell(c.name)} | [${cell(c.title.slice(0, 110))}](${c.link}) | ${cell(c.source)} | ${c.publishedAt.slice(0, 10)} |`);
  return [
    '### Catalogue suggestions',
    '',
    'Berlin startups named in this week\'s news that are not in `cloudflare/startups.json`. Verify each one (Berlin, still active) before adding it; nothing is added automatically.',
    '',
    '| Company | Headline | Source | Date |',
    '| --- | --- | --- | --- |',
    ...rows,
    '',
  ].join('\n');
}

/** Mondays from 07:00 UTC, after the edition is published, once per week (or whenever DISCOVER_NOW is set). */
export const isDue = (now, forced) => forced || (new Date(now).getUTCDay() === 1 && new Date(now).getUTCHours() === 7);

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const now = Date.now();
  if (!isDue(now, Boolean(process.env.DISCOVER_NOW))) {
    console.log('Catalogue suggestions run on Mondays at 07:00 UTC; nothing to do.');
  } else {
    const root = new URL('..', import.meta.url);
    const catalogue = JSON.parse(readFileSync(new URL('cloudflare/startups.json', root), 'utf8'));
    const feeds = JSON.parse(readFileSync(new URL('cloudflare/feeds.json', root), 'utf8'));
    const candidates = findCandidates(await readRecentItems({ feeds, now }), catalogueNames(catalogue));
    const text = summary(candidates);
    console.log(text);
    if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, text);
  }
}
