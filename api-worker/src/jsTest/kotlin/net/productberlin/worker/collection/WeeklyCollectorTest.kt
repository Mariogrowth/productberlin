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

    /** Two outlets: the least coverage that can rank. */
    private fun pair() = listOf(story("one"), story("two").copy(source = "Second Publisher"))

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
                            (1..8).map { story("related$it", "2026-09-26T12:00:0$it.000Z").copy(source = "Second Publisher") }
                    }
                }
            assertEquals("published", WeeklyCollector(source, repository, MemoryHistory()).refresh(catalogue, window, window.end, "token"))
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
                            return pair()
                        }
                    }
                assertFailsWith<IllegalStateException> {
                    WeeklyCollector(
                        source,
                        repository,
                        MemoryHistory(),
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
            assertEquals("skipped", WeeklyCollector(source, repository, MemoryHistory()).refresh(catalogue, window, window.end, "token"))
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
                    story("second").copy(source = "Second Publisher", headline = "mika accounting second outlet"),
                )
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch) = stories
                }
            WeeklyCollector(source, repository, MemoryHistory()).refresh(catalogue, window, window.end, "token")
            val ranking = repository.published!!
            assertEquals(3, ranking.articleCount)
            assertEquals(2, ranking.startups.single().mentionCount)
            assertEquals(
                listOf("second", "start"),
                ranking.startups
                    .single()
                    .news
                    .map { it.id },
            )
        }

    @Test
    fun sharePriceNotesNeitherCountNorAppear() =
        runTest {
            val repository = RecordingRepository()
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch) =
                        pair() + story("note").copy(headline = "mika-Aktie: Kurs gibt nach nach accounting Zahlen")
                }
            WeeklyCollector(source, repository, MemoryHistory()).refresh(catalogue, window, window.end, "token")
            val ranking = repository.published!!
            assertEquals(2, ranking.startups.single().mentionCount)
            assertEquals(
                listOf("one", "two"),
                ranking.startups
                    .single()
                    .news
                    .map { it.id },
            )
            // Still reviewed: the note was a valid in-week article.
            assertEquals(3, ranking.articleCount)
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
                            pair()
                        } else {
                            emptyList()
                        }
                }
            WeeklyCollector(source, repository, MemoryHistory()).refresh(catalogue, window, window.end, "token")
            assertEquals(
                listOf("one", "two"),
                repository.published!!
                    .startups
                    .single()
                    .news
                    .map { it.id },
            )
        }

    @Test
    fun editionsRankFourWeeksOfMomentumFromStoredHistoryAndStoreTheirOwnWeek() =
        runTest {
            val companies = catalogue + StartupCandidate("noxtua", "Noxtua", "Legal AI", "Legal")
            val earlier = window.previousWeeks(3)
            val history = MemoryHistory(listOf(earlier[0].weekStart, earlier[2].weekStart))
            // Two weeks ago: Noxtua's funding round in two outlets (2 × 3 × 50 % = 3). This week: mika in two outlets (2 × 1).
            history.weeks[earlier[1].weekStart] =
                net.productberlin.worker.repository.RecordedWeek(
                    listOf(
                        NewsArticle("n1", "Noxtua raises €100M Series C", "Dealroom", "2026-09-09T12:00:00.000Z"),
                        NewsArticle("n2", "Noxtua sammelt 100 Millionen Euro ein", "FAZ", "2026-09-09T13:00:00.000Z"),
                    ),
                    40,
                )
            val repository = RecordingRepository()
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch) = pair() + story("unrelated").copy(headline = "Another company story")
                }
            WeeklyCollector(source, repository, history).refresh(companies, window, window.end, "token")
            val ranking = repository.published!!
            assertEquals(listOf("noxtua", "mika"), ranking.startups.map { it.id })
            assertEquals(
                listOf("n2", "n1"),
                ranking.startups
                    .first()
                    .news
                    .map { it.id },
                "news from the stored week, newest first",
            )
            assertEquals(43, ranking.articleCount, "reviewed across the four weeks")
            assertTrue(
                ranking.startups
                    .first()
                    .reason
                    .contains("press momentum"),
                ranking.startups.first().reason,
            )
            // This week is stored for later editions, with only the articles naming a catalogue company.
            assertEquals(
                listOf("one", "two"),
                history.weeks
                    .getValue(window.weekStart)
                    .articles
                    .map { it.id },
            )
            assertEquals(3, history.weeks.getValue(window.weekStart).reviewed)
        }

    @Test
    fun historyWeeksAreStoredOnceWithoutPublishing() =
        runTest {
            val repository = RecordingRepository()
            val history = MemoryHistory()
            var searches = 0
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch): List<NewsArticle> {
                        searches++
                        return pair() + story("unrelated").copy(headline = "Another company story")
                    }
                }
            val collector = WeeklyCollector(source, repository, history, pause = {}, retryPause = {})
            assertEquals("recorded", collector.recordHistory(catalogue, window, window.end))
            assertEquals(
                listOf("one", "two"),
                history.weeks
                    .getValue(window.weekStart)
                    .articles
                    .map { it.id },
            )
            assertEquals(3, history.weeks.getValue(window.weekStart).reviewed)
            assertEquals(null, repository.published)
            assertEquals(2, searches)
            assertEquals("skipped", collector.recordHistory(catalogue, window, window.end))
            assertEquals(2, searches, "a stored week is never searched again")
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
                        return pair()
                    }
                }
            // A small catalogue allows no failed search; the German one is retried once, then the run fails.
            assertFailsWith<IllegalStateException> {
                WeeklyCollector(source, repository, MemoryHistory()).refresh(catalogue, window, window.end, "token")
            }
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
                        override suspend fun search(search: NewsSearch) = pair()
                    }
                assertFailsWith<IllegalStateException> {
                    WeeklyCollector(
                        source,
                        repository,
                        MemoryHistory(),
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
                        return pair()
                    }
                }
            val collector =
                WeeklyCollector(source, repository, MemoryHistory(), pause = { pauses++ }, retryPause = { retryPauses++ }, log = {
                    logged +=
                        it
                })
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
                        return pair()
                    }
                }
            val failure =
                assertFailsWith<IllegalStateException> {
                    WeeklyCollector(
                        source,
                        repository,
                        MemoryHistory(),
                        pause = {},
                        retryPause = {},
                    ).refresh(bigCatalogue, window, window.end, "token")
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
                        return pair()
                    }
                }
            assertEquals(
                "published",
                WeeklyCollector(source, repository, MemoryHistory(), pause = {
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
                    WeeklyCollector(
                        source,
                        repository,
                        MemoryHistory(),
                        pause = {},
                        retryPause = {},
                    ).refresh(bigCatalogue, window, window.end, "token")
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
