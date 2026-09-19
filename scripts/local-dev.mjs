import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, delimiter, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertPortAvailable, runManagedProcess } from './dev-process.mjs';

const port = Number(process.env.PRODUCTBERLIN_PORT ?? '8787');
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid PRODUCTBERLIN_PORT');
const serveOnly = process.argv.includes('--serve-only');
try {
  await assertPortAvailable(port);
} catch (error) {
  console.error(error.message);
  process.exit(1);
}

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const nodeBin = dirname(process.execPath);
const npmCli = [
  resolve(nodeBin, '../lib/node_modules/npm/bin/npm-cli.js'),
  resolve(nodeBin, 'node_modules/npm/bin/npm-cli.js'),
].find(existsSync);
if (!npmCli && !serveOnly) throw new Error('npm is missing from this Node installation. Install Node.js with npm, then run npm start.');
const options = {
  cwd: root,
  stdio: 'inherit',
  env: { ...process.env, PATH: `${nodeBin}${delimiter}${process.env.PATH ?? ''}`, WRANGLER_SEND_METRICS: 'false' },
};
function run(script, args) {
  const result = spawnSync(process.execPath, [script, ...args], options);
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}

// Check the port before dependency installation or database changes.
if (!serveOnly) {
  // All DB commands are explicitly local. The seed preserves published data.
  run(npmCli, ['ci', '--no-audit', '--no-fund']);
  run('scripts/cloudflare.mjs', ['migrate', 'local']);
  run('scripts/cloudflare.mjs', ['seed', 'local']);
}
console.log(`\nStarting the complete app at http://localhost:${port} (website + API + local D1).`);
process.exitCode = await runManagedProcess(process.execPath, [
  'node_modules/wrangler/bin/wrangler.js', 'dev', '--local', '--port', String(port), '--inspector-port', '0',
], options);
