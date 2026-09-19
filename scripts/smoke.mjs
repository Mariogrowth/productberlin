import assert from 'node:assert/strict';

const origin = process.env.DEPLOY_URL;
const expectedVersion = process.env.BUILD_SHA;
if (!origin?.startsWith('https://') || !expectedVersion) throw new Error('Set DEPLOY_URL (https) and BUILD_SHA');
async function get(path) {
  const response = await fetch(new URL(path, origin), { signal: AbortSignal.timeout(15_000) });
  assert.equal(response.status, 200, `${path}: ${response.status}`);
  return response;
}
// Allow a short propagation window after a Worker deployment.
let lastError;
for (let attempt = 0; attempt < 6; attempt++) {
  try {
    const health = await (await get('/api/health')).json();
    assert.equal(health.version, expectedVersion);
    const ranking = await (await get('/api/rankings/weekly')).json();
    const allowedCounts = process.env.REQUIRE_RANKING === 'true' ? [10] : [0, 10];
    assert.ok(allowedCounts.includes(ranking.startups.length), 'Unexpected ranking size');
    assert.match(await (await get('/')).text(), /productberlin\.js/);
    const javascript = await get('/productberlin.js');
    assert.match(javascript.headers.get('content-type') ?? '', /javascript/);
    assert.ok((await javascript.text()).length > 0, 'Empty JavaScript bundle');
    assert.match((await get('/styles.css')).headers.get('content-type') ?? '', /text\/css/);
    console.log(`Deployment healthy: ${expectedVersion}`);
    lastError = undefined;
    break;
  } catch (error) {
    lastError = error;
    await new Promise(resolve => setTimeout(resolve, 5000));
  }
}
if (lastError) throw lastError;
