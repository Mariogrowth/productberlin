package net.productberlin.worker.feeds

import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.StartupCandidate

class FeedEndpointTest {
    private val token = "t".repeat(40)
    private val auth = "Bearer $token"
    private val now = Date.parse("2026-10-18T12:00:00Z")
    private val feeds = listOf(PublisherFeed("ds", "deutsche-startups.de", "https://ds.example/feed/", 2))
    private val catalogue = listOf(StartupCandidate("n26", "N26", "Bank", "Fintech"))

    private class Fake : FeedRepository {
        val stored = mutableListOf<FeedItem>()

        override suspend fun knownKeys(since: String) = stored.map { it.key }

        override suspend fun store(
            items: List<FeedItem>,
            classifiedAt: String,
        ): Int {
            val fresh = items.filter { item -> stored.none { it.key == item.key } }
            stored += fresh
            return fresh.size
        }
    }

    private fun endpoint(repository: Fake = Fake()) = FeedEndpoint(token, repository, feeds, catalogue, now)

    private fun item(
        link: String = "https://ds.example/procuros?utm_source=rss",
        verdict: String? = PROCUROS,
        published: String = "2026-10-17T10:00:00Z",
        feed: String = "ds",
        title: String = "Procuros: Berliner Logistik-Startup erhält 20 Mio. Euro",
    ) = """{"feed":"$feed","title":${JSON.stringify(
        title,
    )},"link":"$link","publishedAt":"$published"${verdict?.let { ""","verdict":$it""" } ?: ""}}"""

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
            assertEquals(503, FeedEndpoint(null, Fake(), feeds, catalogue, now).handle("GET", auth, "").status)
        }

    @Test
    fun aModelVerdictIsStoredWithItsStoryAndTheLinkKeyedWithoutTracking() =
        runTest {
            val repository = Fake()
            val response = endpoint(repository).handle("POST", auth, post(item(), item()))
            assertEquals("""{"received":2,"valid":1,"stored":1}""", response.body)
            val stored = repository.stored.single()
            assertEquals("https://ds.example/procuros", stored.key)
            assertEquals("deutsche-startups.de", stored.feed.name)
            assertEquals("2026-10-17T10:00:00.000Z", stored.publishedAt)
            assertEquals(listOf(VerdictStory(null, "Procuros", NewsCategory.Funding, "Procuros raises €20M")), stored.stories)
        }

    @Test
    fun anItemWithoutAVerdictIsLeftToTheKeywordRules() =
        runTest {
            val repository = Fake()
            endpoint(repository).handle("POST", auth, post(item(verdict = null)))
            assertNull(repository.stored.single().stories)
            endpoint(repository).handle("POST", auth, post(item(link = "https://ds.example/other", verdict = """{"stories":[]}""")))
            assertEquals(emptyList(), repository.stored.last().stories, "judged: no Berlin startup in it")
        }

    @Test
    fun modelOutputIsUntrustedOnlyValidStoriesAndRealCatalogueIdsAreKept() =
        runTest {
            val repository = Fake()
            val stories =
                """{"stories":[
                {"companyId":"n26","companyName":"N26 Bank AG","category":"People","headline":"N26 hires a CFO"},
                {"companyId":"made-up","companyName":"Ghost","category":"Funding","headline":"Ghost raises"},
                {"companyId":null,"companyName":"Line\nbreak","category":"Other","headline":"Tab\tand\u0007bell"},
                {"companyId":null,"companyName":"X","category":"Promo","headline":"Not a category"},
                {"companyId":null,"companyName":"","category":"Funding","headline":"No name"},
                {"companyId":null,"companyName":"Long","category":"Funding","headline":"${"h".repeat(161)}"}]}"""
            endpoint(repository).handle("POST", auth, post(item(verdict = stories)))
            assertEquals(
                listOf(
                    VerdictStory("n26", "N26", NewsCategory.People, "N26 hires a CFO"),
                    VerdictStory(null, "Ghost", NewsCategory.Funding, "Ghost raises"),
                    VerdictStory(null, "Line break", NewsCategory.Other, "Tab and bell"),
                ),
                repository.stored.single().stories,
                "catalogue names win, unknown IDs become new companies, invalid stories are dropped, control characters removed",
            )
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

    private companion object {
        const val PROCUROS =
            """{"stories":[{"companyId":null,"companyName":"Procuros","category":"Funding","headline":"Procuros raises €20M"}]}"""
    }
}
