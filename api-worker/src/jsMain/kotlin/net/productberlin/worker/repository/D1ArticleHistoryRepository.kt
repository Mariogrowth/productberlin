package net.productberlin.worker.repository

import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import net.productberlin.domain.entity.NewsArticle

internal class D1ArticleHistoryRepository(
    private val database: dynamic,
) : ArticleHistoryRepository {
    override suspend fun recorded(weekStarts: List<String>): Map<String, RecordedWeek> {
        if (weekStarts.isEmpty()) return emptyMap()
        val weeksJson = buildJsonArray { weekStarts.forEach { add(JsonPrimitive(it)) } }.toString()
        val result =
            statement(
                """
                SELECT w.week_start, w.reviewed, a.id, a.headline, a.translated_headline, a.source, a.url, a.published_at
                FROM collected_weeks w LEFT JOIN collected_articles a ON a.week_start = w.week_start
                WHERE w.week_start IN (SELECT value FROM json_each(?))
                ORDER BY w.week_start, a.published_at, a.id
                """.trimIndent(),
                weeksJson,
            ).all()
                .unsafeCast<Promise<dynamic>>()
                .await()
        return result.results
            .unsafeCast<Array<dynamic>>()
            .groupBy { it.week_start as String }
            .mapValues { (_, rows) ->
                RecordedWeek(
                    rows.filter { it.id != null }.map {
                        NewsArticle(
                            it.id as String,
                            it.headline as String,
                            it.source as String,
                            it.published_at as String,
                            it.url as String?,
                            translatedHeadline = it.translated_headline as String?,
                        )
                    },
                    (rows.first().reviewed as Number).toInt(),
                )
            }
    }

    override suspend fun record(
        weekStart: String,
        articles: List<NewsArticle>,
        reviewed: Int,
        recordedAt: String,
    ): Boolean {
        val json =
            buildJsonArray {
                for (article in articles) {
                    addJsonObject {
                        put("id", article.id)
                        put("headline", article.headline)
                        put("translated", article.translatedHeadline)
                        put("source", article.source)
                        put("url", article.url)
                        put("published", article.publishedAt)
                    }
                }
            }.toString()
        // Articles are written only by the batch that created this week's row, so a repeated record changes nothing.
        val results =
            database
                .batch(
                    arrayOf(
                        statement(
                            "INSERT INTO collected_weeks (week_start,reviewed,recorded_at) VALUES (?,?,?) ON CONFLICT(week_start) DO NOTHING",
                            weekStart,
                            reviewed,
                            recordedAt,
                        ),
                        statement(
                            """
                            INSERT INTO collected_articles (week_start,id,headline,translated_headline,source,url,published_at)
                            SELECT ?,json_extract(value,'$.id'),json_extract(value,'$.headline'),json_extract(value,'$.translated'),
                                json_extract(value,'$.source'),json_extract(value,'$.url'),json_extract(value,'$.published')
                            FROM json_each(?)
                            WHERE EXISTS (SELECT 1 FROM collected_weeks WHERE week_start=? AND recorded_at=?)
                            ON CONFLICT(week_start,id) DO NOTHING
                            """.trimIndent(),
                            weekStart,
                            json,
                            weekStart,
                            recordedAt,
                        ),
                    ),
                ).unsafeCast<Promise<Array<dynamic>>>()
                .await()
        return (results.first().meta.changes as Number).toInt() == 1
    }

    private fun statement(
        sql: String,
        vararg values: Any?,
    ): dynamic {
        val query = database.prepare(sql)
        return query.bind.apply(query, values)
    }
}
