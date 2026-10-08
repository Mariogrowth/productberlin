import assert from 'node:assert/strict';
import { test } from 'node:test';
import { collect, compactFeed, headlineOf, protectNames, translateHeadlines, unprotectNames } from '../collect-news.mjs';

const rss = id => `<rss version="2.0"><channel><item><title>Noxtua sammelt ${id} ein - Handelsblatt</title><link>https://news.google.com/rss/articles/${id}</link><guid isPermaLink="false">${id}</guid><pubDate>Wed, 30 Sep 2026 09:00:00 GMT</pubDate><description>long html</description><source url="https://www.handelsblatt.com">Handelsblatt</source></item></channel></rss>`;
const plan = { due: true, collectionKey: '2026-10-05-trusted-v1', week: '2026-09-28 – 2026-10-04',
  start: '2026-09-28T00:00:00.000Z', end: '2026-10-05T00:00:00.000Z', searches: [
  { query: 'after:2026-09-28 before:2026-10-05 ("Noxtua")', language: 'en' },
  { query: 'after:2026-09-28 before:2026-10-05 ("Noxtua")', language: 'de' },
] };

function fakeFetch({ planBody = plan, google = () => 200, postStatus = 200, deepl = 200 } = {}) {
  const calls = { google: [], posts: [], headers: [], deepl: [] };
  const impl = async (url, init = {}) => {
    const target = new URL(url);
    if (target.hostname.endsWith('deepl.com')) {
      const request = JSON.parse(init.body);
      calls.deepl.push({ host: target.hostname, auth: init.headers.Authorization, request });
      if (deepl !== 200) return new Response('Quota exceeded', { status: deepl });
      return Response.json({ translations: request.text.map(text => ({ detected_source_language: 'DE', text: text.replace(' sammelt ', ' raises ').replace(' ein', '') })) });
    }
    if (target.hostname === 'news.google.com') {
      calls.google.push(`${target.searchParams.get('hl')} ${target.searchParams.get('q')}`);
      const status = google(calls.google.length);
      return new Response(status === 200 ? rss(`a${calls.google.length}`) : 'Unavailable', { status });
    }
    calls.headers.push(init.headers?.Authorization);
    if (init.method === 'POST') {
      calls.posts.push(JSON.parse(init.body));
      return Response.json(postStatus === 200 ? { outcome: 'published', collectionKey: plan.collectionKey } : { error: 'collection_failed', message: 'boom' }, { status: postStatus });
    }
    return Response.json(planBody);
  };
  return { impl, calls };
}
const run = (fake, extra = {}) => collect({ baseUrl: 'https://site.test', token: 'secret', fetchImpl: fake.impl, pauseMs: 0, retryPauseMs: 0, log: () => {}, ...extra });

test('fetches exactly the planned searches and posts compact parsed feeds with the bearer token', async () => {
  const fake = fakeFetch();
  assert.deepEqual(await run(fake), { outcome: 'published', failed: 0 });
  assert.deepEqual(fake.calls.google, plan.searches.map(s => `${s.language} ${s.query}`));
  assert.ok(fake.calls.headers.every(h => h === 'Bearer secret'));
  const posted = fake.calls.posts.single ?? fake.calls.posts[0];
  assert.equal(posted.collectionKey, plan.collectionKey);
  const item = posted.results[0].feed.rss.channel.item[0];
  assert.deepEqual(Object.keys(item).sort(), ['guid', 'link', 'pubDate', 'source', 'title']);
  assert.equal(item.source['@_url'], 'https://www.handelsblatt.com');
});

test('a week that is not due makes no Google requests and posts nothing', async () => {
  const fake = fakeFetch({ planBody: { due: false, reason: 'published', collectionKey: plan.collectionKey } });
  assert.deepEqual(await run(fake), { outcome: 'published', failed: 0 });
  assert.equal(fake.calls.google.length, 0);
  assert.equal(fake.calls.posts.length, 0);
});

test('failed searches are retried once; persistent failures are sent as errors for the Worker to judge', async () => {
  const fake = fakeFetch({ google: n => (n === 1 || n === 2 || n === 4 ? 503 : 200) });
  const result = await run(fake);
  assert.equal(fake.calls.google.length, 4, 'two searches plus one retry each');
  assert.equal(result.failed, 1);
  const [first, second] = fake.calls.posts[0].results;
  assert.ok(first.feed && !first.error, 'recovered on retry');
  assert.equal(second.error, 'Google News returned HTTP 503');
});

