@file:OptIn(ExperimentalJsExport::class)

package net.productberlin.worker

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
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
): Promise<dynamic> =
    CoroutineScope(EmptyCoroutineContext).promise {
        val response = keyCheck(method, domain, GlobalFetcher())
        val result = js("({})")
        result.status = response.status
        result.body = response.body
        result.allow = response.allow
        result
    }

internal suspend fun keyCheck(
    method: String,
    input: String?,
    fetcher: PublicFetcher,
): EndpointResponse {
    if (method != "GET") return EndpointResponse(405, """{"error":"method_not_allowed"}""", allow = "GET")
    val domain = input?.let(CheckSiteForKeys::hostname) ?: return EndpointResponse(400, """{"error":"invalid_domain"}""")
    return when (val result = SiteScanner(fetcher).scan(domain)) {
        is KeyScanResult.Completed -> EndpointResponse(200, wireJson.encodeToString(result.scan.toDto()))
        KeyScanResult.Unreachable -> EndpointResponse(502, """{"error":"unreachable"}""")
        KeyScanResult.InvalidDomain -> EndpointResponse(400, """{"error":"invalid_domain"}""")
        KeyScanResult.Unavailable -> EndpointResponse(503, """{"error":"unavailable"}""")
    }
}
