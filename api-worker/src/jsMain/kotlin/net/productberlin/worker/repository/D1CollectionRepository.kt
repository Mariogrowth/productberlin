package net.productberlin.worker.repository

import kotlin.js.Date
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.worker.collection.CollectionWindow
import net.productberlin.worker.mapper.toDto

internal class D1CollectionRepository(
    private val database: dynamic,
) : CollectionRepository {
    override suspend fun acquire(
        week: String,
        now: String,
        token: String,
    ): Boolean {
        val until = Date(Date.parse(now) + 15 * 60_000).toISOString()
        val result =
            statement(
                """
                INSERT INTO collection_runs (week_start, lease_token, lease_until, status, started_at)
                VALUES (?, ?, ?, 'running', ?)
                ON CONFLICT(week_start) DO UPDATE SET lease_token=excluded.lease_token, lease_until=excluded.lease_until,
                    status='running', started_at=excluded.started_at, finished_at=NULL, error=NULL
                WHERE collection_runs.status='failed' OR (collection_runs.status='running' AND collection_runs.lease_until <= ?)
                """.trimIndent(),
                week,
                token,
                until,
                now,
                now,
            ).run().unsafeCast<Promise<dynamic>>().await()
        return (result.meta.changes as Number).toInt() == 1
    }

    override suspend fun previousPositions(beforeWeek: String): Map<String, Int> {
        val result =
            statement(
                """
                SELECT startup_id, position FROM ranking_entries WHERE snapshot_id=(
                    SELECT id FROM ranking_snapshots WHERE status='published' AND is_mock=0 AND week_start < ?
                    AND (SELECT COUNT(*) FROM ranking_entries WHERE snapshot_id=ranking_snapshots.id)=expected_count
                    ORDER BY week_start DESC, created_at DESC, id DESC LIMIT 1)
                """.trimIndent(),
                beforeWeek,
            ).all().unsafeCast<Promise<dynamic>>().await()
        return result.results.unsafeCast<Array<dynamic>>().associate { (it.startup_id as String) to (it.position as Number).toInt() }
    }

    override suspend fun publish(
        window: CollectionWindow,
        ranking: WeeklyRanking,
        token: String,
    ): Boolean {
        require(ranking.startups.size in 1..10 && ranking.startups.all { (it.mentionCount ?: 0) > 0 })
        val snapshot = "google-news-${window.collectionKey}"
        val json = Json { encodeDefaults = true }.encodeToString(ranking.toDto())
        val draft = "EXISTS (SELECT 1 FROM ranking_snapshots WHERE id=? AND status='draft')"
        val batch =
            arrayOf(
                statement(
                    """
                    INSERT INTO ranking_snapshots (id,week_start,week_label,status,is_mock,created_at,expected_count,search_query,article_count)
                    SELECT ?,?,?,'draft',0,?,?,?,? WHERE EXISTS (
                        SELECT 1 FROM collection_runs WHERE week_start=? AND lease_token=? AND status='running')
                    ON CONFLICT(id) DO NOTHING
                    """.trimIndent(),
                    snapshot,
                    window.start.take(10),
                    ranking.weekLabel,
                    ranking.updatedAt,
                    ranking.startups.size,
                    window.query,
                    ranking.articleCount,
                    window.collectionKey,
                    token,
                ),
                statement(
                    """
                    INSERT INTO startups (id,name,description,category)
                    SELECT json_extract(value,'$.id'),json_extract(value,'$.name'),json_extract(value,'$.description'),
                        json_extract(value,'$.category') FROM json_each(?, '$.startups') WHERE $draft
                    ON CONFLICT(id) DO UPDATE SET name=excluded.name, description=excluded.description, category=excluded.category
                    """.trimIndent(),
                    json,
                    snapshot,
                ),
                statement(
                    """
                    INSERT INTO ranking_entries (snapshot_id,startup_id,position,movement,reason,mention_count)
                    SELECT ?,json_extract(value,'$.id'),CAST(key AS INTEGER)+1,json_extract(value,'$.movement'),
                        json_extract(value,'$.reason'),json_extract(value,'$.mentionCount') FROM json_each(?, '$.startups') WHERE $draft
                    """.trimIndent(),
                    snapshot,
                    json,
                    snapshot,
                ),
                statement(
                    """
                    INSERT INTO news_articles (id,snapshot_id,startup_id,headline,source,url,published_at,summary)
                    SELECT ? || ':' || json_extract(s.value,'$.id') || ':' || n.key,?,json_extract(s.value,'$.id'),
                        json_extract(n.value,'$.headline'),json_extract(n.value,'$.source'),json_extract(n.value,'$.url'),
                        json_extract(n.value,'$.publishedAt'),json_extract(n.value,'$.summary')
                    FROM json_each(?, '$.startups') s JOIN json_each(s.value,'$.news') n WHERE $draft
                    """.trimIndent(),
                    snapshot,
                    snapshot,
                    json,
                    snapshot,
                ),
                statement(
                    """
                    UPDATE ranking_snapshots SET status='published' WHERE id=? AND status='draft'
                    AND (SELECT COUNT(*) FROM ranking_entries WHERE snapshot_id=?)=expected_count
                    """.trimIndent(),
                    snapshot,
                    snapshot,
                ),
                statement(
                    """
                    UPDATE collection_runs SET status='succeeded',finished_at=?,error=NULL
                    WHERE week_start=? AND lease_token=? AND status='running'
                    AND EXISTS (SELECT 1 FROM ranking_snapshots WHERE id=? AND status='published')
                    """.trimIndent(),
                    ranking.updatedAt,
                    window.collectionKey,
                    token,
                    snapshot,
                ),
            )
        val results = database.batch(batch).unsafeCast<Promise<Array<dynamic>>>().await()
        return (results.last().meta.changes as Number).toInt() == 1
    }

    override suspend fun fail(
        week: String,
        token: String,
        now: String,
        error: String,
    ) {
        statement(
            "UPDATE collection_runs SET status='failed',finished_at=?,error=? WHERE week_start=? AND lease_token=? AND status='running'",
            now,
            error.take(500),
            week,
            token,
        ).run().unsafeCast<Promise<dynamic>>().await()
    }

    private fun statement(
        sql: String,
        vararg values: Any?,
    ): dynamic {
        val query = database.prepare(sql)
        return query.bind.apply(query, values)
    }
}
