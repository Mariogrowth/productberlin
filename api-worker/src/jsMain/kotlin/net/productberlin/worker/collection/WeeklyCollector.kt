package net.productberlin.worker.collection

import kotlin.js.Date
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.Startup
import net.productberlin.domain.entity.StartupCandidate
import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.domain.usecase.RankStartupMentions
import net.productberlin.worker.repository.CollectionRepository

internal class WeeklyCollector(
    private val source: NewsSource,
    private val repository: CollectionRepository,
) {
    suspend fun refresh(
        catalogue: List<StartupCandidate>,
        window: CollectionWindow,
        now: String,
        token: String,
    ): String {
        if (!repository.acquire(window.collectionKey, now, token)) return "skipped"
        try {
            val discovery = mutableListOf<NewsArticle>()
            for (query in window.discoverySearches) discovery += source.search(query)
            val articles = validArticles(discovery, window)
            val rank = RankStartupMentions()
            val top = rank(catalogue, articles)
            check(top.isNotEmpty()) { "No catalogue companies mentioned; retaining the previous ranking" }
            val previous = repository.previousPositions(window.start.take(10))
            val startups =
                top.mapIndexed { index, entry ->
                    val related = mutableListOf<NewsArticle>()
                    for (search in window.companySearches(entry.company)) related += source.search(search)
                    val stories =
                        validArticles(related + entry.articles, window)
                            .filter { rank.mentions(entry.company, it) }
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
                            "in English and German Berlin startup and business news during ${window.label}. Ranked by distinct article mentions.",
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
}
