// Feasibility probe: can this machine (e.g. a GitHub Actions runner) query Google News RSS the way the Worker does?
// Runs the Worker's catalogue searches for the latest complete Monday–Sunday UTC week and reports what succeeded.
// It reads nothing secret and writes nothing except an optional Markdown summary. Not part of `npm run check`.
import { appendFileSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { XMLParser } from 'fast-xml-parser';

const pauseMs = Number(process.env.PROBE_PAUSE_MS ?? 3000);
const catalogue = JSON.parse(await readFile('cloudflare/startups.json', 'utf8'));
const publishers = JSON.parse(await readFile('cloudflare/publishers.json', 'utf8')).map(p => p.domain);
const DAY = 86_400_000;

// Same week as CollectionWindow.latestCompleteWeek(now).
const midnight = Date.parse(new Date().toISOString().slice(0, 10) + 'T00:00:00.000Z');
const end = midnight - ((new Date(midnight).getUTCDay() + 6) % 7) * DAY;
const start = end - 7 * DAY;
const day = time => new Date(time).toISOString().slice(0, 10);
const range = `after:${day(start)} before:${day(end)}`;

// Same grouping as CollectionWindow.catalogueSearches: date first, names packed into ≤28-word groups.
const normalised = s => ` ${s.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim()} `;
const words = s => s.split(' ').filter(Boolean).length;
const groups = [];
for (const company of catalogue) {
  const terms = [company.name, ...company.aliases.filter(a => a.trim() && !normalised(a).includes(normalised(company.name)))]
    .map(t => `"${t.replaceAll('"', '')}"`).join(' OR ');
  const last = groups.at(-1);
  if (last && words(`${last} OR ${terms}`) <= 28) groups[groups.length - 1] = `${last} OR ${terms}`;
  else groups.push(terms);
}
const searches = groups.flatMap(group => ['en', 'de'].map(language => ({ query: `${range} (${group})`, language })));

const parser = new XMLParser({ ignoreAttributes: false, isArray: (_name, path) => path === 'rss.channel.item' });
const trusted = url => {
  const host = /^https?:\/\/([^/:?#]+)/i.exec(url ?? '')?.[1]?.toLowerCase().replace(/\.$/, '') ?? '';
  return publishers.some(d => host === d || host.endsWith(`.${d}`));
};
const statuses = {};
const articles = new Map();
let failures = 0;
for (const [index, search] of searches.entries()) {
  if (index > 0) await new Promise(resolve => setTimeout(resolve, pauseMs));
  const url = 'https://news.google.com/rss/search?' + new URLSearchParams({
    q: search.query, hl: search.language, gl: 'DE', ceid: `DE:${search.language}`,
  });
  let status;
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(20_000) });
    status = String(response.status);
    if (response.ok) {
      for (const item of parser.parse(await response.text())?.rss?.channel?.item ?? []) {
        const time = Date.parse(item.pubDate);
        if (time >= start && time < end && trusted(item.source?.['@_url'])) articles.set(item.link, String(item.title));
      }
    } else failures++;
  } catch (error) {
    status = error.name;
    failures++;
  }
  statuses[status] = (statuses[status] ?? 0) + 1;
  console.log(`${index + 1}/${searches.length} ${search.language} → ${status}`);
}

const counts = catalogue.map(c => ({
  name: c.name,
  count: [...articles.values()].filter(title => {
    const headline = normalised(title);
    const named = [c.name, ...c.aliases].some(a => a.trim() && headline.includes(normalised(a)));
    const context = !c.contextKeywords?.length || c.contextKeywords.some(k => k.trim() && headline.includes(normalised(k)));
    return named && context;
  }).length,
})).filter(c => c.count > 0).sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));

const verdict = failures === 0 ? '✅ All searches succeeded'
  : failures <= 4 ? `⚠️ ${failures} searches failed (the Worker tolerates 4)`
  : `❌ ${failures} searches failed`;
const summary = [
  `### Google News probe: ${verdict}`,
  '',
  `Week ${day(start)} – ${day(end - DAY)} · ${searches.length} searches · ${pauseMs / 1000} s apart`,
  '',
  `HTTP results: ${Object.entries(statuses).map(([s, n]) => `${s} × ${n}`).join(', ')}`,
  '',
  `Trusted in-week articles: ${articles.size} · Companies mentioned: ${counts.length}`,
  '',
  counts.slice(0, 15).map((c, i) => `${i + 1}. ${c.name} (${c.count})`).join('\n'),
  '',
].join('\n');
console.log('\n' + summary);
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, summary);
