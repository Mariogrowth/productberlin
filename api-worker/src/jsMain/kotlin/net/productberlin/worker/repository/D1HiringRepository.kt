package net.productberlin.worker.repository

import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import net.productberlin.worker.hiring.HiringCount
import net.productberlin.worker.hiring.HiringRepository

internal class D1HiringRepository(
    private val database: dynamic,
) : HiringRepository {
    override suspend fun isRecorded(week: String): Boolean {
        // Awaited into a variable first: comparing an awaited dynamic inline skips the suspension in Kotlin/JS.
        val row =
            statement("SELECT 1 AS found FROM hiring_runs WHERE week_start=?", week)
                .first()
                .unsafeCast<Promise<dynamic>>()
                .await()
        return row != null
    }

    override suspend fun record(
        week: String,
        counts: List<HiringCount>,
        failed: Int,
        recordedAt: String,
    ): Boolean {
        val json =
            buildJsonArray {
                for (count in counts) {
                    addJsonObject {
                        put("id", count.startupId)
                        put("provider", count.provider.code)
                        put("total", count.totalJobs)
                        put("berlin", count.berlinJobs)
                    }
                }
            }.toString()
        // The counts are written only by the batch that created this week's run row, so a repeated post changes nothing.
        val results =
            database
                .batch(
                    arrayOf(
                        statement(
                            "INSERT INTO hiring_runs (week_start,recorded_at,boards,failed) VALUES (?,?,?,?) ON CONFLICT(week_start) DO NOTHING",
                            week,
                            recordedAt,
                            counts.size,
                            failed,
                        ),
                        statement(
                            """
                            INSERT INTO hiring_counts (week_start,startup_id,provider,total_jobs,berlin_jobs)
                            SELECT ?,json_extract(value,'$.id'),json_extract(value,'$.provider'),json_extract(value,'$.total'),
                                json_extract(value,'$.berlin') FROM json_each(?)
                            WHERE EXISTS (SELECT 1 FROM hiring_runs WHERE week_start=? AND recorded_at=?)
                            ON CONFLICT(week_start,startup_id) DO NOTHING
                            """.trimIndent(),
                            week,
                            json,
                            week,
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
