@file:OptIn(ExperimentalJsExport::class)

package net.productberlin.worker

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlinx.serialization.encodeToString
import net.productberlin.domain.entity.KeyScanResult
import net.productberlin.domain.usecase.CheckSiteForKeys
import net.productberlin.worker.keycheck.GlobalFetcher
import net.productberlin.worker.keycheck.PublicFetcher
import net.productberlin.worker.keycheck.SiteScanner
import net.productberlin.worker.mapper.toDto
import net.productberlin.worker.newsletter.EndpointResponse

/** `GET /api/key-check?domain=…`: the prototype key checker behind `/key-check`. */
@JsExport
fun handleKeyCheck(
    method: String,
    domain: String?,
    limiter: dynamic,
    client: String,
): Promise<dynamic> =
    CoroutineScope(EmptyCoroutineContext).promise {
        // Cloudflare's rate-limiting binding (wrangler.json `ratelimits`), keyed by the visitor's IP address.
        val allow: suspend () -> Boolean = {
            if (limiter == null) {
                false
            } else {
                val options = js("({})")
                options.key = client
                val outcome = limiter.limit(options).unsafeCast<Promise<dynamic>>().await()
                outcome.success == true
            }
        }
        val response = keyCheck(method, domain, GlobalFetcher(), allow)
        val result = js("({})")
        result.status = response.status
        result.body = response.body
        result.allow = response.allow
        result.retryAfter = response.retryAfter
        result
    }

/**
 * Invalid input is rejected before the limit is counted, so a typo never costs the visitor their scan. Without a
 * configured limiter the checker refuses to scan: it must never be usable to hammer someone's site.
 */
internal suspend fun keyCheck(
    method: String,
    input: String?,
    fetcher: PublicFetcher,
    allow: suspend () -> Boolean,
): EndpointResponse {
    if (method != "GET") return EndpointResponse(405, """{"error":"method_not_allowed"}""", allow = "GET")
    val domain = input?.let(CheckSiteForKeys::hostname) ?: return EndpointResponse(400, """{"error":"invalid_domain"}""")
    if (!allow()) return EndpointResponse(429, """{"error":"rate_limited"}""", retryAfter = RETRY_AFTER_SECONDS)
    return when (val result = SiteScanner(fetcher).scan(domain)) {
        is KeyScanResult.Completed -> EndpointResponse(200, wireJson.encodeToString(result.scan.toDto()))
        KeyScanResult.Unreachable -> EndpointResponse(502, """{"error":"unreachable"}""")
        KeyScanResult.InvalidDomain -> EndpointResponse(400, """{"error":"invalid_domain"}""")
        KeyScanResult.RateLimited -> EndpointResponse(429, """{"error":"rate_limited"}""", retryAfter = RETRY_AFTER_SECONDS)
        KeyScanResult.Unavailable -> EndpointResponse(503, """{"error":"unavailable"}""")
    }
}

private const val RETRY_AFTER_SECONDS = 60
