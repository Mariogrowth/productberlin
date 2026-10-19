import assert from 'node:assert/strict';
import { test } from 'node:test';
import { collectEvents, icalTime, parseCalendar } from '../collect-events.mjs';

const NOW = Date.parse('2026-10-19T08:00:00Z');
const LUMA = [
  'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Luma//Merantix AI Campus//EN',
  'BEGIN:VEVENT', 'DTSTART:20261020T160000Z', 'DTEND:20261020T190000Z', 'UID:evt-1@events.lu.ma',
  'SUMMARY:UNCONFERENCE: Engineering AI Together #11',
  'DESCRIPTION:Get up-to-date information at: https://luma.com/unconference_11\\n\\nAd',
  ' dress:\\nMerantix AI Campus Berlin\\nBerlin\\, Berlin',
  'LOCATION:Merantix AI Campus\\, Max-Urich-Straße 3\\, 13355 Berlin\\, Germany', 'END:VEVENT',
  'BEGIN:VEVENT', 'DTSTART:20261021T170000Z', 'UID:evt-2@events.lu.ma', 'SUMMARY:Afterglow: GTC party',
  'DESCRIPTION:Get up-to-date information at: https://luma.com/pkd5csht\\n\\nAddress:\\nCheck event page for more details.',
  'LOCATION:https://luma.com/event/evt-2', 'END:VEVENT',
  'BEGIN:VEVENT', 'DTSTART:20261022T160000Z', 'UID:evt-3@events.lu.ma', 'SUMMARY:Cancelled talk', 'STATUS:CANCELLED', 'END:VEVENT',
  'BEGIN:VEVENT', 'DTSTART;VALUE=DATE:20261023', 'UID:evt-4@events.lu.ma', 'SUMMARY:All-day thing', 'END:VEVENT',
  'BEGIN:VEVENT', 'DTSTART:20261201T160000Z', 'UID:evt-5@events.lu.ma', 'SUMMARY:Far away', 'END:VEVENT',
  'END:VCALENDAR',
].join('\r\n');
const MEETUP = [
  'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'UID:event_316973164@meetup.com',
  'DTSTART;TZID=Europe/Berlin:20261021T180000', 'DTEND;TZID=Europe/Berlin:20261021T210000',
  'SUMMARY:PyData Berlin 2026 October Meetup', 'URL;VALUE=URI:https://www.meetup.com/pydata-berlin/events/316973164/', 'END:VEVENT',
  'END:VCALENDAR',
].join('\r\n');

test('iCal times: UTC, Berlin local time on both sides of the clock change, and all-day dates', () => {
  assert.equal(icalTime('20261020T053000Z'), '2026-10-20T05:30:00.000Z');
  assert.equal(icalTime('20261021T180000', 'Europe/Berlin'), '2026-10-21T16:00:00.000Z', 'summer time, UTC+2');
  assert.equal(icalTime('20261028T180000', 'Europe/Berlin'), '2026-10-28T17:00:00.000Z', 'winter time, UTC+1');
  assert.equal(icalTime('20261023'), null);
  assert.equal(icalTime('20261021T180000', 'Not/AZone'), null);
});

test('Luma and Meetup calendars: links, addresses, unfolding and escapes; cancelled and all-day events skipped', () => {
  const luma = parseCalendar(LUMA);
  assert.deepEqual(luma.map(e => e.uid), ['evt-1@events.lu.ma', 'evt-2@events.lu.ma', 'evt-5@events.lu.ma']);
  assert.deepEqual(luma[0], {
    uid: 'evt-1@events.lu.ma', title: 'UNCONFERENCE: Engineering AI Together #11', startsAt: '2026-10-20T16:00:00.000Z',
    endsAt: '2026-10-20T19:00:00.000Z', location: 'Merantix AI Campus, Max-Urich-Straße 3, 13355 Berlin, Germany', url: 'https://luma.com/unconference_11',
  });
  assert.equal(luma[1].location, '', 'a guest-only address stays empty');
  assert.equal(luma[1].url, 'https://luma.com/pkd5csht');
  const [pydata] = parseCalendar(MEETUP);
  assert.equal(pydata.startsAt, '2026-10-21T16:00:00.000Z');
  assert.equal(pydata.url, 'https://www.meetup.com/pydata-berlin/events/316973164/');
});

function fakeFetch({ due = true } = {}) {
  const calls = { feeds: [], posts: [] };
  const plan = { due, sources: [
    { id: 'merantix-ai-campus', feed: 'https://cal.example/luma.ics' },
    { id: 'pydata-berlin', feed: 'https://cal.example/meetup.ics' },
    { id: 'broken', feed: 'https://cal.example/broken.ics' },
  ] };
  const impl = async (url, init = {}) => {
    const target = String(url);
    if (target.startsWith('https://site.test/')) {
      if (init.method === 'POST') { calls.posts.push(JSON.parse(init.body)); return Response.json({ sources: 2, events: 3 }); }
      return Response.json(plan);
    }
    calls.feeds.push(target);
    if (target.endsWith('luma.ics')) return new Response(LUMA);
    if (target.endsWith('meetup.ics')) return new Response(MEETUP);
    return new Response('<html>gone</html>', { status: 200 });
  };
  return { impl, calls };
}
const run = (fake) => collectEvents({ baseUrl: 'https://site.test', token: 'secret', fetchImpl: fake.impl, log: () => {}, now: NOW });

test('reads every calendar, posts the next three weeks per organiser, and leaves out a broken feed', async () => {
  const fake = fakeFetch();
  assert.deepEqual(await run(fake), { outcome: 'collected', sources: 2, entries: 3, failed: 1, events: 3 });
  const posted = fake.calls.posts[0].sources;
  assert.deepEqual(posted.map(s => s.id).sort(), ['merantix-ai-campus', 'pydata-berlin'], 'the broken feed keeps its stored events');
  assert.deepEqual(posted.find(s => s.id === 'merantix-ai-campus').entries.map(e => e.uid), ['evt-1@events.lu.ma', 'evt-2@events.lu.ma'], 'December is outside the window');
});

test('when a refresh is not due, no calendar is read', async () => {
  const fake = fakeFetch({ due: false });
  assert.equal((await run(fake)).outcome, 'not_due');
  assert.equal(fake.calls.feeds.length + fake.calls.posts.length, 0);
});
