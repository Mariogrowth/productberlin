package net.productberlin.worker.collection

import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.StartupCandidate
import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.worker.repository.CollectionRepository

class WeeklyCollectorTest {
    private val window = CollectionWindow.latestCompleteWeek(Date.parse("2026-09-28T06:00:00Z"))
    private val catalogue =
        listOf(StartupCandidate("mika", "mika", "AI accounting", "Finance", contextKeywords = listOf("accounting", "fintech")))

    private fun story(
        id: String,
        date: String = "2026-09-25T12:00:00.000Z",
    ) = NewsArticle(id, "mika announces accounting product $id", "Publisher", date, "https://news.google.com/rss/articles/$id")

    @Test
    fun countsInWeekCatalogueSearchArticlesOnceAndShowsTheFiveNewest() =
        runTest {
            val repository = RecordingRepository()
            val queries = mutableListOf<NewsSearch>()
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch): List<NewsArticle> {
                        queries += search
                        return listOf(story("one"), story("old", "2026-09-20T23:59:59.000Z"), story("future", window.end)) +
                            (1..8).map { story("related$it", "2026-09-26T12:00:0$it.000Z") }
                    }
                }
            assertEquals("published", WeeklyCollector(source, repository).refresh(catalogue, window, window.end, "token"))
            val result = repository.published!!
            assertEquals("2026-09-21 – 2026-09-27", result.weekLabel)
            assertEquals(setOf(NewsLanguage.English, NewsLanguage.German), queries.map { it.language }.toSet())
            assertEquals(2, queries.size)
            assertTrue(queries.all { it.query == "after:2026-09-21 before:2026-09-28 (\"mika\")" })
            // Every in-week article naming the company counts once, however many searches return it.
            assertEquals(9, result.articleCount)
            assertEquals(9, result.startups.single().mentionCount)
            assertEquals(2, result.startups.single().movement)
            assertEquals(
                listOf("related8", "related7", "related6", "related5", "related4"),
                result.startups
                    .single()
                    .news
                    .map { it.id },
            )
            assertFalse(result.isMock)
        }

    @Test
    fun emptyFeedOrFailedEnrichmentNeverPublishesAndRecordsFailure() =
        runTest {
            for (empty in listOf(true, false)) {
                val repository = RecordingRepository()
                val source =
                    object : NewsSource {
                        override suspend fun search(search: NewsSearch): List<NewsArticle> {
                            if (empty) return emptyList()
                            check(search.language == NewsLanguage.English) { "Feed unavailable" }
                            return listOf(story("one"))
                        }
                    }
                assertFailsWith<IllegalStateException> {
                    WeeklyCollector(
                        source,
                        repository,
                    ).refresh(catalogue, window, window.end, "token")
                }
                assertTrue(repository.failed)
                assertEquals(null, repository.published)
            }
        }

    @Test
    fun duplicateScheduledEventDoesNotFetchOrPublish() =
        runTest {
            val repository = RecordingRepository(allow = false)
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch): List<NewsArticle> = error("Must not fetch")
                }
            assertEquals("skipped", WeeklyCollector(source, repository).refresh(catalogue, window, window.end, "token"))
            assertEquals(null, repository.published)
        }

    @Test
    fun dateAndIdentityFiltersDoNotInflateCoverage() =
        runTest {
            val repository = RecordingRepository()
            val stories =
                listOf(
                    story("start", window.start),
                    story("same-url").copy(url = story("start").url + "?tracking=1"),
                    story("same-title").copy(headline = story("start").headline.uppercase()),
                    story("bad-date", "invalid"),
                    story("before", "2026-09-20T23:59:59.999Z"),
                    story("end", window.end),
                    story("other-company").copy(headline = "Another startup raises funding"),
                )
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch) = stories
                }
            WeeklyCollector(source, repository).refresh(catalogue, window, window.end, "token")
            val ranking = repository.published!!
            assertEquals(2, ranking.articleCount)
            assertEquals(1, ranking.startups.single().mentionCount)
            assertEquals(
                listOf("start"),
                ranking.startups
                    .single()
                    .news
                    .map { it.id },
            )
        }

    @Test
    fun fewMatchesShowOnlyRealArticles() =
        runTest {
            val repository = RecordingRepository()
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch) =
                        if (search.language ==
                            NewsLanguage.English
                        ) {
                            listOf(story("one"))
                        } else {
                            emptyList()
                        }
                }
            WeeklyCollector(source, repository).refresh(catalogue, window, window.end, "token")
            assertEquals(
                listOf("one"),
                repository.published!!
                    .startups
                    .single()
                    .news
                    .map { it.id },
            )
        }

    @Test
    fun failureHalfwayThroughTheSearchesDoesNotPublishPartialResults() =
        runTest {
            val repository = RecordingRepository()
            var calls = 0
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch): List<NewsArticle> {
                        calls++
                        check(search.language != NewsLanguage.German) { "German feed failed" }
                        return listOf(story("one"))
                    }
                }
            // A small catalogue allows no failed search; the German one is retried once, then the run fails.
            assertFailsWith<IllegalStateException> { WeeklyCollector(source, repository).refresh(catalogue, window, window.end, "token") }
            assertEquals(3, calls)
            assertTrue(repository.failed)
            assertEquals(null, repository.published)
        }

    @Test
    fun lostLeaseAndDatabaseFailureAreReportedWithoutSuccessfulPublication() =
        runTest {
            for (repository in listOf(RecordingRepository(publishResult = false), RecordingRepository(publicationError = true))) {
                val source =
                    object : NewsSource {
                        override suspend fun search(search: NewsSearch) = listOf(story("one"))
                    }
                assertFailsWith<IllegalStateException> {
                    WeeklyCollector(
                        source,
                        repository,
                    ).refresh(catalogue, window, window.end, "token")
                }
                assertTrue(repository.failed)
                assertEquals(null, repository.published)
            }
        }

    private val bigCatalogue =
        (1..120).map { StartupCandidate("c$it", "Company $it", "Description", "Tech") } + catalogue

    private fun budget(searches: Int) = Triple(searches, minOf(4, searches / 10), (49 - searches).coerceAtLeast(0))

    @Test
    fun failedSearchesAreRetriedOnceAfterAPauseAndRecoveredOnesCount() =
        runTest {
            val (searches, allowed, retries) = budget(window.catalogueSearches(bigCatalogue).size)
            val failing = allowed + 2 // more than allowed, fewer than allowed + retries
            assertTrue(retries >= failing, "test needs spare retries, got $retries")
            var calls = 0
            var pauses = 0
            var retryPauses = 0
            val logged = mutableListOf<String>()
            val repository = RecordingRepository()
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch): List<NewsArticle> {
                        calls++
                        check(calls > failing) { "Google News returned HTTP 503" } // the first searches fail once
                        return listOf(story("one"))
                    }
                }
            val collector =
                WeeklyCollector(source, repository, pause = { pauses++ }, retryPause = { retryPauses++ }, log = { logged += it })
            assertEquals("published", collector.refresh(bigCatalogue, window, window.end, "token"))
            assertEquals(searches + failing, calls, "each failed search retried exactly once")
            assertEquals(1, retryPauses)
            assertEquals((searches - 1) + (failing - 1), pauses)
            assertTrue(
                logged.single().contains("$failing failed searches; $failing recovered on retry, 0 still missing"),
                logged.toString(),
            )
        }

    @Test
    fun searchesStillFailingAfterRetriesBeyondTheAllowanceKeepThePreviousEdition() =
        runTest {
            val (_, allowed, _) = budget(window.catalogueSearches(bigCatalogue).size)
            val broken = window.catalogueSearches(bigCatalogue).take(allowed + 1).toSet()
            val repository = RecordingRepository()
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch): List<NewsArticle> {
                        check(search !in broken) { "Google News returned HTTP 503" }
                        return listOf(story("one"))
                    }
                }
            val failure =
                assertFailsWith<IllegalStateException> {
                    WeeklyCollector(source, repository, pause = {}, retryPause = {}).refresh(bigCatalogue, window, window.end, "token")
                }
            assertTrue(failure.message!!.contains("still failed after retries"), failure.message)
            assertTrue(repository.failed)
            assertEquals(null, repository.published)
        }

    @Test
    fun aFewPersistentFailuresStillPublish() =
        runTest {
            val (_, allowed, _) = budget(window.catalogueSearches(bigCatalogue).size)
            val broken = window.catalogueSearches(bigCatalogue).take(allowed).toSet()
            val repository = RecordingRepository()
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch): List<NewsArticle> {
                        check(search !in broken) { "Google News returned HTTP 503" }
                        return listOf(story("one"))
                    }
                }
            assertEquals(
                "published",
                WeeklyCollector(source, repository, pause = {
                }, retryPause = {}, log = {}).refresh(bigCatalogue, window, window.end, "token"),
            )
        }

    @Test
    fun whenEvenRetriesCannotSaveTheRunTheFirstPassStopsEarly() =
        runTest {
            val (_, allowed, retries) = budget(window.catalogueSearches(bigCatalogue).size)
            var calls = 0
            val repository = RecordingRepository()
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch): List<NewsArticle> {
                        calls++
                        error("Google News returned HTTP 503")
                    }
                }
            val failure =
                assertFailsWith<IllegalStateException> {
                    WeeklyCollector(source, repository, pause = {}, retryPause = {}).refresh(bigCatalogue, window, window.end, "token")
                }
            assertEquals(allowed + retries + 1, calls)
            assertTrue(failure.message!!.contains("HTTP 503"), failure.message)
            assertTrue(repository.failed)
        }
}

private class RecordingRepository(
    private val allow: Boolean = true,
    private val publishResult: Boolean = true,
    private val publicationError: Boolean = false,
) : CollectionRepository {
    var published: WeeklyRanking? = null
    var failed = false

    override suspend fun acquire(
        week: String,
        now: String,
        token: String,
    ) = allow

    override suspend fun previousPositions(beforeWeek: String) = mapOf("mika" to 3)

    override suspend fun publish(
        window: CollectionWindow,
        ranking: WeeklyRanking,
        token: String,
    ): Boolean {
        check(!publicationError) { "D1 unavailable" }
        if (publishResult) published = ranking
        return publishResult
    }

    override suspend fun fail(
        week: String,
        token: String,
        now: String,
        error: String,
    ) {
        failed = true
    }
}
