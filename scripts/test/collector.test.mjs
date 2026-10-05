import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { after, before, beforeEach, test } from 'node:test';
import { Miniflare, convertV4MiniflareOptions } from 'miniflare';

let mf, db, directory, mode = 'success', calls = [];
const catalogue = JSON.parse(await readFile('cloudflare/startups.json', 'utf8')).slice(0, 12);
const weekOne = Date.parse('2026-09-28T06:00:00Z');
// Searches the Worker makes for the real bundled catalogue: names (plus distinct aliases) packed into ≤28-word groups.
const fullCatalogue = JSON.parse(await readFile('cloudflare/startups.json', 'utf8'));
const normalised = s => ` ${s.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim()} `;
const CATALOGUE_SEARCHES = 2 * fullCatalogue.reduce((groups, c) => {
  const terms = [c.name, ...c.aliases.filter(a => a.trim() && !normalised(a).includes(normalised(c.name)))].map(t => `"${t.replaceAll('"', '')}"`).join(' OR ');
  const last = groups.at(-1);
  if (last && `${last} OR ${terms}`.split(' ').filter(Boolean).length <= 28) groups[groups.length - 1] = `${last} OR ${terms}`;
  else groups.push(terms);
  return groups;
}, []).length;
let activeWeek = weekOne;
let discoveryGate, discoveryStarted;
let takeoverDone = false;
const xml = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;');
function item(company, id, day = 1) {
  const date = new Date(activeWeek - day * 86_400_000).toUTCString();
  return `<item><title>${xml(company.name)} ${company.contextKeywords?.[0] ?? ""} launches product ${id} - Publisher</title><source url="https://www.handelsblatt.com">Publisher</source><guid isPermaLink="false">${id}</guid><link>https://news.google.com/rss/articles/${id}?oc=5</link><pubDate>${date}</pubDate></item>`;
}
// Fixture stories are dated relative to the Monday 06:00 UTC that starts the event's week.
const DAY = 86_400_000;
const mondayOf = time => { const midnight = Math.floor(time / DAY) * DAY; return midnight - ((new Date(midnight).getUTCDay() + 6) % 7) * DAY + 6 * 3_600_000; };
// An item from a specific publisher, to exercise the publisher policy and headline de-duplication.
function itemFrom(company, id, source, site, title = `${company.name} ${company.contextKeywords?.[0] ?? ''} launches product ${id}`) {
  const date = new Date(activeWeek - 86_400_000).toUTCString();
  return `<item><title>${xml(title)} - ${xml(source)}</title><source url="${site}">${xml(source)}</source><guid isPermaLink="false">${id}</guid><link>https://news.google.com/rss/articles/${id}?oc=5</link><pubDate>${date}</pubDate></item>`;
}
// Publishers outside cloudflare/publishers.json: video, a foreign site and an untrusted generic one.
const blockedItems = company => [
  itemFrom(company, `${company.id}-video`, 'YouTube', 'https://www.youtube.com'),
  itemFrom(company, `${company.id}-ph`, 'politiko', 'https://politiko.com.ph'),
  itemFrom(company, `${company.id}-deal`, 'Popular Science', 'https://www.popsci.com'),
].join('');
async function scheduled(time) {
  activeWeek = mondayOf(time);
  const worker = await mf.getWorker();
  return worker.scheduled({ scheduledTime: new Date(time), cron: '0 6 * * MON' });
}
async function ranking() {
  const response = await mf.dispatchFetch('https://local.test/api/rankings/weekly');
  assert.equal(response.status, 200);
  return response.json();
}

