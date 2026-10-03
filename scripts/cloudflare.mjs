import { spawnSync } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { mockSeedSql } from './seed.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const [command, environment] = process.argv.slice(2);
if (!['config', 'migrate', 'seed', 'deploy-check', 'deploy'].includes(command) || !['local', 'staging', 'production'].includes(environment)) {
  throw new Error('Usage: npm run cf -- <config|migrate|seed|deploy-check|deploy> <local|staging|production>');
}
if (environment === 'local' && command === 'deploy') throw new Error('Local configuration cannot be deployed');
let configPath = resolve(root, 'wrangler.json');
if (environment !== 'local') {
  const databaseId = process.env.D1_DATABASE_ID;
  const buildSha = process.env.BUILD_SHA;
  if (!databaseId || !/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(databaseId) || /^0+-0+-0+-0+-0+$/.test(databaseId)) {
    throw new Error('Set D1_DATABASE_ID to the database UUID for the selected environment');
  }
  if (['deploy', 'deploy-check'].includes(command) && !/^[0-9a-f]{40}$/.test(buildSha ?? '')) {
    throw new Error('Set BUILD_SHA to the full Git commit SHA before deploying');
  }
  const config = JSON.parse(await readFile(configPath, 'utf8'));
  delete config.$schema;
  config.name = `productberlin-${environment}`;
  config.main = resolve(root, config.main);
  config.assets.directory = resolve(root, config.assets.directory);
  // The Brandfetch client ID is public (embedded in logo URLs); an environment variable may override the default.
  config.vars = { ...config.vars, BUILD_SHA: buildSha ?? 'manual' };
  if (process.env.BRANDFETCH_CLIENT_ID) config.vars.BRANDFETCH_CLIENT_ID = process.env.BRANDFETCH_CLIENT_ID;
  config.d1_databases = [{
    binding: 'DB', database_name: `productberlin-${environment}`, database_id: databaseId,
    migrations_dir: resolve(root, 'cloudflare/migrations'),
  }];
  configPath = resolve(root, `cloudflare/.generated/${environment}.json`);
  await mkdir(dirname(configPath), { recursive: true });
  await writeFile(configPath, JSON.stringify(config, null, 2) + '\n');
}
if (command === 'config') {
  console.log(configPath);
} else {
  const target = environment === 'local' ? '--local' : '--remote';
  let args;
  let secretsDirectory;
  if (command === 'migrate') args = ['d1', 'migrations', 'apply', 'DB', target];
  if (command === 'seed') {
    const seedPath = resolve(root, '.wrangler/mock-seed.sql');
    await mkdir(dirname(seedPath), { recursive: true });
    await writeFile(seedPath, await mockSeedSql());
    args = ['d1', 'execute', 'DB', target, '--file', seedPath, '--yes'];
  }
  if (command === 'deploy-check') args = ['deploy', '--dry-run'];
  if (command === 'deploy') {
    args = ['deploy', '--message', `Git ${process.env.BUILD_SHA}`];
    // Secrets ship with the same Worker version as the code. The file is private, temporary and never logged.
    if (process.env.BREVO_API_KEY) {
      secretsDirectory = await mkdtemp(join(tmpdir(), 'productberlin-secrets-'));
      const secretsPath = join(secretsDirectory, 'secrets.json');
      await writeFile(secretsPath, JSON.stringify({ BREVO_API_KEY: process.env.BREVO_API_KEY }), { mode: 0o600 });
      args.push('--secrets-file', secretsPath);
    } else {
      console.warn('BREVO_API_KEY is not set; the existing Worker secret, if any, is kept.');
    }
  }
  let result;
  try {
    result = spawnSync(process.execPath, [resolve(root, 'node_modules/wrangler/bin/wrangler.js'), ...args, '--config', configPath], {
      cwd: root, stdio: 'inherit', env: { ...process.env, BREVO_API_KEY: '', CI: 'true', WRANGLER_SEND_METRICS: 'false' },
    });
  } finally {
    if (secretsDirectory) await rm(secretsDirectory, { recursive: true, force: true });
  }
  if (result.error) throw result.error;
  process.exitCode = result.status ?? 1;
}
