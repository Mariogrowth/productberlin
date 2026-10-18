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
        storedAt: String,
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
                    }
                }
            }.toString()
        val result =
            statement(
                """
                INSERT INTO feed_items (key,feed,source,title,translated_title,link,published_at,stored_at)
                SELECT json_extract(value,'$.key'),json_extract(value,'$.feed'),json_extract(value,'$.source'),
                    json_extract(value,'$.title'),json_extract(value,'$.translated'),json_extract(value,'$.link'),
                    json_extract(value,'$.published'),? FROM json_each(?) WHERE 1
                ON CONFLICT(key) DO NOTHING
                """.trimIndent(),
                storedAt,
                itemsJson,
            ).run()
                .unsafeCast<Promise<dynamic>>()
                .await()
        return (result.meta.changes as Number).toInt()
    }

    private fun statement(
        sql: String,
        vararg values: Any?,
    ): dynamic {
        val query = database.prepare(sql)
        return query.bind.apply(query, values)
    }
}
