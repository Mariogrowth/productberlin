import assert from 'node:assert/strict';
import { test } from 'node:test';
import { catalogueNames, findCandidates, isDue, readRecentItems, summary } from '../discover-companies.mjs';

const known = catalogueNames([{ name: 'finmid', aliases: [] }, { name: 'Kuro', aliases: ['Kuro Engineering'] }, { name: 'Raisin', aliases: ['WeltSparen'] }]);
const item = (title, teaser = '', publishedAt = '2026-10-14T09:00:00.000Z') =>
  ({ title, teaser, source: 'Trending Topics', link: `https://news.example/${encodeURIComponent(title)}`, publishedAt });

test('names Berlin startups outside the catalogue from headlines and teasers', () => {
  const candidates = findCandidates([
    item('Procuros: Berliner Logistik-Startup erhält 20 Mio. Euro'),
    item('Neue Runde für Liqid', 'Der Berliner Robo-Advisor-Anbieter Liqid sichert sich 33 Millionen Euro.'),
    item('Berlin-based Deepslate raises €7.7M for European voice AI'),
    item('Berliner KI-Startup Lumen startet Agenten für Steuerberater'),
  ], known);
  assert.deepEqual(candidates.map(c => c.name).sort(), ['Deepslate', 'Liqid', 'Lumen', 'Procuros']);
  assert.equal(candidates.find(c => c.name === 'Liqid').title, 'Neue Runde für Liqid');
});

test('skips catalogue companies, their aliases and generic words', () => {
  const candidates = findCandidates([
    item('finmid: Berliner Fintech erhält 17 Millionen Euro'),
    item('Kuro Engineering: Berliner ConTech-Startup sammelt 10 Millionen'),
    item('Berliner Startup WeltSparen wächst'),
    item('Berlin-based startup raises money'),
    item('Berliner Startup erhält Förderung'),
    item('Matcha Cookies & Carrot Cake: Berliner Start-up will Backmischungen revolutionieren'),
    item('Berlin-based Cherry Ventures backs a new fund'),
    item('Interview', 'Das Berliner Startup 10 Millionen Nutzer erreicht'),
    { ...item('Christian Lindner investiert in Berliner Start-up', 'Christian Lindner investiert in Berliner Start-up  WirtschaftsWoche'), source: 'WirtschaftsWoche' },
  ], known);
  assert.deepEqual(candidates, []);
});

test('one entry per company, preferring a funding or launch report, then the newest', () => {
  const candidates = findCandidates([
    item('Berliner Startup Procuros im Porträt', '', '2026-10-16T09:00:00.000Z'),
    item('Procuros: Berliner Logistik-Startup erhält 20 Mio. Euro', '', '2026-10-12T09:00:00.000Z'),
    item('Berlin-based Ostrom expands to Spain', '', '2026-10-15T09:00:00.000Z'),
  ], known);
  assert.deepEqual(candidates.map(c => [c.name, c.event]), [['Procuros', true], ['Ostrom', false]]);
  assert.match(candidates[0].title, /20 Mio/);
});

test('the summary is a review table, never an automatic change', () => {
  const text = summary(findCandidates([item('Procuros: Berliner Logistik-Startup erhält 20 Mio. Euro | exklusiv')], known));
  assert.match(text, /\| Procuros \| \[Procuros: Berliner Logistik-Startup erhält 20 Mio\. Euro \\\| exklusiv\]\(https:\/\/news\.example\//);
  assert.match(text, /nothing is added automatically/);
  assert.match(summary([]), /No Berlin startups outside the catalogue/);
});

test('runs Mondays at 07:00 UTC or when forced', () => {
  assert.equal(isDue(Date.parse('2026-10-19T07:20:00Z'), false), true);
  assert.equal(isDue(Date.parse('2026-10-19T08:20:00Z'), false), false);
  assert.equal(isDue(Date.parse('2026-10-20T07:20:00Z'), false), false);
  assert.equal(isDue(Date.parse('2026-10-20T07:20:00Z'), true), true);
});

test('reads the past week from feeds and searches once per link; a failing source is skipped', async () => {
  const now = Date.parse('2026-10-19T07:00:00Z');
  const rss = items => `<?xml version="1.0"?><rss version="2.0"><channel><title>F</title>${items.map(([title, link, date, source]) =>
    `<item><title>${title}</title><link>${link}</link><pubDate>${new Date(date).toUTCString()}</pubDate>${source ? `<source url="https://tt.example">${source}</source>` : ''}</item>`).join('')}</channel></rss>`;
  const requested = [];
  const fetchImpl = async url => {
    const target = String(url);
    requested.push(target);
    if (target === 'https://feed.example/feed/') {
      return new Response(rss([['Procuros: Berliner Logistik-Startup erhält 20 Mio. Euro', 'https://feed.example/procuros', '2026-10-12T09:00:00Z'],
        ['Procuros: Berliner Logistik-Startup erhält 20 Mio. Euro', 'https://feed.example/procuros', '2026-10-12T09:00:00Z']]));
    }
    if (target === 'https://feed.example/feed/?paged=2') return new Response(rss([['Too old', 'https://feed.example/old', '2026-10-01T09:00:00Z']]));
    if (target.includes('news.google.com') && target.includes('Fintech')) {
      return new Response(rss([['Berliner Fintech Liqid holt 33 Millionen - extraETF', 'https://news.google.com/rss/articles/liqid', '2026-10-15T09:00:00Z', 'extraETF']]));
    }
    return new Response('blocked', { status: 503 });
  };
  const items = await readRecentItems({ feeds: [{ id: 'f', name: 'Feed', url: 'https://feed.example/feed/', pages: 3 }], now, fetchImpl, log: () => {}, pause: 0 });
  assert.deepEqual(items.map(i => [i.title.slice(0, 20), i.source]), [['Procuros: Berliner L', 'Feed'], ['Berliner Fintech Liq', 'extraETF']]);
  assert.equal(items[1].title, 'Berliner Fintech Liqid holt 33 Millionen', 'the publisher suffix is not part of the headline');
  assert.ok(!requested.includes('https://feed.example/feed/?paged=3'), 'stops paging once a page reaches past the week');
  assert.ok(requested.every(url => !url.includes('site.test')), 'never contacts the Worker');
});
