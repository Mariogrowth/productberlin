import assert from 'node:assert/strict';
import { test } from 'node:test';
import { collectFeeds, feedItems, itemKey } from '../collect-feeds.mjs';
import { parseRssXml } from '../../api-worker/rss-parser.mjs';

const NOW = Date.parse('2026-10-18T12:00:00Z');
const day = n => new Date(NOW - n * 86_400_000).toUTCString();
const rss = items => `<?xml version="1.0"?><rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel><title>Feed</title>${
  items.map(([title, link, date, body = '']) => `<item><title>${title}</title><link>${link}</link><pubDate>${date}</pubDate><description><![CDATA[<p>${body}</p>]]></description></item>`).join('')
}</channel></rss>`;
const FEEDS = {
  'https://ds.example/feed/': rss([
    ['Procuros: Berliner Logistik-Startup erh&#228;lt 20 Mio. Euro', 'https://ds.example/procuros?utm_source=rss', day(1), 'Das Berliner Startup Procuros sammelt 20 Millionen Euro ein.'],
    ['Wiener Startup reebuild holt 3 Millionen Euro', 'https://ds.example/reebuild', day(2)],
    ['Known story', 'https://ds.example/known', day(2)],
    ['Too old', 'https://ds.example/old', day(30)],
  ]),
  'https://ds.example/feed/?paged=2': rss([['Older still', 'https://ds.example/older', day(40)]]),
  'https://tt.example/feed/': rss([['N26 holt Ex-Postbank-Finanzchef als CFO', 'https://tt.example/n26-cfo', day(3)]]),
};
const plan = {
  feeds: [{ id: 'ds', name: 'deutsche-startups.de', url: 'https://ds.example/feed/', pages: 2 }, { id: 'tt', name: 'Trending Topics', url: 'https://tt.example/feed/', pages: 1 }],
  known: ['https://ds.example/known'],
  since: new Date(NOW - 21 * 86_400_000).toISOString(),
};

function fakeFetch() {
  const calls = { feeds: [], posts: [], deepl: 0, auth: [] };
  const impl = async (url, init = {}) => {
    const target = String(url);
    if (target.startsWith('https://site.test/')) {
      calls.auth.push(init.headers?.Authorization);
      if (init.method === 'POST') {
        const body = JSON.parse(init.body);
        calls.posts.push(body);
        return Response.json({ received: body.items.length, valid: body.items.length, stored: body.items.length });
      }
      return Response.json(plan);
    }
    if (target.includes('deepl.com')) {
      calls.deepl++;
      const request = JSON.parse(init.body);
      return Response.json({ translations: request.text.map(text => ({ detected_source_language: 'DE', text: `EN: ${text}` })) });
    }
    calls.feeds.push(target);
    return target in FEEDS ? new Response(FEEDS[target]) : new Response('missing', { status: 404 });
  };
  return { impl, calls };
}
const run = (fake, extra = {}) => collectFeeds({ baseUrl: 'https://site.test', token: 'secret', fetchImpl: fake.impl, log: () => {}, ...extra });

test('feed items keep title, link, date and a plain-text teaser; unusable items are skipped', () => {
  const parsed = parseRssXml(rss([
    ['Procuros &amp; Co', 'https://ds.example/a', day(1), '<b>Berliner</b> Startup'],
    ['No date', 'https://ds.example/b', 'not a date'],
    ['Plain http', 'http://ds.example/c', day(1)],
  ]));
  assert.deepEqual(feedItems(parsed, { id: 'ds' }), [
    { feed: 'ds', title: 'Procuros & Co', link: 'https://ds.example/a', publishedAt: new Date(Date.parse(day(1))).toISOString(), teaser: 'Berliner Startup' },
  ]);
  assert.equal(itemKey('https://ds.example/a/?utm_source=rss#top'), 'https://ds.example/a');
});

test('only new, in-window items are posted, with translated titles', async () => {
  const fake = fakeFetch();
  const result = await run(fake, { deeplApiKey: 'deepl:fx' });
  assert.deepEqual(result, { read: 6, new: 3, stored: 3 });
  assert.ok(fake.calls.auth.every(h => h === 'Bearer secret'));
  // Page 1 still reached into the window, so page 2 was read; its items are all older, so paging stopped there.
  assert.deepEqual(fake.calls.feeds, ['https://ds.example/feed/', 'https://ds.example/feed/?paged=2', 'https://tt.example/feed/']);
  const posted = fake.calls.posts.flatMap(p => p.items);
  assert.deepEqual(posted.map(i => i.link).sort(), ['https://ds.example/procuros?utm_source=rss', 'https://ds.example/reebuild', 'https://tt.example/n26-cfo']);
  assert.deepEqual(Object.keys(posted[0]).sort(), ['feed', 'link', 'publishedAt', 'title', 'translatedTitle']);
  assert.equal(posted.find(i => i.link.includes('n26')).translatedTitle, 'EN: N26 holt Ex-Postbank-Finanzchef als CFO');
});

test('without a DeepL key, items are posted in their original language', async () => {
  const fake = fakeFetch();
  await run(fake);
  assert.equal(fake.calls.deepl, 0);
  assert.ok(fake.calls.posts.flatMap(p => p.items).every(i => i.translatedTitle === undefined));
});

test('a feed that fails is skipped; the others still post', async () => {
  const fake = fakeFetch();
  plan.feeds.push({ id: 'down', name: 'Down', url: 'https://down.example/feed/', pages: 1 });
  try {
    assert.equal((await run(fake)).new, 3);
  } finally {
    plan.feeds.pop();
  }
});

test('nothing new means no post', async () => {
  const fake = fakeFetch();
  const original = plan.known;
  plan.known = ['https://ds.example/procuros', 'https://ds.example/reebuild', 'https://ds.example/known', 'https://tt.example/n26-cfo'];
  try {
    assert.deepEqual(await run(fake), { read: 6, new: 0, stored: 0 });
    assert.equal(fake.calls.posts.length, 0);
  } finally {
    plan.known = original;
  }
});
