package net.productberlin.worker.collection

import kotlin.js.Date
import net.productberlin.domain.entity.NewsArticle

/**
 * The XML library handles structure/entities; only plain text and safe Google links cross this boundary. Items from
 * publishers that [allowPublisher] rejects (by name and site URL) are dropped before they can count or be shown.
 */
internal class RssParser(
    private val allowPublisher: (source: String, sourceUrl: String?) -> Boolean = PublisherPolicy::allows,
    private val parseXml: (String) -> dynamic,
) {
    fun parse(xml: String): List<NewsArticle> {
        val feed = parseXml(xml)
        require(feed.rss?.channel != null) { "Response is not an RSS channel" }
        val items =
            feed.rss.channel.item
                .unsafeCast<Array<dynamic>?>() ?: emptyArray()
        require(items.size <= 1000) { "RSS item limit exceeded" }
        return items.mapNotNull { item ->
            val source = nodeText(item.source)
            val rawTitle = text(item.title)
            val headline = rawTitle.removeSuffix(" - $source").trim()
            val url = text(item.link)
            val time = Date.parse(text(item.pubDate))
            if (headline.isBlank() || source.isBlank() || !time.isFinite() || !safeGoogleLink(url)) return@mapNotNull null
            if (!allowPublisher(source, sourceUrl(item.source))) return@mapNotNull null
            val id = nodeText(item.guid).ifBlank { url.substringBefore('?') }
            NewsArticle(id.take(1000), headline.take(500), source.take(160), Date(time).toISOString(), url)
        }
    }

    private fun sourceUrl(value: dynamic): String? =
        if (value != null && jsTypeOf(value) == "object") text(value["@_url"]).ifBlank { null } else null

    private fun nodeText(value: dynamic): String = text(if (value != null && jsTypeOf(value) == "object") value["#text"] else value)

    private fun text(value: dynamic): String = if (jsTypeOf(value) == "string") (value as String).trim() else ""

    private fun safeGoogleLink(value: String): Boolean =
        Regex("^https://news\\.google\\.com/(rss/)?articles/[A-Za-z0-9_-]+(?:\\?[^\\s<>]*)?$").matches(value)
}