before(async () => {
  directory = await mkdtemp(join(tmpdir(), 'productberlin-collector-'));
  const result = spawnSync(process.execPath, ['node_modules/wrangler/bin/wrangler.js', 'deploy', '--dry-run', '--outdir', directory], {
    env: { ...process.env, CI: 'true', WRANGLER_SEND_METRICS: 'false' }, encoding: 'utf8', timeout: 60_000,
  });
  assert.equal(result.status, 0, result.stdout + result.stderr);
  mf = new Miniflare(convertV4MiniflareOptions({
    name: 'collector', modules: true, script: await readFile(join(directory, 'entry.js'), 'utf8'), compatibilityDate: '2026-09-01',
    d1Databases: { DB: 'collector-test' }, bindings: { BUILD_SHA: 'test', SEARCH_PAUSE_MS: '0', RETRY_PAUSE_MS: '0' },
    outboundService: async request => {
      const url = new URL(request.url);
      assert.equal(url.origin, 'https://news.google.com');
      assert.equal(url.pathname, '/rss/search');
      const query = url.searchParams.get('q');
      const language = url.searchParams.get('hl');
      assert.ok(['en', 'de'].includes(language));
      assert.equal(url.searchParams.get('ceid'), `DE:${language}`);
      calls.push(query);
      if (mode === 'blocked' && calls.length === 1) {
        discoveryStarted();
        await discoveryGate;
      }
      if (mode === 'lost-lease' && !takeoverDone) {
        takeoverDone = true;
        await db.prepare("UPDATE collection_runs SET lease_token='new-owner',lease_until='2099-01-01T00:00:00.000Z' WHERE status='running'").run();
      }
      if (mode === 'partial-failure' && calls.length > 1) return new Response('Unavailable', { status: 503 });
      if (mode === 'flaky' && calls.length >= 2 && calls.length <= 4) return new Response('Unavailable', { status: 503 });
      // Like 2026-10-05: a burst of 503s in the first pass that clears up by the time failed searches are retried.
      if (mode === 'burst' && calls.length >= 10 && calls.length <= 15) return new Response('Unavailable', { status: 503 });
      if (mode === 'unavailable') return new Response('Unavailable', { status: 503 });
      if (mode === 'malformed') return new Response('<rss><channel></rss>');
      // Every search is a grouped catalogue search with the date first: after:… before:… ("A" OR "B" …)
      assert.match(query, /^after:\d{4}-\d{2}-\d{2} before:\d{4}-\d{2}-\d{2} \(".+\)$/);
      assert.ok(query.split(' ').length <= 30, query);
      let items = '';
      if (mode !== 'empty') {
        // The Worker searches the whole bundled catalogue; groups without any of the 12 test companies are empty.
        // Each test company gets a distinct number of articles (12 - position) plus seven more, so counts differ.
        const group = catalogue.filter(c => query.includes(`"${c.name}"`) && (mode !== 'sparse' || catalogue.indexOf(c) < 2));
        items = group.flatMap(c => {
          const i = catalogue.indexOf(c);
          return [
            ...Array.from({ length: 12 - i }, (_, n) => item(c, `${c.id}-${n}`, 2)),
            ...Array.from({ length: 7 }, (_, n) => item(c, `${c.id}-related-${n}`, n + 1)),
          ];
        }).join('');
        if (language === 'de' && group.includes(catalogue[0])) items += item(catalogue[0], 'german-only', 2);
        if (mode === 'publishers') {
          items = group.map(c => {
            const title = `${c.name} ${c.contextKeywords?.[0] ?? ''} wins syndicated award`;
            return blockedItems(c) + itemFrom(c, `${c.id}-syndicated-1`, 'Xpert.Digital - Author', 'https://xpert.digital', title)
              + itemFrom(c, `${c.id}-syndicated-2`, 'xpert.digital', 'https://xpert.digital', title);
          }).join('') + items;
        }
      }
      return new Response(`<rss version="2.0"><channel>${items}</channel></rss>`, { headers: { 'content-type': 'application/rss+xml' } });
    },
  }));
  db = (await mf.getBindings()).DB;
  for (const file of ['0001_initial.sql', '0002_weekly_collection.sql']) {
    const sql = await readFile(resolve('cloudflare/migrations', file), 'utf8');
    await db.batch(sql.split(';').map(s => s.trim()).filter(Boolean).map(s => db.prepare(s)));
  }
});
after(async () => {
  await mf?.dispose();
  if (directory) await rm(directory, { recursive: true, force: true });
});

beforeEach(async () => {
  await db.batch(['news_articles', 'ranking_entries', 'ranking_snapshots', 'startups', 'collection_runs'].map(table => db.prepare(`DELETE FROM ${table}`)));
  calls = [];
  mode = 'success';
  takeoverDone = false;
});

test('scheduled collector publishes ten ranked companies and at most five dated links through the API', async () => {
  // A successful collection from the old policy must not block the expanded same-week edition.
  await db.prepare("INSERT INTO collection_runs (week_start,lease_token,lease_until,status,started_at) VALUES ('2026-09-28','old','2026-09-28','succeeded','2026-09-28')").run();
  await db.batch([
    db.prepare("INSERT INTO startups (id,name,description,category) VALUES (?,?,?,?)").bind(catalogue[0].id, catalogue[0].name, 'Old description', 'Category'),
    db.prepare("INSERT INTO ranking_snapshots (id,week_start,week_label,status,is_mock,created_at,expected_count) VALUES ('old-policy','2026-09-21','Old edition','published',0,'2026-09-21T00:00:00.000Z',1)"),
    db.prepare("INSERT INTO ranking_entries (snapshot_id,startup_id,position,reason,mention_count) VALUES ('old-policy',?,1,'Old reason',1)").bind(catalogue[0].id),
  ]);
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const result = await ranking();
  assert.equal(result.isMock, false);
  assert.equal(result.startups.length, 10);
  // 79 articles with distinct per-company counts plus 7 more for each of the 12 test companies.
  assert.equal(result.articleCount, 79 + 12 * 7);
  assert.deepEqual(result.startups.map(s => s.id), catalogue.slice(0, 10).map(s => s.id));
  assert.deepEqual(result.startups.map(s => s.mentionCount), [13, 11, 10, 9, 8, 7, 6, 5, 4, 3].map(n => n + 7));
  // Every company in the bundled catalogue, packed into ≤30-word groups, in two languages.
  assert.equal(calls.length, CATALOGUE_SEARCHES);
  for (const startup of result.startups) {
    assert.equal(startup.news.length, 5);
    assert.equal(startup.movement, null);
    assert.match(startup.news[0].url, /^https:\/\/news.google.com\/rss\/articles\//);
    assert.equal(startup.news[0].source, 'Publisher');
  }
  assert.equal((await db.prepare("SELECT status FROM collection_runs WHERE week_start='2026-09-28-trusted-v1'").first()).status, 'succeeded');
});

test('duplicate events skip collection, and a failed next week retains the previous published snapshot', async () => {
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const previous = await ranking();
  const count = calls.length;
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  assert.equal(calls.length, count);
  mode = 'unavailable';
  assert.notEqual((await scheduled(weekOne + 7 * 86_400_000)).outcome, 'ok');
  assert.deepEqual(await ranking(), previous);
  assert.equal((await db.prepare("SELECT status FROM collection_runs WHERE week_start='2026-10-05-trusted-v1'").first()).status, 'failed');
});

test('off-schedule events during a week neither refetch nor replace the published edition', async () => {
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const published = await ranking();
  const count = calls.length;
  for (const day of [1, 3, 6]) {
    assert.equal((await scheduled(weekOne + day * DAY)).outcome, 'ok');
    assert.equal(calls.length, count);
    assert.deepEqual(await ranking(), published);
  }
  assert.equal((await db.prepare('SELECT COUNT(*) AS count FROM ranking_snapshots').first()).count, 1);
});

test('a failed Monday collection is retried for the same week by a later event that week', async () => {
  mode = 'unavailable';
  assert.notEqual((await scheduled(weekOne)).outcome, 'ok');
  mode = 'success';
  assert.equal((await scheduled(weekOne + 3 * DAY)).outcome, 'ok');
  assert.equal((await ranking()).weekLabel, '2026-09-21 – 2026-09-27');
  assert.equal((await db.prepare("SELECT status FROM collection_runs WHERE week_start='2026-09-28-trusted-v1'").first()).status, 'succeeded');
});

test('D1 publication rolls back all writes on failure, then a retry publishes with movement', async () => {
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const previous = await ranking();
  mode = 'success';
  await db.prepare("CREATE TRIGGER reject_news BEFORE INSERT ON news_articles BEGIN SELECT RAISE(ABORT, 'test publication failure'); END").run();
  assert.notEqual((await scheduled(weekOne + 7 * 86_400_000)).outcome, 'ok');
  assert.deepEqual(await ranking(), previous);
  assert.equal((await db.prepare('SELECT COUNT(*) AS count FROM ranking_snapshots').first()).count, 1);
  await db.prepare('DROP TRIGGER reject_news').run();
  assert.equal((await scheduled(weekOne + 7 * 86_400_000)).outcome, 'ok');
  assert.equal((await ranking()).weekLabel, '2026-09-28 – 2026-10-04');
  assert.ok((await ranking()).startups.every(s => s.movement === 0));
});

test('empty and malformed feeds retain data; an active lease skips and an expired lease recovers', async () => {
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const next = weekOne + 14 * 86_400_000;
  const previous = await ranking();
  for (const failure of ['empty', 'malformed']) {
    mode = failure;
    assert.notEqual((await scheduled(next)).outcome, 'ok');
    assert.deepEqual(await ranking(), previous);
  }
  await db.prepare("UPDATE collection_runs SET status='running',lease_until='2099-01-01T00:00:00.000Z' WHERE week_start='2026-10-12-trusted-v1'").run();
  const count = calls.length;
  assert.equal((await scheduled(next)).outcome, 'ok');
  assert.equal(calls.length, count);
  await db.prepare("UPDATE collection_runs SET lease_until='2000-01-01T00:00:00.000Z' WHERE week_start='2026-10-12-trusted-v1'").run();
  mode = 'success';
  assert.equal((await scheduled(next)).outcome, 'ok');
  assert.equal((await ranking()).weekLabel, '2026-10-05 – 2026-10-11');
});

async function contents() {
  const tables = ['ranking_snapshots', 'startups', 'ranking_entries', 'news_articles'];
  return Promise.all(tables.map(async table => (await db.prepare(`SELECT * FROM ${table} ORDER BY rowid`).all()).results));
}

test('every stage of the D1 batch is atomic, including the final success audit write', async () => {
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const previous = await ranking();
  const stored = await contents();
  for (const target of [
    'BEFORE INSERT ON ranking_snapshots',
    'BEFORE UPDATE ON startups',
    'BEFORE INSERT ON ranking_entries',
    'BEFORE INSERT ON news_articles',
    "BEFORE UPDATE ON ranking_snapshots WHEN NEW.status='published'",
    "BEFORE UPDATE ON collection_runs WHEN NEW.status='succeeded'",
  ]) {
    await db.prepare(`CREATE TRIGGER reject_publication ${target} BEGIN SELECT RAISE(ABORT, 'forced batch failure'); END`).run();
    try {
      assert.notEqual((await scheduled(weekOne + 7 * 86_400_000)).outcome, 'ok', target);
      assert.deepEqual(await contents(), stored, target);
      assert.deepEqual(await ranking(), previous, target);
      const run = await db.prepare("SELECT status,error FROM collection_runs WHERE week_start='2026-10-05-trusted-v1'").first();
      assert.equal(run.status, 'failed');
      assert.ok(run.error.length > 0 && run.error.length <= 500);
    } finally {
      await db.prepare('DROP TRIGGER reject_publication').run();
    }
  }
  assert.equal((await scheduled(weekOne + 7 * 86_400_000)).outcome, 'ok');
});

test('a failure partway through the searches retains all previous DB content', async () => {
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const stored = await contents();
  calls = [];
  mode = 'partial-failure'; // the first search succeeds, every later one fails
  assert.notEqual((await scheduled(weekOne + 7 * 86_400_000)).outcome, 'ok');
  // 4 failures may remain and 5 can be retried, so the first pass stops at the tenth failure.
  assert.equal(calls.length, 11);
  assert.deepEqual(await contents(), stored);
});

test('a worker whose lease was replaced cannot publish or mark the new owner failed', async () => {
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const stored = await contents();
  mode = 'lost-lease';
  assert.notEqual((await scheduled(weekOne + 7 * 86_400_000)).outcome, 'ok');
  assert.deepEqual(await contents(), stored);
  const run = await db.prepare("SELECT lease_token,status,error FROM collection_runs WHERE week_start='2026-10-05-trusted-v1'").first();
  assert.deepEqual(run, { lease_token: 'new-owner', status: 'running', error: null });
});

test('overlapping scheduled deliveries have exactly one fetch and publication owner', { timeout: 15_000 }, async () => {
  mode = 'blocked';
  let release;
  discoveryGate = new Promise(resolve => { release = resolve; });
  const started = new Promise(resolve => { discoveryStarted = resolve; });
  const first = scheduled(weekOne);
  try {
    await Promise.race([started, first.then(() => { throw new Error("Collector completed before the fetch gate"); })]);
    assert.equal((await scheduled(weekOne)).outcome, 'ok');
    assert.equal(calls.length, 1);
  } finally { release(); }
  assert.equal((await first).outcome, 'ok');
  assert.equal(calls.length, CATALOGUE_SEARCHES);
  assert.equal((await db.prepare('SELECT COUNT(*) AS count FROM ranking_snapshots').first()).count, 1);
  assert.equal((await ranking()).startups.length, 10);
});

test('a sparse valid week publishes its actual positive count and ordered newest-five news', async () => {
  mode = 'sparse';
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const result = await ranking();
  assert.equal(result.startups.length, 2);
  assert.equal(result.articleCount, 24 + 2 * 7);
  assert.equal(calls.length, CATALOGUE_SEARCHES);
  assert.equal((await db.prepare('SELECT expected_count FROM ranking_snapshots').first()).expected_count, 2);
  for (const company of result.startups) {
    assert.equal(company.news.length, 5);
    assert.equal(new Set(company.news.map(n => n.url)).size, 5);
    assert.deepEqual(company.news.map(n => n.publishedAt), company.news.map(n => n.publishedAt).sort().reverse());
    assert.ok(company.news.every(n => Date.parse(n.publishedAt) < activeWeek));
  }
});

test('rank movement ignores newer mock, draft and incomplete earlier-week snapshots', async () => {
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  for (const [id, status, mock, expected] of [['mock', 'published', 1, 1], ['draft', 'draft', 0, 1], ['incomplete', 'published', 0, 10]]) {
    await db.prepare('INSERT INTO ranking_snapshots (id,week_start,week_label,status,is_mock,created_at,expected_count) VALUES (?,?,?,?,?,?,?)')
      .bind(id, '2026-09-27', id, status, mock, '2026-09-27T12:00:00.000Z', expected).run();
    await db.prepare("INSERT INTO ranking_entries (snapshot_id,startup_id,position,reason,mention_count) VALUES (?,?,1,'Ignored',1)")
      .bind(id, catalogue[1].id).run();
  }
  assert.equal((await scheduled(weekOne + 7 * 86_400_000)).outcome, 'ok');
  assert.ok((await ranking()).startups.every(s => s.movement === 0));
});

test('untrusted publishers neither count nor show, and syndicated headlines show once', async () => {
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const baseline = Object.fromEntries((await ranking()).startups.map(s => [s.id, s.mentionCount]));
  await db.batch(['news_articles', 'ranking_entries', 'ranking_snapshots', 'startups', 'collection_runs'].map(table => db.prepare(`DELETE FROM ${table}`)));
  mode = 'publishers';
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const filtered = await ranking();
  // Untrusted items add nothing. The two syndicated copies (trusted, differently spelled publisher) still count
  // separately, as documented, but are shown once.
  assert.deepEqual(Object.fromEntries(filtered.startups.map(s => [s.id, s.mentionCount])), Object.fromEntries(Object.entries(baseline).map(([id, n]) => [id, n + 2])));
  for (const startup of filtered.startups) {
    assert.ok(startup.news.every(n => !['YouTube', 'politiko', 'Popular Science'].includes(n.source)), startup.id);
    const headlines = startup.news.map(n => n.headline.toLowerCase());
    assert.equal(new Set(headlines).size, headlines.length, startup.id);
    assert.equal(headlines.filter(h => h.includes('syndicated award')).length, 1, startup.id);
  }
});

test('a few failed searches (Google 503s) still publish the edition', async () => {
  mode = 'flaky'; // searches 2–4 fail once and succeed when retried
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  assert.equal(calls.length, CATALOGUE_SEARCHES + 3);
  const result = await ranking();
  assert.equal(result.weekLabel, '2026-09-21 – 2026-09-27');
  assert.equal(result.startups.length, 10);
});

test('the hourly trigger waits for Monday 06:00 UTC before a new week, then retries failures later that week', async () => {
  assert.equal((await scheduled(Date.parse('2026-09-28T05:17:00Z'))).outcome, 'ok');
  assert.equal(calls.length, 0);
  assert.equal((await db.prepare('SELECT COUNT(*) AS count FROM collection_runs').first()).count, 0);
  mode = 'unavailable';
  assert.notEqual((await scheduled(Date.parse('2026-09-28T06:17:00Z'))).outcome, 'ok');
  mode = 'success';
  assert.equal((await scheduled(Date.parse('2026-09-28T07:17:00Z'))).outcome, 'ok');
  assert.equal((await ranking()).weekLabel, '2026-09-21 – 2026-09-27');
  const count = calls.length;
  assert.equal((await scheduled(Date.parse('2026-09-28T08:17:00Z'))).outcome, 'ok');
  assert.equal(calls.length, count, 'a published week is skipped without contacting Google');
});

test('a burst of six 503s is recovered by retrying failed searches after a pause', async () => {
  mode = 'burst';
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  // 6 failures exceed the 4 allowed; the 5 spare requests retry them and 1 remaining failure is tolerated.
  assert.equal(calls.length, CATALOGUE_SEARCHES + 5);
  assert.equal(calls.length <= 49, true, 'stays within the free plan subrequest budget');
  assert.equal((await ranking()).weekLabel, '2026-09-21 – 2026-09-27');
});
