// Only the Cloudflare module-worker adapter is JavaScript; application logic is Kotlin/JS.
import { handleApi } from './build/dist/js/productionLibrary/Productberlin-api-worker.mjs';

export default {
  async fetch(request, env) {
    const result = await handleApi(new URL(request.url).pathname, request.method, env.DB, env.BUILD_SHA ?? 'local');
    const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' };
    if (result.status === 405) headers.Allow = 'GET, HEAD';
    return new Response(request.method === 'HEAD' ? null : result.body, { status: result.status, headers });
  },
};
