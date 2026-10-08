package net.productberlin.worker.collection

import kotlin.js.Date
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.productberlin.domain.entity.StartupCandidate
import net.productberlin.worker.bearerRejection
import net.productberlin.worker.newsletter.EndpointResponse
import net.productberlin.worker.repository.ArticleHistoryRepository
import net.productberlin.worker.repository.CollectionRepository

/**
 * `/api/internal/collection`, used by the GitHub Actions collector because Google News blocks Cloudflare's servers.
 * - `GET` returns the next unit of work and exactly which searches to run. An edition scores four weeks, so any of
 *   the three previous weeks missing from the stored history is planned first, oldest first, as a `history` unit
 *   that only stores articles; then the `edition` unit for the latest complete week.
 * - `POST` receives the fetched, parsed feeds for the planned unit and runs it.
 * Both require `Authorization: Bearer <COLLECTOR_TOKEN>`; without a configured token the endpoint is unavailable.
 */
internal class CollectionEndpoint(
    private val expectedToken: String?,
    private val repository: CollectionRepository,
    private val history: ArticleHistoryRepository,
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
        bearerRejection(expectedToken, authorization)?.let { return it }
        return when (method) {
            "GET" -> plan()
            "POST" -> collect(body)
            else -> EndpointResponse(405, error("method_not_allowed"), allow = "GET, POST")
        }
    }

    /** The next unit of work: a missing history week (oldest first), else the edition, or null when there is none. */
    private suspend fun next(): WorkUnit? {
        val window = CollectionWindow.latestCompleteWeek(now)
        val earlier = window.previousWeeks(3).reversed()
        val stored = history.recorded(earlier.map { it.weekStart })
        earlier.firstOrNull { it.weekStart !in stored }?.let { return WorkUnit(it, history = true) }
        if (!CollectionWindow.firstAttemptDue(now) || repository.isCompleted(window.collectionKey)) return null
        return WorkUnit(window, history = false)
    }

    private data class WorkUnit(
        val window: CollectionWindow,
        val history: Boolean,
    ) {
        val key: String get() = if (history) window.historyKey else window.collectionKey
    }

    private suspend fun plan(): EndpointResponse {
        val unit = next()
        if (unit == null) {
            if (!CollectionWindow.firstAttemptDue(now)) return EndpointResponse(200, """{"due":false,"reason":"waiting"}""")
            val key = CollectionWindow.latestCompleteWeek(now).collectionKey
            return EndpointResponse(200, """{"due":false,"reason":"published","collectionKey":"$key"}""")
        }
        val window = unit.window
        val body =
            buildJsonObject {
                put("due", true)
                put("mode", if (unit.history) "history" else "edition")
                put("collectionKey", unit.key)
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
        val unit = next()
        if (unit == null || payload?.collectionKey != unit.key) return EndpointResponse(409, error("stale_plan"))
        val window = unit.window
        val results = mutableMapOf<NewsSearch, PrefetchedResult>()
        for (item in (payload.results.unsafeCast<Array<dynamic>?>() ?: emptyArray())) {
            val query = item?.query as? String ?: continue
            val language = NewsLanguage.entries.firstOrNull { it.code == item.language } ?: continue
            results[NewsSearch(query, language)] = PrefetchedResult(item.feed, (item.error as? String)?.takeIf { it.isNotBlank() })
        }
        return try {
            val collector = WeeklyCollector(PrefetchedNewsSource(results, parser), repository, history, pause = {}, retryPause = {})
            val outcome =
                if (unit.history) {
                    collector.recordHistory(catalogue, window, Date(now).toISOString())
                } else {
                    collector.refresh(catalogue, window, Date(now).toISOString(), newToken())
                }
            EndpointResponse(200, """{"outcome":"$outcome","collectionKey":"${unit.key}"}""")
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

    private companion object {
        const val MAX_BODY = 8_000_000
    }
}
