// Only the Cloudflare module-worker adapter is JavaScript; application logic is Kotlin/JS.
import catalogue from '../cloudflare/startups.json';
import publishers from '../cloudflare/publishers.json';
import { parseRssXml } from './rss-parser.mjs';
import { handleApi, handleScheduled, handleSubscription } from './build/dist/js/productionLibrary/Productberlin-api-worker.mjs';

const catalogueJson = JSON.stringify(catalogue);
const publishersJson = JSON.stringify(publishers);

export default {
  async scheduled(controller, env) {
    await handleScheduled(env.DB, controller.scheduledTime, catalogueJson, publishersJson, parseRssXml);
  },
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/api/subscriptions') {
      // Read at most a small body; the endpoint rejects anything larger than 1 KB.
      const body = request.method === 'POST' ? (await request.text()).slice(0, 2048) : '';
      const result = await handleSubscription(
        request.method, request.headers.get('Origin'), url.origin, request.headers.get('Content-Type'), body,
        env.BREVO_API_KEY, env.BREVO_LIST_ID, env.BREVO_DOI_TEMPLATE_ID,
      );
      const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' };
      if (result.allow) headers.Allow = result.allow;
      return new Response(result.body, { status: result.status, headers });
    }
    const result = await handleApi(new URL(request.url).pathname, request.method, env.DB, env.BUILD_SHA ?? 'local', catalogueJson, env.BRANDFETCH_CLIENT_ID);
    const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' };
    if (result.status === 405) headers.Allow = 'GET, HEAD';
    return new Response(request.method === 'HEAD' ? null : result.body, { status: result.status, headers });
  },
};
