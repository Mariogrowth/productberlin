// Weekly hiring collector, run by GitHub Actions after the news collector.
// 1. Ask the Worker for its plan (GET /api/internal/hiring): is this week still unrecorded, and which job boards to read.
// 2. Read each board's public job feed and count open roles in total and in Berlin, retrying failures once.
// 3. Send only the counts back (POST); the Worker validates them and stores the week in one transaction.
// Recorded for ranking experiments only: nothing on the site reads these counts yet.
import { pathToFileURL } from 'node:url';

const BERLIN = /berlin/i;
const berlinIn = (...places) => places.flat().some(place => typeof place === 'string' && BERLIN.test(place));
const count = (jobs, places) => ({ totalJobs: jobs.length, berlinJobs: jobs.filter(job => berlinIn(places(job))).length });
const decodeXml = s => s.replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&quot;', '"').replaceAll('&apos;', "'").replaceAll('&amp;', '&');

/** Each provider's public feed: where it is, and how to count its roles. `read` returns the response text or throws. */
export const PROVIDERS = {
  personio: {
    async count(handle, read) {
      const xml = await read(`https://${handle}.jobs.personio.de/xml`);
      if (!/<workzag-jobs[\s>]/.test(xml)) throw new Error('Not a Personio job feed');
      const positions = [...xml.matchAll(/<position>([\s\S]*?)<\/position>/g)].map(m => m[1]);
      return count(positions, p => [...p.matchAll(/<office>([\s\S]*?)<\/office>/g)].map(m => decodeXml(m[1])));
    },
  },
  ashby: {
    async count(handle, read) {
      const { jobs } = JSON.parse(await read(`https://api.ashbyhq.com/posting-api/job-board/${handle}`));
      return count(jobs ?? [], job => [job.location, job.address?.postalAddress?.addressLocality,
        ...(job.secondaryLocations ?? []).map(l => [l.location, l.address?.addressLocality])].flat());
    },
  },
  greenhouse: {
    async count(handle, read) {
      const { jobs } = JSON.parse(await read(`https://boards-api.greenhouse.io/v1/boards/${handle}/jobs`));
      return count(jobs ?? [], job => [job.location?.name]);
    },
  },
  lever: {
    async count(handle, read) {
      const jobs = JSON.parse(await read(`https://api.lever.co/v0/postings/${handle}?mode=json`));
      if (!Array.isArray(jobs)) throw new Error('Not a Lever job feed');
      return count(jobs, job => [job.categories?.location, ...(job.categories?.allLocations ?? [])]);
    },
  },
  workable: {
    async count(handle, read) {
      const { jobs } = JSON.parse(await read(`https://apply.workable.com/api/v1/widget/accounts/${handle}`));
      return count(jobs ?? [], job => [job.city, ...(job.locations ?? []).map(l => l.city)]);
    },
  },
  recruitee: {
    async count(handle, read) {
      const { offers } = JSON.parse(await read(`https://${handle}.recruitee.com/api/offers/`));
      return count(offers ?? [], offer => [offer.city, offer.location, ...(offer.locations ?? []).map(l => l.city)]);
    },
  },
  smartrecruiters: {
    // Paged, 100 roles per page. Very large boards stop after 10 pages: Berlin roles are then a lower bound.
    async count(handle, read) {
      let total = 0, berlin = 0;
      for (let offset = 0, page = 0; page < 10; page++, offset += 100) {
        const body = JSON.parse(await read(`https://api.smartrecruiters.com/v1/companies/${handle}/postings?limit=100&offset=${offset}`));
        total = body.totalFound ?? 0;
        const jobs = body.content ?? [];
        berlin += jobs.filter(job => berlinIn([job.location?.city, job.location?.fullLocation])).length;
        if (!jobs.length || offset + jobs.length >= total) break;
      }
      return { totalJobs: total, berlinJobs: Math.min(berlin, total) };
    },
  },
};

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

export async function collectJobs({ baseUrl, token, fetchImpl = fetch, concurrency = 4, retryPauseMs = 5000, log = console.log }) {
  if (!baseUrl || !/^https?:\/\//.test(baseUrl)) throw new Error('Set COLLECTOR_URL to the site origin, e.g. https://product.berlin');
  if (!token) throw new Error('Set COLLECTOR_TOKEN');
  const endpoint = new URL('/api/internal/hiring', baseUrl);
  const headers = { Authorization: `Bearer ${token}` };

  const planResponse = await fetchImpl(endpoint, { headers });
  if (!planResponse.ok) throw new Error(`Hiring plan request failed: HTTP ${planResponse.status} ${await planResponse.text()}`);
  const plan = await planResponse.json();
  if (!plan.due) {
    log(`No hiring counts to record (${plan.reason}, week of ${plan.week}).`);
    return { outcome: plan.reason, failed: 0 };
  }
  log(`Recording hiring for the week of ${plan.week}: ${plan.boards.length} job boards.`);

  const read = async url => {
    const response = await fetchImpl(url, { headers: { Accept: 'application/json, application/xml' }, signal: AbortSignal.timeout(20_000) });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.text();
  };
  const results = plan.boards.map(board => ({ ...board }));
  const countOne = async result => {
    try {
      const provider = PROVIDERS[result.provider];
      if (!provider) throw new Error(`Unsupported provider ${result.provider}`);
      Object.assign(result, await provider.count(result.handle, read));
      delete result.error;
    } catch (error) {
      result.error = error.message;
    }
  };
  // A few boards at a time: they are on different hosts, and each provider serves these feeds publicly.
  const runAll = async list => {
    const queue = [...list];
    await Promise.all(Array.from({ length: Math.min(concurrency, queue.length) }, async () => {
      while (queue.length) await countOne(queue.shift());
    }));
  };
  await runAll(results);
  const failedFirst = results.filter(r => r.error);
  if (failedFirst.length) {
    log(`${failedFirst.length} job boards failed; retrying once after ${retryPauseMs / 1000} s.`);
    await sleep(retryPauseMs);
    await runAll(failedFirst);
  }
  const failures = results.filter(r => r.error);
  for (const r of failures) log(`  ${r.startupId} (${r.provider}/${r.handle}): ${r.error}`);
  log(`Counted ${results.length - failures.length}/${results.length} job boards.`);

  const response = await fetchImpl(endpoint, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      week: plan.week,
      results: results.map(({ startupId, provider, handle, totalJobs, berlinJobs, error }) =>
        (error ? { startupId, provider, handle, error } : { startupId, provider, handle, totalJobs, berlinJobs })),
    }),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`Recording hiring counts failed: HTTP ${response.status} ${body.error ?? ''}`.trim());
  log(`Worker: ${body.outcome} (week of ${body.week}, ${body.boards} boards, ${body.failed} missing).`);
  return { outcome: body.outcome, failed: failures.length };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const result = await collectJobs({ baseUrl: process.env.COLLECTOR_URL, token: process.env.COLLECTOR_TOKEN });
  if (process.env.GITHUB_STEP_SUMMARY) {
    const { appendFileSync } = await import('node:fs');
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, `### Weekly hiring counts: ${result.outcome}\n\nJob boards that failed after retry: ${result.failed}\n`);
  }
}
