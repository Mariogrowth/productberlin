import assert from 'node:assert/strict';
import { test } from 'node:test';
import { PROVIDERS, collectJobs } from '../collect-jobs.mjs';

// Trimmed copies of each provider's public feed format.
const FEEDS = {
  'https://acme.jobs.personio.de/xml': `<?xml version="1.0" encoding="UTF-8"?><workzag-jobs>
    <position><id>1</id><office>Berlin</office><name>Engineer</name></position>
    <position><id>2</id><office>Munich</office><additionalOffices><office>Berlin</office></additionalOffices><name>Sales</name></position>
    <position><id>3</id><office>Hamburg</office><name>Ops</name></position></workzag-jobs>`,
  'https://api.ashbyhq.com/posting-api/job-board/acme': JSON.stringify({ jobs: [
    { title: 'A', location: 'Berlin, Germany' },
    { title: 'B', location: 'Remote', secondaryLocations: [{ location: 'Berlin' }] },
    { title: 'C', location: 'Remote (EU)' }] }),
  'https://boards-api.greenhouse.io/v1/boards/acme/jobs': JSON.stringify({ jobs: [
    { title: 'A', location: { name: 'Berlin' } }, { title: 'B', location: { name: 'London' } }] }),
  'https://api.lever.co/v0/postings/acme?mode=json': JSON.stringify([
    { text: 'A', categories: { location: 'Paris', allLocations: ['Paris', 'Berlin'] } }, { text: 'B', categories: { location: 'Paris' } }]),
  'https://apply.workable.com/api/v1/widget/accounts/acme': JSON.stringify({ jobs: [{ title: 'A', city: 'Berlin' }, { title: 'B', city: 'Leipzig' }] }),
  'https://acme.recruitee.com/api/offers/': JSON.stringify({ offers: [{ title: 'A', city: 'Berlin' }, { title: 'B', locations: [{ city: 'Berlin' }] }] }),
};
const SMART_PAGES = [
  { totalFound: 150, content: Array.from({ length: 100 }, (_, i) => ({ location: { city: i < 10 ? 'Berlin' : 'Dubai' } })) },
  { totalFound: 150, content: Array.from({ length: 50 }, () => ({ location: { city: 'Berlin' } })) },
];
const read = async url => {
  const smart = /smartrecruiters\.com\/v1\/companies\/acme\/postings\?limit=100&offset=(\d+)/.exec(url);
  if (smart) return JSON.stringify(SMART_PAGES[Number(smart[1]) / 100]);
  if (!(url in FEEDS)) throw new Error('HTTP 404');
  return FEEDS[url];
};

test('every provider counts all open roles and those in Berlin, including secondary locations', async () => {
  const expected = {
    personio: { totalJobs: 3, berlinJobs: 2 }, ashby: { totalJobs: 3, berlinJobs: 2 }, greenhouse: { totalJobs: 2, berlinJobs: 1 },
    lever: { totalJobs: 2, berlinJobs: 1 }, workable: { totalJobs: 2, berlinJobs: 1 }, recruitee: { totalJobs: 2, berlinJobs: 2 },
    smartrecruiters: { totalJobs: 150, berlinJobs: 60 },
  };
  assert.deepEqual(Object.keys(PROVIDERS).sort(), Object.keys(expected).sort());
  for (const [provider, counts] of Object.entries(expected)) {
    assert.deepEqual(await PROVIDERS[provider].count('acme', read), counts, provider);
  }
});

test('a Personio handle that serves an HTML page instead of a job feed is an error, not zero roles', async () => {
  await assert.rejects(PROVIDERS.personio.count('acme', async () => '<!DOCTYPE html><html></html>'), /Not a Personio job feed/);
});

const plan = { due: true, week: '2026-10-05', boards: [
  { startupId: 'acme', provider: 'greenhouse', handle: 'acme' },
  { startupId: 'beta', provider: 'workable', handle: 'acme' },
  { startupId: 'gone', provider: 'lever', handle: 'missing' },
] };
function fakeFetch({ planBody = plan, postStatus = 200, flaky = new Set() } = {}) {
  const calls = { feeds: [], posts: [], auth: [] };
  const impl = async (url, init = {}) => {
    const target = String(url);
    if (target.startsWith('https://site.test/')) {
      calls.auth.push(init.headers?.Authorization);
      if (init.method === 'POST') {
        calls.posts.push(JSON.parse(init.body));
        return Response.json(postStatus === 200 ? { outcome: 'recorded', week: plan.week, boards: 2, failed: 1 } : { error: 'too_many_failures' }, { status: postStatus });
      }
      return Response.json(planBody);
    }
    calls.feeds.push(target);
    if (flaky.has(target) && calls.feeds.filter(u => u === target).length === 1) return new Response('busy', { status: 503 });
    return target in FEEDS ? new Response(FEEDS[target]) : new Response('missing', { status: 404 });
  };
  return { impl, calls };
}
const run = (fake, extra = {}) => collectJobs({ baseUrl: 'https://site.test', token: 'secret', fetchImpl: fake.impl, retryPauseMs: 0, log: () => {}, ...extra });

test('reads exactly the planned boards and posts only counts, with failures marked for the Worker', async () => {
  const fake = fakeFetch({ flaky: new Set(['https://boards-api.greenhouse.io/v1/boards/acme/jobs']) });
  assert.deepEqual(await run(fake), { outcome: 'recorded', failed: 1 });
  assert.ok(fake.calls.auth.every(h => h === 'Bearer secret'));
  const posted = fake.calls.posts[0];
  assert.equal(posted.week, plan.week);
  assert.deepEqual(posted.results, [
    { startupId: 'acme', provider: 'greenhouse', handle: 'acme', totalJobs: 2, berlinJobs: 1 },
    { startupId: 'beta', provider: 'workable', handle: 'acme', totalJobs: 2, berlinJobs: 1 },
    { startupId: 'gone', provider: 'lever', handle: 'missing', error: 'HTTP 404' },
  ]);
  // The flaky board recovered on its single retry; the missing one was tried twice in total.
  assert.equal(fake.calls.feeds.filter(u => u.includes('greenhouse')).length, 2);
  assert.equal(fake.calls.feeds.filter(u => u.includes('lever')).length, 2);
});

test('a week already recorded reads no job boards and posts nothing', async () => {
  const fake = fakeFetch({ planBody: { due: false, reason: 'recorded', week: plan.week } });
  assert.deepEqual(await run(fake), { outcome: 'recorded', failed: 0 });
  assert.equal(fake.calls.feeds.length, 0);
  assert.equal(fake.calls.posts.length, 0);
});

test('a Worker rejection fails the run so the workflow shows it', async () => {
  await assert.rejects(run(fakeFetch({ postStatus: 502 })), /HTTP 502 too_many_failures/);
});

test('missing configuration fails before any request', async () => {
  const fake = fakeFetch();
  await assert.rejects(collectJobs({ baseUrl: 'site.test', token: 'secret', fetchImpl: fake.impl }), /COLLECTOR_URL/);
  await assert.rejects(collectJobs({ baseUrl: 'https://site.test', token: '', fetchImpl: fake.impl }), /COLLECTOR_TOKEN/);
  assert.equal(fake.calls.auth.length + fake.calls.feeds.length, 0);
});
