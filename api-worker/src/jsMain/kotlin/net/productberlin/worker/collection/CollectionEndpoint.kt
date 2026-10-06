package net.productberlin.worker.collection

import kotlin.js.Date
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.productberlin.domain.entity.StartupCandidate
import net.productberlin.worker.newsletter.EndpointResponse
import net.productberlin.worker.repository.CollectionRepository

/**
 * `/api/internal/collection`, used by the GitHub Actions collector because Google News blocks Cloudflare's servers.
 * - `GET` returns whether this week still needs collecting and, if so, exactly which searches to run.
 * - `POST` receives the fetched, parsed feeds for those searches and runs the normal weekly collection on them.
 * Both require `Authorization: Bearer <COLLECTOR_TOKEN>`; without a configured token the endpoint is unavailable.
 */
internal class CollectionEndpoint(
    private val expectedToken: String?,
    private val repository: CollectionRepository,
    private val catalogue: List<StartupCandidate>,
    private val parser: RssParser,
    private val now: Double,
    private val newToken: () -> String,
) {
    suspend fun handle(
        method: String,
        authorization: String?,
        body: String,
    ): EndpointResponse {
        val token = expectedToken?.takeIf { it.length >= MIN_TOKEN_LENGTH } ?: return EndpointResponse(503, error("not_configured"))
        if (!constantTimeEquals(authorization.orEmpty(), "Bearer $token")) return EndpointResponse(401, error("unauthorized"))
        return when (method) {
            "GET" -> plan()
            "POST" -> collect(body)
            else -> EndpointResponse(405, error("method_not_allowed"), allow = "GET, POST")
        }
    }

    private suspend fun plan(): EndpointResponse {
        if (!CollectionWindow.firstAttemptDue(now)) return EndpointResponse(200, """{"due":false,"reason":"waiting"}""")
        val window = CollectionWindow.latestCompleteWeek(now)
        if (repository.isCompleted(window.collectionKey)) {
            return EndpointResponse(200, """{"due":false,"reason":"published","collectionKey":"${window.collectionKey}"}""")
        }
        val body =
            buildJsonObject {
                put("due", true)
                put("collectionKey", window.collectionKey)
                put("week", window.label)
                put("start", window.start)
                put("end", window.end)
                putJsonArray("searches") {
                    for (search in window.catalogueSearches(catalogue)) {
                        addJsonObject {
                            put("query", search.query)
                            put("language", search.language.code)
                        }
                    }
                }
            }
        return EndpointResponse(200, body.toString())
    }

    private suspend fun collect(body: String): EndpointResponse {
        if (body.length > MAX_BODY) return EndpointResponse(413, error("payload_too_large"))
        val payload =
            try {
                JSON.parse<dynamic>(body)
            } catch (invalid: Throwable) {
                return EndpointResponse(400, error("invalid_json"))
            }
        val window = CollectionWindow.latestCompleteWeek(now)
        if (!CollectionWindow.firstAttemptDue(now) || payload?.collectionKey != window.collectionKey) {
            return EndpointResponse(409, error("stale_plan"))
        }
        val results = mutableMapOf<NewsSearch, PrefetchedResult>()
        for (item in (payload.results.unsafeCast<Array<dynamic>?>() ?: emptyArray())) {
            val query = item?.query as? String ?: continue
            val language = NewsLanguage.entries.firstOrNull { it.code == item.language } ?: continue
            results[NewsSearch(query, language)] = PrefetchedResult(item.feed, (item.error as? String)?.takeIf { it.isNotBlank() })
        }
        return try {
            val outcome =
                WeeklyCollector(PrefetchedNewsSource(results, parser), repository, pause = {}, retryPause = {})
                    .refresh(catalogue, window, Date(now).toISOString(), newToken())
            EndpointResponse(200, """{"outcome":"$outcome","collectionKey":"${window.collectionKey}"}""")
        } catch (failure: IllegalStateException) {
            console.error("Weekly news collection failed: ${failure.message}")
            EndpointResponse(
                502,
                buildJsonObject {
                    put("error", "collection_failed")
                    put("message", failure.message.orEmpty().take(300))
                }.toString(),
            )
        }
    }

    private fun error(code: String) = """{"error":"$code"}"""

    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean {
        var difference = a.length xor b.length
        for (i in 0 until maxOf(a.length, b.length)) {
            difference = difference or (a.getOrElse(i) { ' ' }.code xor b.getOrElse(i) { ' ' }.code)
        }
        return difference == 0
    }

    private companion object {
        const val MIN_TOKEN_LENGTH = 32
        const val MAX_BODY = 8_000_000
    }
}
