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
    private val trusted = TrustedPublishers(listOf("handelsblatt.com", "faz.net"))
    private val window = CollectionWindow.latestCompleteWeek(tuesday)
    private val earlier = window.previousWeeks(3).map { it.weekStart }
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
        history: MemoryHistory = MemoryHistory(earlier),
    ) = CollectionEndpoint(configured, repository, history, catalogue, parser, now) { "lease" }

    /** Noxtua's round in two trusted outlets, the least coverage that can rank. */
    private fun feed(
        source: String = "https://www.handelsblatt.com",
        headline: String = "Noxtua raises Series C",
        date: String = "Wed, 30 Sep 2026 09:00:00 GMT",
    ) = """{"rss":{"channel":{"item":[{"title":"$headline - Handelsblatt","link":"https://news.google.com/rss/articles/a1",
        "guid":"a1","pubDate":"$date","source":{"#text":"Handelsblatt","@_url":"$source"}},
        {"title":"Noxtua erhält Millionen - FAZ","link":"https://news.google.com/rss/articles/a2",
        "guid":"a2","pubDate":"$date","source":{"#text":"FAZ","@_url":"${if (source.contains(
            "handelsblatt",
        )
    ) {
        "https://www.faz.net"
    } else {
        source
    }}"}}]}}}"""

    private fun body(
        feed: String? = feed(),
        error: String? = null,
        key: String = "2026-10-05-${CollectionWindow.POLICY_VERSION}",
        searched: CollectionWindow = window,
    ): String {
        val search = searched.catalogueSearches(catalogue)
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
            assertEquals(listOf("FAZ", "Handelsblatt"), startup.news.map { it.source }.sorted())
        }

    @Test
    fun untrustedOrFailedFeedsCannotPublishAndStalePlansAreRejected() =
        runTest {
            val untrusted = endpoint().handle("POST", auth, body(feed(source = "https://www.youtube.com")))
            assertEquals(502, untrusted.status)
            assertTrue(untrusted.body.contains("No trusted articles this week"), untrusted.body)
            val failed = endpoint().handle("POST", auth, body(feed = null, error = "Google News returned HTTP 503"))
            assertEquals(502, failed.status)
            assertTrue(failed.body.contains("HTTP 503"), failed.body)
            assertEquals(409, endpoint().handle("POST", auth, body(key = "2026-09-28-${CollectionWindow.POLICY_VERSION}")).status)
            assertEquals(400, endpoint().handle("POST", auth, "{").status)
            assertEquals(413, endpoint().handle("POST", auth, " ".repeat(8_000_001)).status)
        }

    @Test
    fun missingHistoryWeeksArePlannedFirstOldestFirstAndOnlyStored() =
        runTest {
            val history = MemoryHistory()
            val repository = Fake()
            val plan = JSON.parse<dynamic>(endpoint(repository, history = history).handle("GET", auth, "").body)
            assertEquals("history", plan.mode)
            assertEquals("2026-09-07-history", plan.collectionKey)
            assertEquals("2026-09-07T00:00:00.000Z", plan.start)
            val oldest = window.previousWeeks(3).last()
            val posted =
                endpoint(repository, history = history)
                    .handle("POST", auth, body(feed(date = "Wed, 09 Sep 2026 09:00:00 GMT"), key = "2026-09-07-history", searched = oldest))
            assertEquals(200, posted.status, posted.body)
            assertEquals("""{"outcome":"recorded","collectionKey":"2026-09-07-history"}""", posted.body)
            assertEquals(
                2,
                history.weeks
                    .getValue("2026-09-07")
                    .articles.size,
            )
            assertEquals(null, repository.published, "history units never publish")
            assertEquals(
                "2026-09-14-history",
                JSON.parse<dynamic>(endpoint(repository, history = history).handle("GET", auth, "").body).collectionKey,
            )
            // History is filled even before Monday 06:00 UTC; only the edition waits.
            val monday = Date.parse("2026-10-05T05:07:00Z")
            assertEquals(
                "history",
                JSON.parse<dynamic>(endpoint(now = monday, history = MemoryHistory()).handle("GET", auth, "").body).mode,
            )
            // A post for a week that is no longer the next unit is stale.
            assertEquals(
                409,
                endpoint(repository, history = history).handle("POST", auth, body(key = "2026-09-07-history", searched = oldest)).status,
            )
        }

    @Test
    fun withHistoryInPlaceTheEditionIsPlanned() =
        runTest {
            val plan = JSON.parse<dynamic>(endpoint().handle("GET", auth, "").body)
            assertEquals("edition", plan.mode)
            assertEquals("2026-10-05-${CollectionWindow.POLICY_VERSION}", plan.collectionKey)
        }
}
