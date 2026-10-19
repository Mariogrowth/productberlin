package net.productberlin.worker.events

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.productberlin.domain.entity.EventKind
import net.productberlin.domain.entity.EventSource

/** A curated organiser and its public iCal feed. */
internal data class EventFeed(
    val source: EventSource,
    val feed: String,
)

internal fun parseEventSources(json: String): List<EventFeed> =
    Json
        .parseToJsonElement(json)
        .jsonArray
        .map { item ->
            val value = item.jsonObject

            fun field(name: String) = requireNotNull(value[name]).jsonPrimitive.content
            val kind = if (field("kind") == "startup") EventKind.Startup else EventKind.Tech
            EventFeed(
                EventSource(field("id"), field("name"), kind, requireNotNull(value["berlinBased"]).jsonPrimitive.boolean),
                field("feed"),
            ).also {
                require(Regex("[a-z0-9-]+").matches(it.source.id)) { "Invalid event source id" }
                require(it.feed.startsWith("https://")) { "Event feeds must be HTTPS" }
                require(field("kind") in setOf("startup", "tech")) { "Unknown event kind" }
            }
        }.also { feeds -> require(feeds.map { it.source.id }.distinct().size == feeds.size) { "Duplicate event source ids" } }
