package net.productberlin.worker.collection

import net.productberlin.domain.entity.NewsArticle

/** One search as fetched by the GitHub collector: either a parsed feed or the reason it failed. */
internal data class PrefetchedResult(
    val feed: dynamic,
    val error: String?,
)

/**
 * Serves searches that the GitHub collector already fetched from Google News (Google blocks Cloudflare's servers).
 * A search the collector did not send, or could not fetch, fails like a live request would, so the usual tolerance
 * applies. Retrying returns the same result; the collector retries before sending.
 */
internal class PrefetchedNewsSource(
    private val results: Map<NewsSearch, PrefetchedResult>,
    private val parser: RssParser,
) : NewsSource {
    override suspend fun search(search: NewsSearch): List<NewsArticle> {
        val result = results[search] ?: error("Search was not fetched by the collector")
        result.error?.let { error(it.take(200)) }
        return parser.parseTree(result.feed)
    }
}
