import assert from 'node:assert/strict';

/** Validate the destination before migrations or uploads. Custom domains use the version smoke check. */
export function validateDeploymentUrl(value, environment) {
  assert.ok(['staging', 'production'].includes(environment), 'Invalid deployment environment');
  let url;
  try {
    url = new URL(value);
  } catch {
    assert.fail('DEPLOY_URL must be a complete HTTPS origin, including https://');
  }
  assert.ok(url.protocol === 'https:' && !url.username && !url.password && !url.search && !url.hash && url.pathname === '/',
    'DEPLOY_URL must be an HTTPS origin without credentials, path, query or fragment');

  if (url.hostname === 'workers.dev' || url.hostname.endsWith('.workers.dev')) {
    const labels = url.hostname.split('.');
    const worker = `productberlin-${environment}`;
    assert.ok(labels.length === 4 && labels[0] === worker,
      `DEPLOY_URL does not target ${worker}. Set DEPLOY_URL in the GitHub ${environment} environment to ` +
      `https://${worker}.<account-subdomain>.workers.dev (or its custom domain). ` +
      'The account subdomain may contain "staging"; the first hostname label must match the deployed Worker.');
  }
  return url;
}
