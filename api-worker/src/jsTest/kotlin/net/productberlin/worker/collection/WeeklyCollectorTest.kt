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
    fun usesSevenCompleteUtcDaysAndFiveNewestStoriesWithoutInflatingMentions() =
        runTest {
            val repository = RecordingRepository()
            val queries = mutableListOf<NewsSearch>()
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch): List<NewsArticle> {
                        queries += search
                        return if (search.query.startsWith("(Berlin")) {
                            listOf(story("one"), story("old", "2026-09-20T23:59:59.000Z"), story("future", window.end))
                        } else {
                            (1..8).map { story("related$it", "2026-09-26T12:00:0$it.000Z") }
                        }
                    }
                }
            assertEquals("published", WeeklyCollector(source, repository).refresh(catalogue, window, window.end, "token"))
            val result = repository.published!!
            assertEquals("2026-09-21 – 2026-09-27", result.weekLabel)
            assertEquals(14, queries.count { it.query.startsWith("(Berlin") })
            assertEquals(setOf(NewsLanguage.English, NewsLanguage.German), queries.map { it.language }.toSet())
            assertEquals(16, queries.size)
            assertTrue(queries.last().query.contains("(\"accounting\" OR \"fintech\")"))
            assertEquals(1, result.articleCount)
            assertEquals(1, result.startups.single().mentionCount)
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
                            check(search.query.startsWith("(Berlin")) { "Feed unavailable" }
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
    fun emptyFollowupsKeepDiscoveryEvidenceAndDoNotInventFiveArticles() =
        runTest {
            val repository = RecordingRepository()
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch) =
                        if (search.query.startsWith("(Berlin")) listOf(story("one")) else emptyList()
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
    fun failureHalfwayThroughDiscoveryDoesNotPublishPartialResults() =
        runTest {
            val repository = RecordingRepository()
            var calls = 0
            val source =
                object : NewsSource {
                    override suspend fun search(search: NewsSearch): List<NewsArticle> {
                        calls++
                        check(calls < 7) { "German feed failed" }
                        return listOf(story("one"))
                    }
                }
            assertFailsWith<IllegalStateException> { WeeklyCollector(source, repository).refresh(catalogue, window, window.end, "token") }
            assertEquals(7, calls)
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
