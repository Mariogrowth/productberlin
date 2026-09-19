import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { createServer } from 'node:net';

export async function assertPortAvailable(port) {
  for (const host of ['127.0.0.1', '::1']) {
    const probe = createServer();
    try {
      probe.listen({ host, port, exclusive: true });
      await once(probe, 'listening');
    } catch (error) {
      if (error.code === 'EADDRINUSE') {
        throw new Error(`Port ${port} is already in use. Stop the existing webApp/runLocal or npm dev session, then try again. No existing process was stopped.`);
      }
      if (!['EADDRNOTAVAIL', 'EAFNOSUPPORT'].includes(error.code)) throw error;
    } finally {
      if (probe.listening) await new Promise(resolve => probe.close(resolve));
    }
  }
}

/** Own a separate process group so stopping the launcher also stops Wrangler's workerd children. */
export async function runManagedProcess(executable, args, options) {
  const child = spawn(executable, args, { ...options, detached: process.platform !== 'win32' });
  let stopping = false;
  let forceStop;
  function signalTree(signal) {
    if (!child.pid) return;
    if (process.platform === 'win32') {
      // Windows has no POSIX process groups. Target only this launcher's child tree.
      spawn('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' });
    } else {
      try {
        process.kill(-child.pid, signal);
      } catch (error) {
        if (error.code !== 'ESRCH') throw error;
      }
    }
  }
  function stop() {
    if (stopping) return;
    stopping = true;
    signalTree('SIGTERM');
    forceStop = setTimeout(() => signalTree('SIGKILL'), 5000);
    forceStop.unref();
  }
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);
  try {
    const [code, signal] = await once(child, 'exit');
    return stopping || signal === 'SIGINT' || signal === 'SIGTERM' ? 0 : (code ?? 1);
  } finally {
    clearTimeout(forceStop);
    process.off('SIGINT', stop);
    process.off('SIGTERM', stop);
    // The wrapper can exit before its runtime, including on startup failure.
    signalTree('SIGKILL');
  }
}
