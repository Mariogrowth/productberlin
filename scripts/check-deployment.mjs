import assert from 'node:assert/strict';
import { stat } from 'node:fs/promises';

// Fail before migrations or uploads. Never print credential values.
const required = ['CLOUDFLARE_API_TOKEN', 'CLOUDFLARE_ACCOUNT_ID', 'D1_DATABASE_ID', 'DEPLOY_URL', 'BUILD_SHA', 'TARGET_ENV'];
const missing = required.filter(name => !process.env[name]?.trim());
assert.equal(missing.length, 0, `Missing GitHub environment settings: ${missing.join(', ')}`);
assert.ok(['staging', 'production'].includes(process.env.TARGET_ENV), 'Invalid deployment environment');
assert.match(process.env.BUILD_SHA, /^[0-9a-f]{40}$/, 'BUILD_SHA must be a full Git commit SHA');
assert.match(process.env.CLOUDFLARE_ACCOUNT_ID, /^[0-9a-f]{32}$/i, 'Invalid Cloudflare account ID');
const url = new URL(process.env.DEPLOY_URL);
assert.ok(url.protocol === 'https:' && !url.username && !url.password && !url.search && !url.hash && url.pathname === '/',
  'DEPLOY_URL must be an HTTPS origin, such as https://productberlin-staging.example.workers.dev');

for (const path of [
  'api-worker/entry.mjs',
  'api-worker/build/dist/js/productionLibrary/Productberlin-api-worker.mjs',
  'webApp/build/dist/js/productionExecutable/index.html',
  'webApp/build/dist/js/productionExecutable/productberlin.js',
  'webApp/build/dist/js/productionExecutable/styles.css',
  'cloudflare/migrations/0001_initial.sql',
]) {
  const file = await stat(path).catch(() => null);
  assert.ok(file?.isFile() && file.size > 0, `Missing or empty deployment file: ${path}`);
}
console.log('Deployment settings and required artifacts are present.');
