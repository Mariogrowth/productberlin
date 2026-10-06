package net.productberlin.worker.collection

import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.productberlin.domain.entity.StartupCandidate
import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.worker.repository.CollectionRepository

class CollectionEndpointTest {
    private val token = "t".repeat(40)
    private val auth = "Bearer $token"
    private val tuesday = Date.parse("2026-10-06T08:07:00Z")
    private val catalogue = listOf(StartupCandidate("noxtua", "Noxtua", "Legal AI", "Legal tech"))
    private val trusted = TrustedPublishers(listOf("handelsblatt.com"))
    private val parser = RssParser(trusted::allows) { error("no XML expected") }

    private class Fake(
        var completed: Boolean = false,
    ) : CollectionRepository {
        var published: WeeklyRanking? = null

        override suspend fun acquire(
            week: String,
            now: String,
            token: String,
        ) = !completed

        override suspend fun previousPositions(beforeWeek: String) = emptyMap<String, Int>()

        override suspend fun isCompleted(week: String) = completed

        override suspend fun publish(
            window: CollectionWindow,
            ranking: WeeklyRanking,
            token: String,
        ): Boolean {
            published = ranking
            completed = true
            return true
        }

        override suspend fun fail(
            week: String,
            token: String,
            now: String,
            error: String,
        ) = Unit
    }

    private fun endpoint(
        repository: Fake = Fake(),
        now: Double = tuesday,
        configured: String? = token,
    ) = CollectionEndpoint(configured, repository, catalogue, parser, now) { "lease" }

    private fun feed(
        source: String = "https://www.handelsblatt.com",
        headline: String = "Noxtua raises Series C",
    ) = """{"rss":{"channel":{"item":[{"title":"$headline - Handelsblatt","link":"https://news.google.com/rss/articles/a1",
        "guid":"a1","pubDate":"Wed, 30 Sep 2026 09:00:00 GMT","source":{"#text":"Handelsblatt","@_url":"$source"}}]}}}"""

    private fun body(
        feed: String? = feed(),
        error: String? = null,
        key: String = "2026-10-05-${CollectionWindow.POLICY_VERSION}",
    ): String {
        val search = CollectionWindow.latestCompleteWeek(tuesday).catalogueSearches(catalogue)
        return search.joinToString(",", """{"collectionKey":"$key","results":[""", "]}") {
            """{"query":${JSON.stringify(it.query)},"language":"${it.language.code}"""" +
                (feed?.let { f -> ""","feed":$f""" } ?: "") + (error?.let { e -> ""","error":"$e"""" } ?: "") + "}"
        }
    }

    @Test
    fun requiresAConfiguredTokenAndTheExactBearerHeader() =
        runTest {
            assertEquals(503, endpoint(configured = null).handle("GET", auth, "").status)
            assertEquals(503, endpoint(configured = "short").handle("GET", "Bearer short", "").status)
            for (header in listOf(null, "", token, "Bearer ${token}x", "bearer $token", "Bearer ${token.dropLast(1)}")) {
                assertEquals(401, endpoint().handle("GET", header, "").status, header.toString())
            }
            assertEquals(405, endpoint().handle("DELETE", auth, "").status)
        }

    @Test
    fun planListsTheWorkersOwnSearchesUntilTheWeekIsPublished() =
        runTest {
            val due = JSON.parse<dynamic>(endpoint().handle("GET", auth, "").body)
            assertEquals(true, due.due)
            assertEquals("2026-10-05-${CollectionWindow.POLICY_VERSION}", due.collectionKey)
            assertEquals("2026-09-28T00:00:00.000Z", due.start)
            assertEquals("2026-10-05T00:00:00.000Z", due.end)
            val expected = CollectionWindow.latestCompleteWeek(tuesday).catalogueSearches(catalogue)
            assertEquals(expected.map { it.query }, due.searches.unsafeCast<Array<dynamic>>().map { it.query as String })
            assertEquals("published", JSON.parse<dynamic>(endpoint(Fake(completed = true)).handle("GET", auth, "").body).reason)
            val monday = Date.parse("2026-10-05T05:07:00Z")
            assertEquals("waiting", JSON.parse<dynamic>(endpoint(now = monday).handle("GET", auth, "").body).reason)
        }

    @Test
    fun postedFeedsArePublishedWithTheTrustedPublisherFilter() =
        runTest {
            val repository = Fake()
            val response = endpoint(repository).handle("POST", auth, body())
            assertEquals(200, response.status, response.body)
            assertEquals("published", JSON.parse<dynamic>(response.body).outcome)
            val startup = repository.published!!.startups.single()
            assertEquals("noxtua", startup.id)
            assertEquals("Handelsblatt", startup.news.single().source)
        }

    @Test
    fun untrustedOrFailedFeedsCannotPublishAndStalePlansAreRejected() =
        runTest {
            val untrusted = endpoint().handle("POST", auth, body(feed(source = "https://www.youtube.com")))
            assertEquals(502, untrusted.status)
            assertTrue(untrusted.body.contains("No catalogue companies mentioned"), untrusted.body)
            val failed = endpoint().handle("POST", auth, body(feed = null, error = "Google News returned HTTP 503"))
            assertEquals(502, failed.status)
            assertTrue(failed.body.contains("HTTP 503"), failed.body)
            assertEquals(409, endpoint().handle("POST", auth, body(key = "2026-09-28-${CollectionWindow.POLICY_VERSION}")).status)
            assertEquals(400, endpoint().handle("POST", auth, "{").status)
            assertEquals(413, endpoint().handle("POST", auth, " ".repeat(8_000_001)).status)
        }
}
