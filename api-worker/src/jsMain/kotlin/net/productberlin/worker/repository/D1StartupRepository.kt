package net.productberlin.worker.repository

import kotlin.js.Promise
import kotlinx.coroutines.await
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.Startup
import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.domain.repository.StartupRepository

/**
 * Reads one complete, published snapshot in one SQL statement. No partial refresh can leak into the UI.
 * Logos are resolved per ranked company only, from current catalogue metadata rather than the snapshot.
 */
internal class D1StartupRepository(
    private val database: dynamic,
    private val logoUrl: (startupId: String) -> String? = { null },
) : StartupRepository {
    override suspend fun getWeeklyRanking(): WeeklyRanking {
        val result =
            database
                .prepare(RANKING_QUERY)
                .all()
                .unsafeCast<Promise<dynamic>>()
                .await()
        val rows = result.results.unsafeCast<Array<dynamic>>()
        if (rows.isEmpty()) return WeeklyRanking("No published ranking yet", emptyList())
        val startups =
            rows.groupBy { it.startup_id as String }.values.map { entries ->
                val row = entries.first()
                Startup(
                    id = row.startup_id as String,
                    name = row.name as String,
                    description = row.description as String,
                    category = row.category as String,
                    movement = (row.movement as Number?)?.toInt(),
                    reason = row.reason as String,
                    mentionCount = (row.mention_count as Number?)?.toInt(),
                    logoUrl = logoUrl(row.startup_id as String),
                    news =
                        entries.filter { it.news_id != null }.map {
                            NewsArticle(
                                it.news_id as String,
                                it.headline as String,
                                it.source as String,
                                it.published_at as String,
                                it.url as String?,
                                it.summary as String?,
                            )
                        },
                )
            }
        val first = rows.first()
        return WeeklyRanking(
            first.week_label as String,
            startups,
            (first.is_mock as Number).toInt() == 1,
            first.created_at as String?,
            first.search_query as String?,
            (first.article_count as Number?)?.toInt(),
        )
    }
}

private val RANKING_QUERY =
    """
    WITH latest AS (
        SELECT id, week_label, is_mock, created_at, search_query, article_count FROM ranking_snapshots
        WHERE status = 'published'
          AND (SELECT COUNT(*) FROM ranking_entries WHERE snapshot_id = ranking_snapshots.id) = expected_count
        ORDER BY week_start DESC, created_at DESC, id DESC LIMIT 1
    )
    SELECT latest.week_label, latest.is_mock, latest.created_at, latest.search_query, latest.article_count, s.id AS startup_id, s.name, s.description, s.category,
           e.movement, e.reason, e.mention_count, n.url, n.summary, n.id AS news_id, n.headline, n.source, n.published_at
    FROM latest
    JOIN ranking_entries e ON e.snapshot_id = latest.id
    JOIN startups s ON s.id = e.startup_id
    LEFT JOIN news_articles n ON n.snapshot_id = latest.id AND n.startup_id = s.id
    ORDER BY e.position ASC, n.published_at DESC, n.id ASC
    """.trimIndent()
