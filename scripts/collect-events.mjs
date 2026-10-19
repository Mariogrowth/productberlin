// Berlin startup events collector, run by GitHub Actions in the collect workflow (at most every 6 hours).
// 1. Ask the Worker whether a refresh is due and which organisers' calendars to read (GET /api/internal/events).
// 2. Read each organiser's public iCal feed and keep its entries for the next three weeks.
// 3. Post the entries per organiser (POST). The Worker validates them, keeps in-person Berlin events, decides
//    startup or tech, and replaces each organiser's upcoming events; organisers whose feed failed keep theirs.
import { pathToFileURL } from 'node:url';

const DAY = 86_400_000;
const WINDOW_DAYS = 21;

const unescape = value => value.replace(/\\n/gi, '\n').replace(/\\([,;\\])/g, '$1');

/** Offset of a time zone from UTC at an instant, in milliseconds. */
function offset(timeZone, instant) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
    timeZone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).formatToParts(new Date(instant)).map(part => [part.type, part.value]));
  return Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute, +parts.second) - instant;
}

/** An iCal date-time as ISO-8601 UTC: `20261020T053000Z`, or local time with a TZID. All-day dates return null. */
export function icalTime(value, timeZone) {
  const m = /^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(Z?)$/.exec(value ?? '');
  if (!m) return null;
  const local = Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6]);
  if (m[7] === 'Z' || !timeZone) return new Date(local).toISOString();
  try {
    // Two passes settle the offset around daylight-saving changes.
    let utc = local - offset(timeZone, local);
    utc = local - offset(timeZone, utc);
    return new Date(utc).toISOString();
  } catch {
    return null;
  }
}

/** The events of an iCal document as { uid, title, startsAt, endsAt, location, url }; cancelled and all-day ones are skipped. */
export function parseCalendar(text) {
  const lines = text.replace(/\r?\n[ \t]/g, '').split(/\r?\n/);
  const events = [];
  let current = null;
  for (const line of lines) {
    if (line === 'BEGIN:VEVENT') { current = {}; continue; }
    if (line === 'END:VEVENT') { if (current) events.push(current); current = null; continue; }
    if (!current) continue;
    const colon = line.indexOf(':');
    if (colon < 0) continue;
    const [name, ...params] = line.slice(0, colon).split(';');
    current[name.toUpperCase()] = { value: line.slice(colon + 1), tzid: params.find(p => p.startsWith('TZID='))?.slice(5) };
  }
  return events.flatMap(event => {
    if (event.STATUS?.value?.toUpperCase() === 'CANCELLED' || !event.UID || !event.SUMMARY) return [];
    const startsAt = icalTime(event.DTSTART?.value, event.DTSTART?.tzid);
    if (!startsAt) return [];
    const description = unescape(event.DESCRIPTION?.value ?? '');
    const location = unescape(event.LOCATION?.value ?? '').trim();
    // Luma links the event page in the description ("Get up-to-date information at: …") or as the location.
    const url = event.URL?.value?.trim()
      || /https:\/\/(?:luma\.com|lu\.ma)\/[^\s]+/.exec(description)?.[0]
      || (/^https:\/\//.test(location) ? location : null);
    // Without a written location, use the "Address:" lines Luma adds to the description.
    const address = /^https?:/.test(location) || !location
      ? (/Address:\n([^\n]+)\n([^\n]+)/.exec(description)?.slice(1).filter(line => !/check event page/i.test(line)).join(', ') ?? '')
      : location;
    return [{
      uid: event.UID.value.trim(),
      title: unescape(event.SUMMARY.value).trim(),
      startsAt,
      endsAt: icalTime(event.DTEND?.value, event.DTEND?.tzid),
      location: address,
      url,
    }];
  });
}

export async function collectEvents({ baseUrl, token, fetchImpl = fetch, log = console.log, now = Date.now() }) {
  if (!baseUrl || !/^https?:\/\//.test(baseUrl)) throw new Error('Set COLLECTOR_URL to the site origin, e.g. https://product.berlin');
  if (!token) throw new Error('Set COLLECTOR_TOKEN');
  const endpoint = new URL('/api/internal/events', baseUrl);
  const headers = { Authorization: `Bearer ${token}` };
  const planResponse = await fetchImpl(endpoint, { headers });
  if (!planResponse.ok) throw new Error(`Events plan request failed: HTTP ${planResponse.status} ${await planResponse.text()}`);
  const plan = await planResponse.json();
  if (!plan.due) {
    log('Events were refreshed recently; nothing to do.');
    return { outcome: 'not_due', sources: 0, entries: 0, failed: 0 };
  }

  const read = [];
  let failed = 0;
  const queue = [...plan.sources];
  // A few calendars at a time; each feed is a public subscription link meant to be polled.
  await Promise.all(Array.from({ length: 4 }, async () => {
    while (queue.length) {
      const source = queue.shift();
      try {
        const response = await fetchImpl(source.feed, {
          headers: { 'User-Agent': 'ProductBerlinEvents/1.0 (+https://product.berlin)', Accept: 'text/calendar' },
          signal: AbortSignal.timeout(20_000),
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const body = await response.text();
        if (!body.includes('BEGIN:VCALENDAR')) throw new Error('Not an iCal feed');
        const entries = parseCalendar(body).filter(e => {
          const time = Date.parse(e.startsAt);
          return time >= now - DAY && time <= now + WINDOW_DAYS * DAY;
        });
        read.push({ id: source.id, entries });
      } catch (error) {
        failed++;
        log(`${source.id}: ${error.message}`);
      }
    }
  }));
  const entries = read.reduce((sum, source) => sum + source.entries.length, 0);
  log(`Read ${read.length}/${plan.sources.length} calendars, ${entries} upcoming entries.`);
  const response = await fetchImpl(endpoint, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify({ sources: read }),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`Storing events failed: HTTP ${response.status} ${body.error ?? ''}`.trim());
  log(`Worker kept ${body.events} Berlin events from ${body.sources} organisers.`);
  return { outcome: 'collected', sources: read.length, entries, failed, events: body.events };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const result = await collectEvents({ baseUrl: process.env.COLLECTOR_URL, token: process.env.COLLECTOR_TOKEN });
  if (process.env.GITHUB_STEP_SUMMARY) {
    const { appendFileSync } = await import('node:fs');
    appendFileSync(process.env.GITHUB_STEP_SUMMARY,
      `### Berlin events: ${result.outcome}\n\nCalendars read: ${result.sources} (failed: ${result.failed}), Berlin events kept: ${result.events ?? 0}.\n`);
  }
}
