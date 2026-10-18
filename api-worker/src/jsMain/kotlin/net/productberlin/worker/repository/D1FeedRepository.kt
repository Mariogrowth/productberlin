package net.productberlin.worker.repository

import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import net.productberlin.worker.feeds.FeedItem
import net.productberlin.worker.feeds.FeedRepository

internal class D1FeedRepository(
    private val database: dynamic,
) : FeedRepository {
    override suspend fun knownKeys(since: String): List<String> {
        val result =
            statement("SELECT key FROM feed_items WHERE published_at >= ?", since)
                .all()
                .unsafeCast<Promise<dynamic>>()
                .await()
        return result.results.unsafeCast<Array<dynamic>>().map { it.key as String }
    }

    override suspend fun store(
        items: List<FeedItem>,
        classifiedAt: String,
    ): Int {
        if (items.isEmpty()) return 0
        val itemsJson =
            buildJsonArray {
                for (item in items) {
                    addJsonObject {
                        put("key", item.key)
                        put("feed", item.feed.id)
                        put("source", item.feed.name)
                        put("title", item.title)
                        put("translated", item.translatedTitle)
                        put("link", item.link)
                        put("published", item.publishedAt)
                        put("classifier", if (item.stories != null) "model" else "rules")
                    }
                }
            }.toString()
        val storiesJson =
            buildJsonArray {
                for (item in items) {
                    item.stories.orEmpty().forEachIndexed { position, story ->
                        addJsonObject {
                            put("key", item.key)
                            put("position", position)
                            put("companyId", story.companyId)
                            put("companyName", story.companyName)
                            put("category", story.category.name)
                            put("headline", story.headline)
                        }
                    }
                }
            }.toString()
        // Stories are written only for items this batch inserted (same classified_at), so a repeat changes nothing.
        val results =
            database
                .batch(
                    arrayOf(
                        statement(
                            """
                            INSERT INTO feed_items (key,feed,source,title,translated_title,link,published_at,classified_at,classifier)
                            SELECT json_extract(value,'$.key'),json_extract(value,'$.feed'),json_extract(value,'$.source'),
                                json_extract(value,'$.title'),json_extract(value,'$.translated'),json_extract(value,'$.link'),
                                json_extract(value,'$.published'),?,json_extract(value,'$.classifier') FROM json_each(?) WHERE 1
                            ON CONFLICT(key) DO NOTHING
                            """.trimIndent(),
                            classifiedAt,
                            itemsJson,
                        ),
                        statement(
                            """
                            INSERT INTO feed_stories (item_key,position,company_id,company_name,category,headline)
                            SELECT json_extract(value,'$.key'),json_extract(value,'$.position'),json_extract(value,'$.companyId'),
                                json_extract(value,'$.companyName'),json_extract(value,'$.category'),json_extract(value,'$.headline')
                            FROM json_each(?)
                            WHERE EXISTS (SELECT 1 FROM feed_items WHERE key=json_extract(value,'$.key') AND classified_at=?)
                            ON CONFLICT(item_key,position) DO NOTHING
                            """.trimIndent(),
                            storiesJson,
                            classifiedAt,
                        ),
                    ),
                ).unsafeCast<Promise<Array<dynamic>>>()
                .await()
        return (results.first().meta.changes as Number).toInt()
    }

    private fun statement(
        sql: String,
        vararg values: Any?,
    ): dynamic {
        val query = database.prepare(sql)
        return query.bind.apply(query, values)
    }
}
