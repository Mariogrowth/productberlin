package net.productberlin.worker.feeds

import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FeedEndpointTest {
    private val token = "t".repeat(40)
    private val auth = "Bearer $token"
    private val now = Date.parse("2026-10-18T12:00:00Z")
    private val feeds = listOf(PublisherFeed("ds", "deutsche-startups.de", "https://ds.example/feed/", 2))

    private class Fake : FeedRepository {
        val stored = mutableListOf<FeedItem>()

        override suspend fun knownKeys(since: String) = stored.map { it.key }

        override suspend fun store(
            items: List<FeedItem>,
            storedAt: String,
        ): Int {
            val fresh = items.filter { item -> stored.none { it.key == item.key } }
            stored += fresh
            return fresh.size
        }
    }

    private fun endpoint(repository: Fake = Fake()) = FeedEndpoint(token, repository, feeds, now)

    private fun item(
        link: String = "https://ds.example/procuros?utm_source=rss",
        translated: String? = null,
        published: String = "2026-10-17T10:00:00Z",
        feed: String = "ds",
        title: String = "Procuros: Berliner Logistik-Startup erhält 20 Mio. Euro",
    ) = """{"feed":"$feed","title":${JSON.stringify(
        title,
    )},"link":"$link","publishedAt":"$published"${translated?.let { ""","translatedTitle":"$it"""" } ?: ""}}"""

    private fun post(vararg items: String) = """{"items":[${items.joinToString(",")}]}"""

    @Test
    fun planListsTheFeedsAndTheItemsAlreadyRead() =
        runTest {
            val repository = Fake()
            endpoint(repository).handle("POST", auth, post(item()))
            val plan = JSON.parse<dynamic>(endpoint(repository).handle("GET", auth, "").body)
            assertEquals("https://ds.example/feed/", plan.feeds[0].url)
            assertEquals(2, plan.feeds[0].pages)
            assertEquals(listOf("https://ds.example/procuros"), plan.known.unsafeCast<Array<String>>().toList())
            assertEquals("2026-09-27T12:00:00.000Z", plan.since)
        }

    @Test
    fun requiresTheCollectorToken() =
        runTest {
            assertEquals(401, endpoint().handle("GET", "Bearer wrong", "").status)
            assertEquals(503, FeedEndpoint(null, Fake(), feeds, now).handle("GET", auth, "").status)
        }

    @Test
    fun itemsAreStoredOnceKeyedWithoutTrackingParametersWithCleanTitles() =
        runTest {
            val repository = Fake()
            val response =
                endpoint(repository).handle(
                    "POST",
                    auth,
                    post(
                        item(),
                        item(),
                        item(link = "https://ds.example/n26", title = "N26 holt\nneuen\tCFO", translated = "N26 hires a CFO"),
                    ),
                )
            assertEquals("""{"received":3,"valid":2,"stored":2}""", response.body)
            val procuros = repository.stored.first()
            assertEquals("https://ds.example/procuros", procuros.key)
            assertEquals("deutsche-startups.de", procuros.feed.name)
            assertEquals("2026-10-17T10:00:00.000Z", procuros.publishedAt)
            assertNull(procuros.translatedTitle)
            assertEquals("N26 holt neuen CFO", repository.stored.last().title, "control characters removed")
            assertEquals("N26 hires a CFO", repository.stored.last().translatedTitle)
            assertEquals("""{"received":1,"valid":1,"stored":0}""", endpoint(repository).handle("POST", auth, post(item())).body)
        }

    @Test
    fun itemsFromUnknownFeedsUnsafeLinksOrOutsideTheWindowAreIgnored() =
        runTest {
            val repository = Fake()
            val response =
                endpoint(repository).handle(
                    "POST",
                    auth,
                    post(
                        item(feed = "unknown"),
                        item(link = "http://ds.example/plain"),
                        item(link = "javascript:alert(1)"),
                        item(link = "https://ds.example/old", published = "2026-09-01T10:00:00Z"),
                        item(link = "https://ds.example/future", published = "2026-10-25T10:00:00Z"),
                        item(link = "https://ds.example/no-title", title = "   "),
                        item(link = "https://ds.example/ok"),
                    ),
                )
            assertEquals("""{"received":7,"valid":1,"stored":1}""", response.body)
            assertEquals(listOf("https://ds.example/ok"), repository.stored.map { it.key })
            assertEquals(400, endpoint(repository).handle("POST", auth, "{").status)
            assertTrue(endpoint(repository).handle("POST", auth, """{"items":[${List(401) { item() }.joinToString(",")}]}""").status == 413)
        }
}
