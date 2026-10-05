package net.productberlin.worker.collection

import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.Date
import kotlinx.coroutines.delay
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.Startup
import net.productberlin.domain.entity.StartupCandidate
import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.domain.usecase.RankStartupMentions
import net.productberlin.worker.repository.CollectionRepository

/**
 * Collects one week. Searches are paced ([pause] between them) because Google News answers bursts of automated
 * requests with HTTP 503. Up to a tenth of the searches (at most [MAX_FAILED_SEARCHES]) may fail without losing the
 * edition; beyond that the run stops early, records the failure and keeps the previous edition for a later retry.
 */
internal class WeeklyCollector(
    private val source: NewsSource,
    private val repository: CollectionRepository,
    private val pause: suspend () -> Unit = { delay(PAUSE_MILLIS) },
    private val log: (String) -> Unit = { console.warn(it) },
) {
    suspend fun refresh(
        catalogue: List<StartupCandidate>,
        window: CollectionWindow,
        now: String,
        token: String,
    ): String {
        if (!repository.acquire(window.collectionKey, now, token)) return "skipped"
        try {
            // Every catalogue company searched by name; the parser keeps trusted publishers only.
            val searches = window.catalogueSearches(catalogue)
            val allowedFailures = minOf(MAX_FAILED_SEARCHES, searches.size / 10)
            val fetched = mutableListOf<NewsArticle>()
            val failures = mutableListOf<String>()
            for ((index, search) in searches.withIndex()) {
                if (index > 0) pause()
                try {
                    fetched += source.search(search)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    failures += "${search.language.code} ${search.query.substringAfter('(').take(60)}: ${failure.message}"
                    check(failures.size <= allowedFailures) {
                        "${failures.size} of ${searches.size} searches failed (allowed $allowedFailures): ${failure.message}"
                    }
                }
            }
            if (failures.isNotEmpty()) log("Published despite ${failures.size} failed searches: ${failures.joinToString("; ")}")
            val articles = validArticles(fetched, window)
            val top = RankStartupMentions()(catalogue, articles)
            check(top.isNotEmpty()) { "No catalogue companies mentioned; retaining the previous ranking" }
            val previous = repository.previousPositions(window.start.take(10))
            val startups =
                top.mapIndexed { index, entry ->
                    val stories =
                        entry.articles
                            .sortedWith(compareByDescending<NewsArticle> { it.publishedAt }.thenBy { it.id })
                            // The same headline syndicated under differently spelled publisher names is shown once.
                            .distinctBy { it.headline.lowercase().trim() }
                            .take(5)
                    val company = entry.company
                    Startup(
                        company.id,
                        company.name,
                        company.description,
                        company.category,
                        previous[company.id]?.minus(index + 1),
                        "${entry.mentionCount} distinct Google News article${if (entry.mentionCount == 1) "" else "s"} " +
                            "mentioned ${company.name} " +
                            "in trusted startup, tech and business publications during ${window.label}. Ranked by distinct article mentions.",
                        stories,
                        entry.mentionCount,
                    )
                }
            val ranking = WeeklyRanking(window.label, startups, false, now, window.query, articles.size)
            check(repository.publish(window, ranking, token)) { "Collection lease lost before publication" }
            return "published"
        } catch (error: Throwable) {
            repository.fail(window.collectionKey, token, Date().toISOString(), error.message ?: "Collection failed")
            throw error
        }
    }

    private fun validArticles(
        articles: List<NewsArticle>,
        window: CollectionWindow,
    ): List<NewsArticle> =
        articles
            .filter { Date.parse(it.publishedAt) >= window.startMillis && Date.parse(it.publishedAt) < window.endMillis }
            .distinctBy { it.id }
            .distinctBy { it.url?.substringBefore('?') ?: it.id }
            .distinctBy { it.headline.lowercase().trim() to it.source.lowercase().trim() }

    private companion object {
        const val PAUSE_MILLIS = 1_500L
        const val MAX_FAILED_SEARCHES = 4
    }
}
