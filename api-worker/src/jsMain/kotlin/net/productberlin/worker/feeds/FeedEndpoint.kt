package net.productberlin.worker.feeds

import kotlin.js.Date
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.productberlin.worker.bearerRejection
import net.productberlin.worker.newsletter.EndpointResponse

/**
 * `/api/internal/feeds`, used by the GitHub collector for the weekly news.
 * - `GET`: the publisher feeds to read, and the items already read in the last [WINDOW_DAYS] days.
 * - `POST`: new items (title, optional English translation, link, date). They are judged like Google News articles when
 *   the news is built. Only items from listed feeds with bounded single-line titles, HTTPS links and recent dates are
 *   kept.
 * Both require `Authorization: Bearer <COLLECTOR_TOKEN>`.
 */
internal class FeedEndpoint(
    private val expectedToken: String?,
    private val repository: FeedRepository,
    private val feeds: List<PublisherFeed>,
    private val now: Double,
) {
    suspend fun handle(
        method: String,
        authorization: String?,
        body: String,
    ): EndpointResponse {
        bearerRejection(expectedToken, authorization)?.let { return it }
        return when (method) {
            "GET" -> plan()
            "POST" -> store(body)
            else -> EndpointResponse(405, """{"error":"method_not_allowed"}""", allow = "GET, POST")
        }
    }

    private suspend fun plan(): EndpointResponse {
        val known = repository.knownKeys(Date(now - WINDOW_DAYS * DAY).toISOString())
        val body =
            buildJsonObject {
                putJsonArray("feeds") {
                    for (feed in feeds) {
                        addJsonObject {
                            put("id", feed.id)
                            put("name", feed.name)
                            put("url", feed.url)
                            put("pages", feed.pages)
                        }
                    }
                }
                putJsonArray("known") { known.forEach { add(JsonPrimitive(it)) } }
                put("since", Date(now - WINDOW_DAYS * DAY).toISOString())
            }
        return EndpointResponse(200, body.toString())
    }

    private suspend fun store(body: String): EndpointResponse {
        if (body.length > MAX_BODY) return EndpointResponse(413, """{"error":"payload_too_large"}""")
        val payload =
            try {
                JSON.parse<dynamic>(body)
            } catch (invalid: Throwable) {
                return EndpointResponse(400, """{"error":"invalid_json"}""")
            }
        val raw = payload?.items.unsafeCast<Array<dynamic>?>() ?: return EndpointResponse(400, """{"error":"invalid_items"}""")
        if (raw.size > MAX_ITEMS) return EndpointResponse(413, """{"error":"too_many_items"}""")
        val items = raw.mapNotNull(::item).distinctBy { it.key }
        val stored = repository.store(items, Date(now).toISOString())
        return EndpointResponse(200, """{"received":${raw.size},"valid":${items.size},"stored":$stored}""")
    }

    private fun item(value: dynamic): FeedItem? {
        val feed = feeds.firstOrNull { it.id == (value?.feed as? String) } ?: return null
        val link = (value.link as? String)?.trim()?.takeIf { SAFE_LINK.matches(it) && it.length <= 500 } ?: return null
        val title = text(value.title, 300) ?: return null
        val published = (value.publishedAt as? String)?.let { Date.parse(it) }?.takeIf { !it.isNaN() } ?: return null
        if (published < now - WINDOW_DAYS * DAY || published > now + DAY) return null
        return FeedItem(itemKey(link), feed, title, text(value.translatedTitle, 300), link, Date(published).toISOString())
    }

    /** Single-line text without control characters, at most [max] characters, or null. */
    private fun text(
        value: dynamic,
        max: Int,
    ): String? =
        (value as? String)
            ?.replace(CONTROL, " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= max }

    private companion object {
        const val DAY = 86_400_000.0

        // Six weeks: the news shows two weeks plus three earlier weeks to catch up on, and a one-off backfill can fill
        // them for feeds that started later.
        const val WINDOW_DAYS = 42
        const val MAX_BODY = 2_000_000
        const val MAX_ITEMS = 400
        val SAFE_LINK = Regex("^https://[^\\s/<>]+/[^\\s<>\"]*$")
        val CONTROL = Regex("[\\u0000-\\u001f\\u007f]")
    }
}
