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
let activeWeek = weekOne;
let discoveryGate, discoveryStarted;
let takeoverDone = false;
const xml = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;');
function item(company, id, day = 1) {
  const date = new Date(activeWeek - day * 86_400_000).toUTCString();
  return `<item><title>${xml(company.name)} ${company.contextKeywords?.[0] ?? ""} launches product ${id} - Publisher</title><source url="https://publisher.test">Publisher</source><guid isPermaLink="false">${id}</guid><link>https://news.google.com/rss/articles/${id}?oc=5</link><pubDate>${date}</pubDate></item>`;
}
// Fixture stories are dated relative to the Monday 06:00 UTC that starts the event's week.
const DAY = 86_400_000;
const mondayOf = time => { const midnight = Math.floor(time / DAY) * DAY; return midnight - ((new Date(midnight).getUTCDay() + 6) % 7) * DAY + 6 * 3_600_000; };
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
    d1Databases: { DB: 'collector-test' }, bindings: { BUILD_SHA: 'test' },
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
      if (mode === 'lost-lease' && !takeoverDone && !query.startsWith('(Berlin')) {
        takeoverDone = true;
        await db.prepare("UPDATE collection_runs SET lease_token='new-owner',lease_until='2099-01-01T00:00:00.000Z' WHERE status='running'").run();
      }
      if (mode === 'enrichment-failure' && !query.startsWith('(Berlin')) return new Response('Unavailable', { status: 503 });
      if (mode === 'unavailable') return new Response('Unavailable', { status: 503 });
      if (mode === 'malformed') return new Response('<rss><channel></rss>');
      let items = '';
      if (mode !== 'empty') {
        if (query.startsWith('(Berlin OR Berliner)')) {
          // Same articles in each daily feed must not multiply counts. Each company has a distinct count.
          items = (mode === 'sparse' ? catalogue.slice(0, 2) : catalogue).flatMap((company, i) => Array.from({ length: 12 - i }, (_, n) => item(company, `${company.id}-${n}`, 2))).join('');
          if (language === 'de') items += item(catalogue[0], 'german-only', 2);
        } else {
          const company = catalogue.find(c => query.startsWith(`"${c.name}"`));
          assert.ok(company, query);
          items = Array.from({ length: 7 }, (_, n) => item(company, `${company.id}-related-${n}`, n + 1)).join('');
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
  assert.equal(result.articleCount, 79);
  assert.deepEqual(result.startups.map(s => s.id), catalogue.slice(0, 10).map(s => s.id));
  assert.deepEqual(result.startups.map(s => s.mentionCount), [13, 11, 10, 9, 8, 7, 6, 5, 4, 3]);
  assert.equal(calls.length, 34);
  for (const startup of result.startups) {
    assert.equal(startup.news.length, 5);
    assert.equal(startup.movement, null);
    assert.match(startup.news[0].url, /^https:\/\/news.google.com\/rss\/articles\//);
    assert.equal(startup.news[0].source, 'Publisher');
  }
  assert.equal((await db.prepare("SELECT status FROM collection_runs WHERE week_start='2026-09-28-bilingual-v2'").first()).status, 'succeeded');
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
  assert.equal((await db.prepare("SELECT status FROM collection_runs WHERE week_start='2026-10-05-bilingual-v2'").first()).status, 'failed');
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
  assert.equal((await db.prepare("SELECT status FROM collection_runs WHERE week_start='2026-09-28-bilingual-v2'").first()).status, 'succeeded');
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
  await db.prepare("UPDATE collection_runs SET status='running',lease_until='2099-01-01T00:00:00.000Z' WHERE week_start='2026-10-12-bilingual-v2'").run();
  const count = calls.length;
  assert.equal((await scheduled(next)).outcome, 'ok');
  assert.equal(calls.length, count);
  await db.prepare("UPDATE collection_runs SET lease_until='2000-01-01T00:00:00.000Z' WHERE week_start='2026-10-12-bilingual-v2'").run();
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
      const run = await db.prepare("SELECT status,error FROM collection_runs WHERE week_start='2026-10-05-bilingual-v2'").first();
      assert.equal(run.status, 'failed');
      assert.ok(run.error.length > 0 && run.error.length <= 500);
    } finally {
      await db.prepare('DROP TRIGGER reject_publication').run();
    }
  }
  assert.equal((await scheduled(weekOne + 7 * 86_400_000)).outcome, 'ok');
});

test('failure while fetching winner news retains all previous DB content', async () => {
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const stored = await contents();
  const beforeCalls = calls.length;
  mode = 'enrichment-failure';
  assert.notEqual((await scheduled(weekOne + 7 * 86_400_000)).outcome, 'ok');
  assert.equal(calls.length - beforeCalls, 15);
  assert.deepEqual(await contents(), stored);
});

test('a worker whose lease was replaced cannot publish or mark the new owner failed', async () => {
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const stored = await contents();
  mode = 'lost-lease';
  assert.notEqual((await scheduled(weekOne + 7 * 86_400_000)).outcome, 'ok');
  assert.deepEqual(await contents(), stored);
  const run = await db.prepare("SELECT lease_token,status,error FROM collection_runs WHERE week_start='2026-10-05-bilingual-v2'").first();
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
  assert.equal(calls.length, 34);
  assert.equal((await db.prepare('SELECT COUNT(*) AS count FROM ranking_snapshots').first()).count, 1);
  assert.equal((await ranking()).startups.length, 10);
});

test('a sparse valid week publishes its actual positive count and ordered newest-five news', async () => {
  mode = 'sparse';
  assert.equal((await scheduled(weekOne)).outcome, 'ok');
  const result = await ranking();
  assert.equal(result.startups.length, 2);
  assert.equal(result.articleCount, 24);
  assert.equal(calls.length, 18);
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
