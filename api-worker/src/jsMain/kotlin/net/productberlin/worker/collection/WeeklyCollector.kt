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
 * requests with HTTP 503. Failed searches are retried once at the end, after [retryPause], within the spare request
 * budget. Up to a tenth of the searches (at most [MAX_FAILED_SEARCHES]) may still fail without losing the edition;
 * beyond that the run records the failure and keeps the previous edition for a later hourly retry. The first pass
 * stops early once even the retries could no longer save the run.
 */
internal class WeeklyCollector(
    private val source: NewsSource,
    private val repository: CollectionRepository,
    private val pause: suspend () -> Unit = { delay(PAUSE_MILLIS) },
    private val retryPause: suspend () -> Unit = { delay(RETRY_PAUSE_MILLIS) },
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
            val retries = (REQUEST_BUDGET - searches.size).coerceAtLeast(0)
            val fetched = mutableListOf<NewsArticle>()
            val failed = mutableListOf<NewsSearch>()
            var lastError: String? = null

            suspend fun attempt(search: NewsSearch): Boolean =
                try {
                    fetched += source.search(search)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    lastError = failure.message
                    false
                }

            for ((index, search) in searches.withIndex()) {
                if (index > 0) pause()
                if (!attempt(search)) failed += search
                check(failed.size <= allowedFailures + retries) {
                    "${failed.size} of ${searches.size} searches failed (allowed $allowedFailures after $retries retries): $lastError"
                }
            }
            val firstPassFailures = failed.size
            if (failed.isNotEmpty() && retries > 0) {
                retryPause()
                val retried = failed.take(retries)
                for ((index, search) in retried.withIndex()) {
                    if (index > 0) pause()
                    if (attempt(search)) failed -= search
                }
            }
            check(failed.size <= allowedFailures) {
                "${failed.size} of ${searches.size} searches still failed after retries (allowed $allowedFailures): $lastError"
            }
            if (firstPassFailures > 0) {
                log(
                    "Published after $firstPassFailures failed searches; ${firstPassFailures - failed.size} recovered on retry, " +
                        "${failed.size} still missing: ${failed.joinToString(
                            "; ",
                        ) { "${it.language.code} ${it.query.substringAfter('(').take(60)}" }}",
                )
            }
            val articles = validArticles(fetched, window)
            // Market notes still count as reviewed articles, but never as mentions or shown news.
            val top = RankStartupMentions()(catalogue, articles.filterNot(MarketNotes::isMarketNote))
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
        const val PAUSE_MILLIS = 3_000L
        const val RETRY_PAUSE_MILLIS = 10_000L
        const val MAX_FAILED_SEARCHES = 4

        /** The Workers free plan allows 50 subrequests per run; keep one in reserve. */
        const val REQUEST_BUDGET = 49
    }
}
