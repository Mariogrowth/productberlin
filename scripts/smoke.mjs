import assert from 'node:assert/strict';
import { validateDeploymentUrl } from './deployment-url.mjs';

const origin = process.env.DEPLOY_URL;
const expectedVersion = process.env.BUILD_SHA;
if (!origin?.startsWith('https://') || !expectedVersion) throw new Error('Set DEPLOY_URL (https) and BUILD_SHA');
if (process.env.TARGET_ENV) validateDeploymentUrl(origin, process.env.TARGET_ENV);
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
    assert.equal(health.version, expectedVersion,
      `${origin} serves commit ${health.version}; expected ${expectedVersion}. ` +
      'Check DEPLOY_URL for the selected GitHub environment and the Worker deployment version.');
    const ranking = await (await get('/api/rankings/weekly')).json();
    const count = ranking.startups.length;
    assert.ok(count >= (process.env.REQUIRE_RANKING === 'true' ? 1 : 0) && count <= 10, 'Unexpected ranking size');
    if (!ranking.isMock && count > 0) {
      assert.ok(ranking.startups.every(s => s.mentionCount > 0 && s.news.length <= 5), 'Invalid live ranking');
    }
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
