package net.productberlin.worker.feeds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A startup publisher's RSS feed (`cloudflare/feeds.json`), read for the weekly news. */
internal data class PublisherFeed(
    val id: String,
    val name: String,
    val url: String,
    val pages: Int,
)

internal fun parseFeeds(json: String): List<PublisherFeed> =
    Json
        .parseToJsonElement(json)
        .jsonArray
        .map { item ->
            val value = item.jsonObject

            fun field(name: String) = requireNotNull(value[name]).jsonPrimitive.content
            PublisherFeed(field("id"), field("name"), field("url"), value["pages"]?.jsonPrimitive?.int ?: 1).also {
                require(Regex("[a-z0-9-]+").matches(it.id)) { "Invalid feed id" }
                require(it.url.startsWith("https://")) { "Feeds must be HTTPS" }
                require(it.pages in 1..5) { "1–5 pages per feed" }
            }
        }.also { feeds -> require(feeds.map { it.id }.distinct().size == feeds.size) { "Duplicate feed ids" } }

/** The same article linked with tracking parameters or fragments is one item. */
internal fun itemKey(link: String): String = link.substringBefore('#').substringBefore('?').trimEnd('/')