test('Worker rejections and missing configuration fail the job loudly', async () => {
  await assert.rejects(run(fakeFetch({ postStatus: 502 })), /Collection failed: HTTP 502 boom/);
  await assert.rejects(collect({ baseUrl: '', token: 'x' }), /COLLECTOR_URL/);
  await assert.rejects(collect({ baseUrl: 'https://site.test', token: '' }), /COLLECTOR_TOKEN/);
});

test('compaction keeps only in-week trusted items and the fields the Worker reads', () => {
  const source = url => ({ '#text': 'P', '@_url': url });
  const item = (pubDate, url) => ({ title: 't', link: 'l', guid: 'g', pubDate, source: source(url), description: 'x' });
  const week = { start: plan.start, end: plan.end };
  const feed = { rss: { channel: { item: [
    item('Wed, 30 Sep 2026 09:00:00 GMT', 'https://www.handelsblatt.com'),
    item('Wed, 30 Sep 2026 09:00:00 GMT', 'https://www.youtube.com'),
    item('Sun, 27 Sep 2026 23:59:59 GMT', 'https://www.handelsblatt.com'),
    item('Mon, 05 Oct 2026 00:00:00 GMT', 'https://www.handelsblatt.com'),
  ] } } };
  assert.deepEqual(compactFeed(feed, week), { rss: { channel: { item: [
    { title: 't', link: 'l', guid: 'g', pubDate: 'Wed, 30 Sep 2026 09:00:00 GMT', source: source('https://www.handelsblatt.com') },
  ] } } });
  assert.deepEqual(compactFeed({}, week), { rss: { channel: { item: [] } } });
});

test('German headlines are translated with company names protected; the original stays for matching', async () => {
  const fake = fakeFetch();
  await run(fake, { deeplApiKey: 'key:fx' });
  assert.equal(fake.calls.deepl.length, 1);
  const { host, auth, request } = fake.calls.deepl[0];
  assert.equal(host, 'api-free.deepl.com', 'free-plan keys end in :fx');
  assert.equal(auth, 'DeepL-Auth-Key key:fx');
  assert.deepEqual({ ...request, text: undefined }, { target_lang: 'EN-GB', tag_handling: 'xml', ignore_tags: ['x'], text: undefined });
  assert.ok(request.text.every(t => t.startsWith('<x>Noxtua</x> sammelt')), JSON.stringify(request.text));
  const item = fake.calls.posts[0].results[0].feed.rss.channel.item[0];
  assert.equal(item.title, 'Noxtua sammelt a1 ein - Handelsblatt', 'the original title is untouched');
  assert.equal(item.translatedTitle, 'Noxtua raises a1');
});

test('without a DeepL key, or when DeepL fails, collection still posts untranslated headlines', async () => {
  for (const [options, deepl] of [[{}, 200], [{ deeplApiKey: 'paid-key' }, 456]]) {
    const fake = fakeFetch({ deepl });
    assert.equal((await run(fake, options)).outcome, 'published');
    assert.equal(fake.calls.posts[0].results[0].feed.rss.channel.item[0].translatedTitle, undefined);
    if (options.deeplApiKey) assert.equal(fake.calls.deepl[0].host, 'api.deepl.com', 'paid keys use the paid endpoint');
  }
});

test('English headlines and unchanged texts are not stored as translations', async () => {
  const fetchImpl = async (_url, init) => Response.json({ translations: JSON.parse(init.body).text.map((text, i) =>
    i === 0 ? { detected_source_language: 'EN', text } : { detected_source_language: 'DE', text: unprotectNames(text) }) });
  const result = await translateHeadlines(['Langdock hits €50M ARR', 'n8n'], { apiKey: 'k:fx', fetchImpl, log: () => {} });
  assert.equal(result.size, 0);
});

test('name protection keeps whole catalogue names and escapes XML', () => {
  assert.equal(protectNames('Nox gegen Nightjet: Nox Mobility startet'), '<x>Nox</x> gegen Nightjet: <x>Nox Mobility</x> startet');
  assert.equal(protectNames('Noxtua & C.H.BECK <Mehrheit>'), '<x>Noxtua</x> &amp; C.H.BECK &lt;Mehrheit&gt;');
  assert.equal(protectNames('Langdock hits €50M ARR'), '<x>Langdock</x> hits €50M ARR');
  for (const text of ['Nox gegen Nightjet: Nox Mobility startet', 'Noxtua & C.H.BECK <Mehrheit>']) assert.equal(unprotectNames(protectNames(text)), text);
  assert.equal(headlineOf({ title: 'Der Zinsstreit geht weiter - WiWo', source: { '#text': 'WiWo' } }), 'Der Zinsstreit geht weiter');
});
