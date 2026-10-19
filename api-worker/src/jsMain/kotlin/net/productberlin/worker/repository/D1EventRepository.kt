package net.productberlin.worker.repository

import kotlin.js.Date
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import net.productberlin.domain.entity.EventKind
import net.productberlin.domain.entity.StartupEvent
import net.productberlin.domain.repository.EventRepository
import net.productberlin.worker.events.EventStore

internal class D1EventRepository(
    private val database: dynamic,
    private val now: Double,
) : EventRepository,
    EventStore {
    /** The coming week, including events that started in the last three hours; the same event listed twice shows once. */
    override suspend fun getUpcomingEvents(): List<StartupEvent> {
        val result =
            statement(
                "SELECT id,title,starts_at,ends_at,organiser,kind,venue,url FROM events WHERE starts_at >= ? AND starts_at < ? " +
                    "ORDER BY starts_at, id",
                Date(now - 3 * HOUR).toISOString(),
                Date(now + 7 * DAY).toISOString(),
            ).all()
                .unsafeCast<Promise<dynamic>>()
                .await()
        return result.results
            .unsafeCast<Array<dynamic>>()
            .map {
                StartupEvent(
                    it.id as String,
                    it.title as String,
                    it.starts_at as String,
                    it.ends_at as String?,
                    it.organiser as String,
                    if (it.kind == "Startup") EventKind.Startup else EventKind.Tech,
                    it.venue as String?,
                    it.url as String?,
                )
            }.distinctBy { it.title.lowercase().trim() to it.startsAt }
    }

    override suspend fun lastCollected(): String? {
        val row =
            statement("SELECT collected_at FROM event_collections WHERE id = 1")
                .first()
                .unsafeCast<Promise<dynamic>>()
                .await()
        return row?.collected_at as String?
    }

    override suspend fun replace(
        bySource: Map<String, List<StartupEvent>>,
        now: String,
    ) {
        val statements = mutableListOf<dynamic>()
        for ((source, events) in bySource) {
            statements.add(statement("DELETE FROM events WHERE source = ? AND starts_at >= ?", source, now))
            val json =
                buildJsonArray {
                    for (event in events) {
                        addJsonObject {
                            put("id", event.id)
                            put("title", event.title)
                            put("starts", event.startsAt)
                            put("ends", event.endsAt)
                            put("organiser", event.organiser)
                            put("kind", event.kind.name)
                            put("venue", event.venue)
                            put("url", event.url)
                        }
                    }
                }.toString()
            statements.add(
                statement(
                    """
                    INSERT INTO events (id,source,title,starts_at,ends_at,organiser,kind,venue,url)
                    SELECT json_extract(value,'$.id'),?,json_extract(value,'$.title'),json_extract(value,'$.starts'),
                        json_extract(value,'$.ends'),json_extract(value,'$.organiser'),json_extract(value,'$.kind'),
                        json_extract(value,'$.venue'),json_extract(value,'$.url') FROM json_each(?) WHERE 1
                    ON CONFLICT(id) DO UPDATE SET title=excluded.title, starts_at=excluded.starts_at, ends_at=excluded.ends_at,
                        organiser=excluded.organiser, kind=excluded.kind, venue=excluded.venue, url=excluded.url
                    """.trimIndent(),
                    source,
                    json,
                ),
            )
        }
        statements.add(
            statement(
                "INSERT INTO event_collections (id,collected_at) VALUES (1,?) ON CONFLICT(id) DO UPDATE SET collected_at=excluded.collected_at",
                now,
            ),
        )
        database.batch(statements.toTypedArray()).unsafeCast<Promise<Array<dynamic>>>().await()
    }

    private fun statement(
        sql: String,
        vararg values: Any?,
    ): dynamic {
        val query = database.prepare(sql)
        return query.bind.apply(query, values)
    }

    private companion object {
        const val HOUR = 3_600_000.0
        const val DAY = 86_400_000.0
    }
}
