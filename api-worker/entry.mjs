// Only the Cloudflare module-worker adapter is JavaScript; application logic is Kotlin/JS.
import catalogue from '../cloudflare/startups.json';
import { parseRssXml } from './rss-parser.mjs';
import { handleApi, handleScheduled } from './build/dist/js/productionLibrary/Productberlin-api-worker.mjs';

const catalogueJson = JSON.stringify(catalogue);

export default {
  async scheduled(controller, env) {
    await handleScheduled(env.DB, controller.scheduledTime, catalogueJson, parseRssXml);
  },
  async fetch(request, env) {
    const result = await handleApi(new URL(request.url).pathname, request.method, env.DB, env.BUILD_SHA ?? 'local', catalogueJson, env.BRANDFETCH_CLIENT_ID);
    const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' };
    if (result.status === 405) headers.Allow = 'GET, HEAD';
    return new Response(request.method === 'HEAD' ? null : result.body, { status: result.status, headers });
  },
};
