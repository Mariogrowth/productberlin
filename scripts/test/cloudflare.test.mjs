import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { once } from 'node:events';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { after, before, test } from 'node:test';
import { mockSeedSql, snapshotId } from '../seed.mjs';

const wrangler = resolve('node_modules/wrangler/bin/wrangler.js');
const env = { ...process.env, CI: 'true', WRANGLER_SEND_METRICS: 'false' };
let state, server, origin, logs = '';
function cli(args) {
  const result = spawnSync(process.execPath, [wrangler, ...args, '--persist-to', state], {
    env, encoding: 'utf8', timeout: 60_000,
  });
  assert.equal(result.status, 0, result.stdout + result.stderr);
  return result.stdout;
}
function execute(sql) {
  return cli(['d1', 'execute', 'DB', '--local', '--command', sql, '--json']);
}
async function seed() {
  const path = join(state, 'seed.sql');
  await writeFile(path, await mockSeedSql());
  cli(['d1', 'execute', 'DB', '--local', '--file', path, '--yes']);
}
async function ranking() {
  const response = await fetch(`${origin}/api/rankings/weekly`);
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /application\/json/);
  return response.json();
}

before(async () => {
  state = await mkdtemp(join(tmpdir(), 'productberlin-d1-test-'));
  cli(['d1', 'migrations', 'apply', 'DB', '--local']);
  const portFinder = createServer();
  portFinder.listen(0, '127.0.0.1');
  await once(portFinder, 'listening');
  const port = portFinder.address().port;
  await new Promise(resolve => portFinder.close(resolve));
  origin = `http://127.0.0.1:${port}`;
  server = spawn(process.execPath, [wrangler, 'dev', '--local', '--ip', '127.0.0.1', '--port', String(port), '--inspector-port', '0', '--persist-to', state], { env });
  server.stdout.on('data', chunk => { logs += chunk; });
  server.stderr.on('data', chunk => { logs += chunk; });
  for (let i = 0; i < 120; i++) {
    if (server.exitCode !== null) throw new Error(logs);
    try {
      if ((await fetch(`${origin}/api/health`)).ok) return;
    } catch {}
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error(`Worker did not start: ${logs}`);
});
after(async () => {
  if (server && server.exitCode === null) {
    const exited = once(server, 'exit');
    server.kill('SIGTERM');
    const force = setTimeout(() => server.kill('SIGKILL'), 5000);
    await exited;
    clearTimeout(force);
  }
  if (state) await rm(state, { recursive: true, force: true });
});

test('unseeded D1 returns an empty ranking; migrations can be applied again', async () => {
  assert.deepEqual((await ranking()).startups, []);
  cli(['d1', 'migrations', 'apply', 'DB', '--local']);
});

test('seeded D1 serves exactly the shared mock contract and is idempotent', async () => {
  await seed();
  const expected = JSON.parse(await readFile('cloudflare/fixtures/ranking.json', 'utf8'));
  const actual = await ranking();
  assert.deepEqual(actual, {
    ...expected, isMock: true, updatedAt: '2026-09-13T00:00:00Z', searchQuery: null, articleCount: null,
    startups: expected.startups.map(s => ({ ...s, mentionCount: null, news: s.news.map(n => ({ ...n, url: null, summary: null })) })),
  });
  await seed();
  assert.deepEqual(await ranking(), actual);
  const counts = JSON.parse(execute('SELECT COUNT(*) AS count FROM ranking_entries;'));
  assert.equal(counts[0].results[0].count, 10);
});

test('API reads D1 changes, while incomplete or draft snapshots stay hidden', async () => {
  execute(`UPDATE ranking_entries SET reason = 'Database-backed reason' WHERE snapshot_id = '${snapshotId}' AND position = 1;
    INSERT INTO ranking_snapshots (id,week_start,week_label,status,is_mock,created_at) VALUES ('future-draft', '2099-01-01', 'Future', 'draft', 0, '2099-01-01T00:00:00Z');
    INSERT INTO ranking_snapshots (id,week_start,week_label,status,is_mock,created_at) VALUES ('incomplete', '2099-01-02', 'Incomplete', 'published', 0, '2099-01-02T00:00:00Z');
    INSERT INTO ranking_entries (snapshot_id,startup_id,position,movement,reason) SELECT 'future-draft', startup_id, position, movement, reason FROM ranking_entries WHERE snapshot_id = '${snapshotId}';
    INSERT INTO news_articles (id,snapshot_id,startup_id,headline,source,url,published_at) VALUES ('second-story', '${snapshotId}', 'almedia', 'Second DB story', 'Demo', NULL, 'Sep 12, 2026');`);
  await seed();
  const result = await ranking();
  assert.equal(result.startups.length, 10);
  assert.equal(result.startups[0].reason, 'Database-backed reason');
  assert.equal(result.startups[0].news.length, 2);
  assert.equal(result.startups[0].news[0].headline, 'Second DB story');
  assert.equal(result.weekLabel, 'September 7–13, 2026');
});

test('health checks D1 and reports the build version', async () => {
  const response = await fetch(`${origin}/api/health`);
  assert.deepEqual(await response.json(), { status: 'ok', version: 'local' });
});

test('API errors are JSON, unsupported methods are rejected, HEAD has no body', async () => {
  for (const path of ['/api/missing', '/api']) {
    const response = await fetch(origin + path);
    assert.equal(response.status, 404);
    assert.deepEqual(await response.json(), { error: 'Not found' });
  }
  const post = await fetch(`${origin}/api/rankings/weekly`, { method: 'POST' });
  assert.equal(post.status, 405);
  assert.equal(post.headers.get('allow'), 'GET, HEAD');
  const head = await fetch(`${origin}/api/rankings/weekly`, { method: 'HEAD' });
  assert.equal(head.status, 200);
  assert.equal(await head.text(), '');
});

test('Worker serves the real website and SPA fallback alongside the API', async () => {
  for (const path of ['/', '/startups']) {
    const response = await fetch(origin + path);
    assert.equal(response.status, 200);
    assert.match(await response.text(), /productberlin\.js/);
  }
  const script = await fetch(`${origin}/productberlin.js`);
  assert.equal(script.status, 200);
  assert.match(script.headers.get('content-type'), /javascript/);
});

test('D1 failures return a retryable service error, never a successful empty ranking', async () => {
  execute('DROP TABLE news_articles;');
  const response = await fetch(`${origin}/api/rankings/weekly`);
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { error: 'Data temporarily unavailable' });
});
