package net.productberlin.worker.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RssParserTest {
    @Test
    fun readsAttributedSourceAndGuidAndDropsInvalidDatesAndUnsafeLinks() {
        val parser =
            RssParser {
                JSON.parse<dynamic>(
                    """{"rss":{"channel":{"item":[
              {"title":"Mika &amp; partners - Publisher",
                "source":{"#text":"Publisher",
                "@_url":"https://publisher.test"},
                "guid":{"#text":"identity"},
                "link":"https://news.google.com/rss/articles/abc?oc=5",
                "pubDate":"Fri, 25 Sep 2026 12:00:00 GMT"},
              {"title":"Invalid",
                "source":"Publisher",
                "link":"javascript:alert(1)",
                "pubDate":"Fri, 25 Sep 2026 12:00:00 GMT"},
              {"title":"Invalid date",
                "source":"Publisher",
                "link":"https://news.google.com/rss/articles/abc",
                "pubDate":"not a date"}
            ]}}}""",
                )
            }
        val article = parser.parse("unused").single()
        assertEquals("identity", article.id)
        assertEquals("Publisher", article.source)
        assertEquals("Mika &amp; partners", article.headline)
        assertEquals("2026-09-25T12:00:00.000Z", article.publishedAt)
    }

    @Test
    fun rejectsNonRssResponsesAndAcceptsEmptyChannels() {
        assertFailsWith<IllegalArgumentException> { RssParser { JSON.parse<dynamic>("{}") }.parse("") }
        assertEquals(emptyList(), RssParser { JSON.parse<dynamic>("""{"rss":{"channel":{}}}""") }.parse(""))
    }

    @Test
    fun canonicalGoogleLinkSuppliesMissingIdentityAndNormalizesTimezone() {
        val article =
            RssParser {
                JSON.parse<dynamic>(
                    """{"rss":{"channel":{"item":[{
                "title":" Company launches - Publisher ","source":"Publisher",
                "link":"https://news.google.com/articles/abc_123?oc=5",
                "pubDate":"2026-09-25T14:00:00+02:00"
            }]}}}""",
                )
            }.parse("").single()
        assertEquals("https://news.google.com/articles/abc_123", article.id)
        assertEquals("Company launches", article.headline)
        assertEquals("2026-09-25T12:00:00.000Z", article.publishedAt)
    }

    @Test
    fun rejectsDeceptiveGoogleHostsAndNonArticleLinks() {
        val urls =
            listOf(
                "http://news.google.com/articles/abc",
                "https://news.google.com.evil.test/articles/abc",
                "https://news.google.com@evil.test/articles/abc",
                "https://news.google.com/search?q=abc",
                "javascript:alert(1)",
                "https://news.google.com/articles/abc?x=<script>",
            )
        for (url in urls) {
            val result =
                RssParser {
                    val item = js("({})")
                    item.title = "Company launches"
                    item.source = "Publisher"
                    item.pubDate = "2026-09-25T12:00:00Z"
                    item.link = url
                    val feed = JSON.parse<dynamic>("""{"rss":{"channel":{}}}""")
                    feed.rss.channel.item = arrayOf(item)
                    feed
                }.parse("")
            assertEquals(emptyList(), result, url)
        }
    }

    @Test
    fun rejectsExcessiveItemCountsAndDropsBlankAttributionOrTitle() {
        assertFailsWith<IllegalArgumentException> {
            RssParser {
                val feed = JSON.parse<dynamic>("""{"rss":{"channel":{}}}""")
                feed.rss.channel.item = Array<Any?>(1001) { js("({})") }
                feed
            }.parse("")
        }
        val result =
            RssParser {
                JSON.parse<dynamic>(
                    """{"rss":{"channel":{"item":[
                {"title":"","source":"Publisher","link":"https://news.google.com/articles/a","pubDate":"2026-09-25"},
                {"title":"Title","source":"","link":"https://news.google.com/articles/b","pubDate":"2026-09-25"},
                {"title":42,"source":"Publisher","link":"https://news.google.com/articles/c","pubDate":"2026-09-25"}
            ]}}}""",
                )
            }.parse("")
        assertEquals(emptyList(), result)
    }

    @Test
    fun dropsItemsFromDisallowedPublishersUsingTheSourceSite() {
        val feed =
            """{"rss":{"channel":{"item":[
              {"title":"mika raises seed - FF News","source":{"#text":"FF News","@_url":"https://ffnews.com"},
                "link":"https://news.google.com/rss/articles/a","pubDate":"Fri, 25 Sep 2026 12:00:00 GMT"},
              {"title":"Mika Suansing on funding - politiko","source":{"#text":"politiko","@_url":"https://politiko.com.ph"},
                "link":"https://news.google.com/rss/articles/b","pubDate":"Fri, 25 Sep 2026 12:00:00 GMT"},
              {"title":"Joe and Mika - YouTube","source":{"#text":"YouTube","@_url":"https://www.youtube.com"},
                "link":"https://news.google.com/rss/articles/c","pubDate":"Fri, 25 Sep 2026 12:00:00 GMT"}
            ]}}}"""
        val articles = RssParser(TrustedPublishers(listOf("ffnews.com"))::allows) { JSON.parse<dynamic>(feed) }.parse("")
        assertEquals(listOf("mika raises seed"), articles.map { it.headline })
        assertEquals(3, RssParser({ _, _ -> true }) { JSON.parse<dynamic>(feed) }.parse("").size)
    }
}
