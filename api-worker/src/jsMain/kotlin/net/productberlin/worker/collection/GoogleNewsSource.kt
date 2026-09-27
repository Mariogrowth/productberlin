package net.productberlin.worker.collection

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import net.productberlin.domain.entity.NewsArticle

internal class GoogleNewsSource(
    private val client: HttpClient,
    private val parser: RssParser,
) : NewsSource {
    override suspend fun search(search: NewsSearch): List<NewsArticle> {
        val response =
            client.get("https://news.google.com/rss/search") {
                parameter("q", search.query)
                parameter("hl", search.language.code)
                parameter("gl", "DE")
                parameter("ceid", "DE:${search.language.code}")
            }
        check(response.status.value == 200) { "Google News returned HTTP ${response.status.value}" }
        val xml = response.bodyAsText()
        require(xml.length <= 2_000_000) { "RSS response exceeds size limit" }
        return parser.parse(xml)
    }
}
