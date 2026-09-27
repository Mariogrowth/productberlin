import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { validateDeploymentUrl } from '../deployment-url.mjs';

test('production accepts its own Worker even when the account subdomain contains staging', () => {
  const origin = 'https://productberlin-production.productberlin-staging.workers.dev';
  assert.equal(validateDeploymentUrl(origin, 'production').origin, origin);
});

test('staging accepts its matching Worker and custom domains remain supported', () => {
  for (const [origin, environment] of [
    ['https://productberlin-staging.example.workers.dev/', 'staging'],
    ['https://product.berlin', 'production'],
    ['https://staging.example.com', 'staging'],
  ]) assert.equal(validateDeploymentUrl(origin, environment).origin, origin.replace(/\/$/, ''));
});

test('cross-environment URLs fail with the environment setting to fix', () => {
  for (const [target, other] of [['production', 'staging'], ['staging', 'production']]) {
    assert.throws(() => validateDeploymentUrl(`https://productberlin-${other}.example.workers.dev`, target),
      new RegExp(`Set DEPLOY_URL in the GitHub ${target} environment`));
  }
});

test('account-only and version-preview workers.dev URLs cannot masquerade as the deployed Worker', () => {
  for (const hostname of ['workers.dev', 'example.workers.dev', 'preview.productberlin-production.example.workers.dev']) {
    assert.throws(() => validateDeploymentUrl(`https://${hostname}`, 'production'), /does not target productberlin-production/);
  }
});

test('malformed or non-origin destinations are rejected without echoing credentials', () => {
  for (const value of [undefined, '', 'product.berlin', 'http://product.berlin', 'https://product.berlin/api',
    'https://product.berlin?q=1', 'https://product.berlin/#fragment', 'https://user:do-not-print@product.berlin']) {
    assert.throws(() => validateDeploymentUrl(value, 'production'), error => {
      assert.match(error.message, /HTTPS origin/);
      assert.ok(!error.message.includes('do-not-print'));
      return true;
    });
  }
  assert.throws(() => validateDeploymentUrl('https://product.berlin', 'unknown'), /Invalid deployment environment/);
});

test('preflight rejects the screenshot mismatch before attempting to read build artifacts', () => {
  const result = spawnSync(process.execPath, [fileURLToPath(new URL('../check-deployment.mjs', import.meta.url))], {
    cwd: fileURLToPath(new URL('../', import.meta.url)), // No deployment artifacts here.
    env: {
      ...process.env,
      CLOUDFLARE_API_TOKEN: 'test-token-never-print', CLOUDFLARE_ACCOUNT_ID: 'a'.repeat(32),
      D1_DATABASE_ID: '11111111-1111-4111-8111-111111111111', BUILD_SHA: 'b'.repeat(40),
      TARGET_ENV: 'production', DEPLOY_URL: 'https://productberlin-staging.example.workers.dev',
    }, encoding: 'utf8', timeout: 5000,
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /Set DEPLOY_URL in the GitHub production environment/);
  assert.doesNotMatch(result.stderr, /Missing or empty deployment file|test-token-never-print/);
});
