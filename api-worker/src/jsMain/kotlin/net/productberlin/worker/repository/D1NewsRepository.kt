package net.productberlin.worker.repository

import kotlin.js.Date
import kotlin.js.Promise
import kotlinx.coroutines.await
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.StartupCandidate
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.repository.NewsRepository
import net.productberlin.domain.usecase.CategorizeWeeklyNews
import net.productberlin.domain.usecase.SelectCatchUpStories
import net.productberlin.worker.logo.CompanyLogos

/**
 * The stored articles of the latest published live ranking's week and the week before, categorised, plus the
 * weightier stories of the three weeks before that ([SelectCatchUpStories]). Demo editions have no
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
        // The edition's week and the one before: a single week is often too thin for a front page.
        val editionStart = Date.parse("${latest.week_start}T00:00:00.000Z")
        val from = Date(editionStart - 7 * DAY).toISOString()
        val until = Date(editionStart + 7 * DAY).toISOString()
        val stories = stories(articles(from, until))
        // The three weeks before that (the ranking's stored history), only for stories worth catching up on.
        val earlierFrom = Date(editionStart - 28 * DAY).toISOString()
        val earlier = SelectCatchUpStories()(stories(articles(earlierFrom, from)))
        return WeeklyNews(
            label(from, until),
            stories,
            earlierLabel = label(earlierFrom, from).takeIf { earlier.isNotEmpty() },
            earlier = earlier,
        )
    }

    /** Stored Google News articles of the weeks starting in [from, until), and publisher-feed items published then. */
    private suspend fun articles(
        from: String,
        until: String,
    ): List<NewsArticle> {
        val result =
            database
                .prepare(
                    "SELECT id,headline,translated_headline,source,url,published_at FROM collected_articles " +
                        "WHERE week_start >= ? AND week_start < ? ORDER BY published_at, id",
                ).bind(from.take(10), until.take(10))
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
        // Publisher-feed items of the same weeks, judged like Google News articles.
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
        return articles + feedArticles
    }

    private fun stories(articles: List<NewsArticle>) =
        CategorizeWeeklyNews()(catalogue, articles).map { story ->
            val urls = logos(story.companyId)
            story.copy(logoUrl = urls.icon, logoFallbackUrl = urls.fullLogo)
        }

    /** "2026-10-05 – 2026-10-18" for the days in [from, until). */
    private fun label(
        from: String,
        until: String,
    ) = "${from.take(10)} – ${Date(Date.parse(until) - DAY).toISOString().take(10)}"
}

private const val DAY = 86_400_000.0

private val LATEST_LIVE_EDITION =
    """
    SELECT week_start, week_label FROM ranking_snapshots
    WHERE status = 'published' AND is_mock = 0 AND section = 'startups'
      AND (SELECT COUNT(*) FROM ranking_entries WHERE snapshot_id = ranking_snapshots.id) = expected_count
    ORDER BY week_start DESC, created_at DESC, id DESC LIMIT 1
    """.trimIndent()
