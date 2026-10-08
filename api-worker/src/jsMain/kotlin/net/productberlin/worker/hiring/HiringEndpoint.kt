package net.productberlin.worker.hiring

import kotlin.js.Date
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.productberlin.worker.bearerRejection
import net.productberlin.worker.collection.CollectionWindow
import net.productberlin.worker.newsletter.EndpointResponse

/**
 * `/api/internal/hiring`, used by the GitHub Actions collector to record each catalogue company's open roles once a
 * week. Recorded for ranking experiments only; nothing on the site reads it yet.
 * - `GET` returns whether this week still needs recording and, if so, exactly which job boards to read.
 * - `POST` receives the counts and stores them in one transaction. Counts for boards that were not planned, or
 *   that are impossible, are dropped. If fewer than half of the boards succeeded, nothing is stored so the next
 *   hourly run retries.
 * Both require `Authorization: Bearer <COLLECTOR_TOKEN>`.
 */
internal class HiringEndpoint(
    private val expectedToken: String?,
    private val repository: HiringRepository,
    private val boards: List<JobBoard>,
    private val now: Double,
) {
    /** Counts are recorded per calendar week, identified by its Monday (UTC). */
    private val week = CollectionWindow.latestCompleteWeek(now).end.take(10)

    suspend fun handle(
        method: String,
        authorization: String?,
        body: String,
    ): EndpointResponse {
        bearerRejection(expectedToken, authorization)?.let { return it }
        return when (method) {
            "GET" -> plan()
            "POST" -> record(body)
            else -> EndpointResponse(405, error("method_not_allowed"), allow = "GET, POST")
        }
    }

    private suspend fun plan(): EndpointResponse {
        if (repository.isRecorded(week)) return EndpointResponse(200, """{"due":false,"reason":"recorded","week":"$week"}""")
        val body =
            buildJsonObject {
                put("due", true)
                put("week", week)
                putJsonArray("boards") {
                    for (board in boards) {
                        addJsonObject {
                            put("startupId", board.startupId)
                            put("provider", board.provider.code)
                            put("handle", board.handle)
                        }
                    }
                }
            }
        return EndpointResponse(200, body.toString())
    }

    private suspend fun record(body: String): EndpointResponse {
        if (body.length > MAX_BODY) return EndpointResponse(413, error("payload_too_large"))
        val payload =
            try {
                JSON.parse<dynamic>(body)
            } catch (invalid: Throwable) {
                return EndpointResponse(400, error("invalid_json"))
            }
        if (payload?.week != week) return EndpointResponse(409, error("stale_plan"))
        val planned = boards.associateBy { it.startupId }
        val counts = linkedMapOf<String, HiringCount>()
        for (item in (payload.results.unsafeCast<Array<dynamic>?>() ?: emptyArray())) {
            val board = planned[item?.startupId as? String] ?: continue
            if (item.provider != board.provider.code || item.handle != board.handle) continue
            val total = whole(item.totalJobs) ?: continue
            val berlin = whole(item.berlinJobs) ?: continue
            if (berlin > total || board.startupId in counts) continue
            counts[board.startupId] = HiringCount(board.startupId, board.provider, total, berlin)
        }
        val failed = boards.size - counts.size
        if (counts.size * 2 < boards.size) {
            return EndpointResponse(502, """{"error":"too_many_failures","recorded":${counts.size},"boards":${boards.size}}""")
        }
        val stored = repository.record(week, counts.values.toList(), failed, Date(now).toISOString())
        val outcome = if (stored) "recorded" else "already_recorded"
        return EndpointResponse(200, """{"outcome":"$outcome","week":"$week","boards":${counts.size},"failed":$failed}""")
    }

    /** A whole number of roles in a plausible range, or null. */
    private fun whole(value: dynamic): Int? {
        val number = value as? Number ?: return null
        val asDouble = number.toDouble()
        return if (asDouble % 1.0 == 0.0 && asDouble in 0.0..MAX_JOBS) asDouble.toInt() else null
    }

    private fun error(code: String) = """{"error":"$code"}"""

    private companion object {
        const val MAX_BODY = 1_000_000
        const val MAX_JOBS = 20_000.0
    }
}
