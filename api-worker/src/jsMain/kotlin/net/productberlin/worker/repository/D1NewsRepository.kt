package net.productberlin.worker.repository

import kotlin.js.Date
import kotlin.js.Promise
import kotlinx.coroutines.await
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.StartupCandidate
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.repository.NewsRepository
import net.productberlin.domain.usecase.CategorizeWeeklyNews
import net.productberlin.worker.logo.CompanyLogos

/**
 * The stored articles of the week behind the latest published live ranking, categorised. Demo editions have no
 * stored articles, so they get no news. Logos are built only for companies with a story, as for the ranking.
 */
internal class D1NewsRepository(
    private val database: dynamic,
    private val catalogue: List<StartupCandidate>,
    private val logos: (startupId: String) -> CompanyLogos = { CompanyLogos() },
) : NewsRepository {
    override suspend fun getWeeklyNews(): WeeklyNews {
        val latest =
            database
                .prepare(LATEST_LIVE_EDITION)
                .first()
                .unsafeCast<Promise<dynamic>>()
                .await()
        if (latest == null) return WeeklyNews("No published news yet", emptyList())
        val result =
            database
                .prepare(
                    "SELECT id,headline,translated_headline,source,url,published_at FROM collected_articles WHERE week_start=? " +
                        "ORDER BY published_at, id",
                ).bind(latest.week_start)
                .all()
                .unsafeCast<Promise<dynamic>>()
                .await()
        val articles =
            result.results.unsafeCast<Array<dynamic>>().map {
                NewsArticle(
                    it.id as String,
                    it.headline as String,
                    it.source as String,
                    it.published_at as String,
                    it.url as String?,
                    translatedHeadline = it.translated_headline as String?,
                )
            }
        // Publisher-feed items of the same week, judged like Google News articles.
        val from = "${latest.week_start}T00:00:00.000Z"
        val until = Date(Date.parse(from) + 7 * 86_400_000.0).toISOString()
        val feedRows =
            database
                .prepare(
                    "SELECT key,source,title,translated_title,link,published_at FROM feed_items " +
                        "WHERE published_at >= ? AND published_at < ? ORDER BY published_at, key",
                ).bind(from, until)
                .all()
                .unsafeCast<Promise<dynamic>>()
                .await()
        val feedArticles =
            feedRows.results.unsafeCast<Array<dynamic>>().map {
                NewsArticle(
                    it.key as String,
                    it.title as String,
                    it.source as String,
                    it.published_at as String,
                    it.link as String,
                    translatedHeadline = it.translated_title as String?,
                )
            }
        val stories =
            CategorizeWeeklyNews()(catalogue, articles + feedArticles).map { story ->
                val urls = logos(story.companyId)
                story.copy(logoUrl = urls.icon, logoFallbackUrl = urls.fullLogo)
            }
        return WeeklyNews(latest.week_label as String, stories)
    }
}

private val LATEST_LIVE_EDITION =
    """
    SELECT week_start, week_label FROM ranking_snapshots
    WHERE status = 'published' AND is_mock = 0
      AND (SELECT COUNT(*) FROM ranking_entries WHERE snapshot_id = ranking_snapshots.id) = expected_count
    ORDER BY week_start DESC, created_at DESC, id DESC LIMIT 1
    """.trimIndent()
