import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { createServer } from 'node:net';
import { test } from 'node:test';
import { assertPortAvailable } from '../dev-process.mjs';

async function listen(port = 0, host = '127.0.0.1') {
  const server = createServer(socket => socket.end());
  server.listen(port, host);
  await once(server, 'listening');
  return server;
}
const close = server => new Promise(resolve => server.close(resolve));
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

function start(port, serveOnly = true) {
  const processHandle = spawn(process.execPath, ['scripts/local-dev.mjs', ...(serveOnly ? ['--serve-only'] : [])], {
    env: { ...process.env, PRODUCTBERLIN_PORT: String(port), WRANGLER_SEND_METRICS: 'false' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let output = '';
  processHandle.stdout.on('data', chunk => { output += chunk; });
  processHandle.stderr.on('data', chunk => { output += chunk; });
  return { processHandle, exited: once(processHandle, 'exit'), output: () => output };
}

async function stop(run) {
  if (run.processHandle.exitCode === null && run.processHandle.signalCode === null) run.processHandle.kill('SIGTERM');
  await run.exited;
}

async function ready(run, port) {
  for (let attempt = 0; attempt < 120; attempt++) {
    assert.equal(run.processHandle.exitCode, null, run.output());
    try {
      const response = await fetch(`http://127.0.0.1:${port}/api/health`, { signal: AbortSignal.timeout(1000) });
      if (response.ok) return;
    } catch {}
    await delay(100);
  }
  assert.fail(`Worker did not start: ${run.output()}`);
}

async function released(port) {
  for (let attempt = 0; attempt < 50; attempt++) {
    try { await assertPortAvailable(port); return; } catch {}
    await delay(100);
  }
  assert.fail(`Port ${port} was not released after stopping the launcher`);
}

test('busy port fails before npm installation or database changes and preserves the existing listener', { timeout: 10_000 }, async () => {
  const server = await listen();
  const port = server.address().port;
  const run = start(port, false);
  try {
    const [code] = await run.exited;
    assert.equal(code, 1);
    assert.match(run.output(), /already in use.*Stop the existing/);
    assert.doesNotMatch(run.output(), /Starting the complete app|wrangler|Executing on local database/);
    assert.equal(server.listening, true);
  } finally {
    await stop(run);
    await close(server);
  }
});

test('IPv6-only occupied ports are detected', { timeout: 10_000 }, async t => {
  let server;
  try { server = await listen(0, '::1'); } catch (error) {
    if (['EADDRNOTAVAIL', 'EAFNOSUPPORT'].includes(error.code)) return t.skip('IPv6 unavailable');
    throw error;
  }
  try { await assert.rejects(assertPortAvailable(server.address().port), /already in use/); }
  finally { await close(server); }
});

test('Worker starts with debugger port 9229 occupied, releases children on stop, and restarts', { timeout: 45_000 }, async () => {
  let inspector;
  try { inspector = await listen(9229); } catch (error) {
    if (error.code !== 'EADDRINUSE') throw error; // An unrelated debugger already occupies it.
  }
  const reservation = await listen();
  const port = reservation.address().port;
  await close(reservation);
  try {
    for (const signal of ['SIGTERM', 'SIGINT']) {
      const run = start(port);
      try {
        await ready(run, port);
        run.processHandle.kill(signal);
        const [code] = await run.exited;
        assert.equal(code, 0, run.output());
      } finally { await stop(run); }
      await released(port);
    }
  } finally { if (inspector) await close(inspector); }
});
