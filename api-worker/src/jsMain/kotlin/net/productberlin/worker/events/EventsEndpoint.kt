package net.productberlin.worker.events

import kotlin.js.Date
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.productberlin.domain.entity.CalendarEntry
import net.productberlin.domain.entity.StartupEvent
import net.productberlin.domain.usecase.JudgeCalendarEntry
import net.productberlin.worker.bearerRejection
import net.productberlin.worker.newsletter.EndpointResponse

/**
 * `/api/internal/events`, used by the GitHub collector.
 * - `GET`: whether a refresh is due (at most every [REFRESH_HOURS] hours) and the organisers' feeds to read.
 * - `POST`: each read source's upcoming calendar entries. Entries are validated, judged ([JudgeCalendarEntry]: in
 *   person, in Berlin, startup or tech), and replace that source's upcoming events; sources missing from the post
 *   (a feed that failed) keep theirs.
 * Both require `Authorization: Bearer <COLLECTOR_TOKEN>`.
 */
internal class EventsEndpoint(
    private val expectedToken: String?,
    private val store: EventStore,
    private val feeds: List<EventFeed>,
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
            "POST" -> collect(body)
            else -> EndpointResponse(405, """{"error":"method_not_allowed"}""", allow = "GET, POST")
        }
    }

    private suspend fun plan(): EndpointResponse {
        val last = store.lastCollected()?.let { Date.parse(it) }
        val due = last == null || last.isNaN() || now - last >= REFRESH_HOURS * HOUR
        val body =
            buildJsonObject {
                put("due", due)
                putJsonArray("sources") {
                    for (feed in feeds) {
                        addJsonObject {
                            put("id", feed.source.id)
                            put("feed", feed.feed)
                        }
                    }
                }
            }
        return EndpointResponse(200, body.toString())
    }

    private suspend fun collect(body: String): EndpointResponse {
        if (body.length > MAX_BODY) return EndpointResponse(413, """{"error":"payload_too_large"}""")
        val payload =
            try {
                JSON.parse<dynamic>(body)
            } catch (invalid: Throwable) {
                return EndpointResponse(400, """{"error":"invalid_json"}""")
            }
        val sources = payload?.sources.unsafeCast<Array<dynamic>?>() ?: return EndpointResponse(400, """{"error":"invalid_sources"}""")
        val judge = JudgeCalendarEntry()
        val bySource = linkedMapOf<String, List<StartupEvent>>()
        for (value in sources) {
            val feed = feeds.firstOrNull { it.source.id == (value?.id as? String) } ?: continue
            val entries = value.entries.unsafeCast<Array<dynamic>?>() ?: continue
            bySource[feed.source.id] = entries.take(MAX_ENTRIES).mapNotNull(::entry).mapNotNull { judge(it, feed.source) }
        }
        store.replace(bySource, Date(now).toISOString())
        return EndpointResponse(200, """{"sources":${bySource.size},"events":${bySource.values.sumOf { it.size }}}""")
    }

    private fun entry(value: dynamic): CalendarEntry? {
        val uid = text(value?.uid, 200) ?: return null
        val title = text(value.title, 200) ?: return null
        val starts = (value.startsAt as? String)?.let { Date.parse(it) }?.takeIf { !it.isNaN() } ?: return null
        if (starts < now - DAY || starts > now + WINDOW_DAYS * DAY) return null
        val ends = (value.endsAt as? String)?.let { Date.parse(it) }?.takeIf { !it.isNaN() && it >= starts && it <= starts + 7 * DAY }
        val url = (value.url as? String)?.trim()?.takeIf { SAFE_LINK.matches(it) && it.length <= 500 }
        return CalendarEntry(
            uid,
            title,
            Date(starts).toISOString(),
            ends?.let { Date(it).toISOString() },
            text(value.location, 300).orEmpty(),
            url,
        )
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
        const val HOUR = 3_600_000.0
        const val DAY = 86_400_000.0
        const val REFRESH_HOURS = 6
        const val WINDOW_DAYS = 30
        const val MAX_BODY = 2_000_000
        const val MAX_ENTRIES = 300
        val SAFE_LINK = Regex("^https://[^\\s/<>]+/[^\\s<>\"]*$")
        val CONTROL = Regex("[\\u0000-\\u001f\\u007f]")
    }
}
