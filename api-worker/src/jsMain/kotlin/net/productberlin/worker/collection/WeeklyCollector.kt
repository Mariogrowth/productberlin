package net.productberlin.worker.collection

import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.Date
import kotlinx.coroutines.delay
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.Startup
import net.productberlin.domain.entity.StartupCandidate
import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.domain.usecase.RankStartupMentions
import net.productberlin.domain.usecase.RankStartupMomentum
import net.productberlin.worker.repository.ArticleHistoryRepository
import net.productberlin.worker.repository.CollectionRepository

/**
 * Collects one week, and publishes an edition ranked by press momentum over that week and the three before it. Searches are paced ([pause] between them) because Google News answers bursts of automated
 * requests with HTTP 503. Failed searches are retried once at the end, after [retryPause], within the spare request
 * budget. Up to a tenth of the searches (at most [MAX_FAILED_SEARCHES]) may still fail without losing the edition;
 * beyond that the run records the failure and keeps the previous edition for a later hourly retry. The first pass
 * stops early once even the retries could no longer save the run.
 */
internal class WeeklyCollector(
    private val source: NewsSource,
    private val repository: CollectionRepository,
    private val history: ArticleHistoryRepository,
    private val pause: suspend () -> Unit = { delay(PAUSE_MILLIS) },
    private val retryPause: suspend () -> Unit = { delay(RETRY_PAUSE_MILLIS) },
    private val log: (String) -> Unit = { console.warn(it) },
) {
    /**
     * Stores an earlier week's articles for later editions' momentum, without publishing anything. Used once, when
     * the history an edition needs is missing; afterwards every edition stores its own week.
     */
    suspend fun recordHistory(
        catalogue: List<StartupCandidate>,
        window: CollectionWindow,
        now: String,
    ): String {
        if (history.recorded(listOf(window.weekStart)).isNotEmpty()) return "skipped"
        val articles = fetchWeek(catalogue, window)
        history.record(window.weekStart, mentioning(catalogue, articles), articles.size, now)
        return "recorded"
    }

    suspend fun refresh(
        catalogue: List<StartupCandidate>,
        window: CollectionWindow,
        now: String,
        token: String,
    ): String {
        if (!repository.acquire(window.collectionKey, now, token)) return "skipped"
        try {
            val articles = fetchWeek(catalogue, window)
            // An empty week usually means a broken feed, not silence: keep the previous edition, and store nothing so a
            // retry can still fill the week.
            check(articles.isNotEmpty()) { "No trusted articles this week; retaining the previous ranking" }
            history.record(window.weekStart, mentioning(catalogue, articles), articles.size, now)
            // Press momentum: this week plus the three before it, from stored history. Missing weeks count as quiet.
            val earlier = window.previousWeeks(MOMENTUM_WEEKS - 1)
            val stored = history.recorded(earlier.map { it.weekStart })
            val weeks = listOf(articles) + earlier.map { stored[it.weekStart]?.articles.orEmpty() }
            val reviewed = articles.size + stored.values.sumOf { it.reviewed }
            val top = RankStartupMomentum()(catalogue, weeks)
            check(top.isNotEmpty()) { "No catalogue company has counted coverage from two publishers; retaining the previous ranking" }
            val previous = repository.previousPositions(window.start.take(10))
            val from = earlier.last().weekStart
            val startups =
                top.mapIndexed { index, entry ->
                    val stories =
                        entry.articles
                            // The same headline syndicated under differently spelled publisher names is shown once.
                            .distinctBy { it.headline.lowercase().trim() }
                            .take(5)
                    val company = entry.company
                    val count = entry.articles.size
                    Startup(
                        company.id,
                        company.name,
                        company.description,
                        company.category,
                        previous[company.id]?.minus(index + 1),
                        "$count article${if (count == 1) "" else "s"} from ${entry.publishers} trusted publishers counted for " +
                            "${company.name} between $from and ${window.label.substringAfter("– ")}. Ranked by press momentum: " +
                            "recent weeks and funding, launches and growth news count most.",
                        stories,
                        count,
                    )
                }
            val ranking = WeeklyRanking(window.label, startups, false, now, window.query, reviewed)
            check(repository.publish(window, ranking, token)) { "Collection lease lost before publication" }
            return "published"
        } catch (error: Throwable) {
            repository.fail(window.collectionKey, token, Date().toISOString(), error.message ?: "Collection failed")
            throw error
        }
    }

    /** Runs every catalogue search for [window] and returns its valid, distinct trusted articles. */
    private suspend fun fetchWeek(
        catalogue: List<StartupCandidate>,
        window: CollectionWindow,
    ): List<NewsArticle> {
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
                "Collected ${window.weekStart} after $firstPassFailures failed searches; ${firstPassFailures - failed.size} " +
                    "recovered on retry, ${failed.size} still missing: ${failed.joinToString(
                        "; ",
                    ) { "${it.language.code} ${it.query.substringAfter('(').take(60)}" }}",
            )
        }
        return validArticles(fetched, window)
    }

    /** Only articles naming a catalogue company are stored; the rest only count as reviewed. */
    private fun mentioning(
        catalogue: List<StartupCandidate>,
        articles: List<NewsArticle>,
    ): List<NewsArticle> {
        val ids =
            RankStartupMentions()
                .matches(catalogue, articles)
                .flatMap { it.articles }
                .map { it.id }
                .toSet()
        return articles.filter { it.id in ids }
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

        const val MOMENTUM_WEEKS = 4
    }
}
