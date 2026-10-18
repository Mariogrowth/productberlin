package net.productberlin.worker.repository

import kotlin.js.Promise
import kotlinx.coroutines.await
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.StartupCandidate
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.repository.NewsRepository
import net.productberlin.domain.usecase.CategorizeWeeklyNews

/**
 * The stored articles of the week behind the latest published live ranking, categorised. Demo editions have no
 * stored articles, so they get no news.
 */
internal class D1NewsRepository(
    private val database: dynamic,
    private val catalogue: List<StartupCandidate>,
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
        return WeeklyNews(latest.week_label as String, CategorizeWeeklyNews()(catalogue, articles))
    }
}

private val LATEST_LIVE_EDITION =
    """
    SELECT week_start, week_label FROM ranking_snapshots
    WHERE status = 'published' AND is_mock = 0
      AND (SELECT COUNT(*) FROM ranking_entries WHERE snapshot_id = ranking_snapshots.id) = expected_count
    ORDER BY week_start DESC, created_at DESC, id DESC LIMIT 1
    """.trimIndent()
