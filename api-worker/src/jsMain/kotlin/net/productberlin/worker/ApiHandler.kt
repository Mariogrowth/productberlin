@file:OptIn(ExperimentalJsExport::class)

package net.productberlin.worker

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.productberlin.domain.usecase.GetWeeklyRanking
import net.productberlin.worker.mapper.toDto
import net.productberlin.worker.repository.D1StartupRepository

/** Cloudflare interop is confined to this boundary and the D1 adapter. */
@JsExport
fun handleApi(
    path: String,
    method: String,
    database: dynamic,
    version: String,
): Promise<dynamic> =
    CoroutineScope(EmptyCoroutineContext).promise {
        if (path != "/api/health" && path != "/api/rankings/weekly") {
            return@promise response(404, """{"error":"Not found"}""")
        }
        if (method != "GET" && method != "HEAD") {
            return@promise response(405, """{"error":"Method not allowed"}""")
        }
        try {
            if (path == "/api/health") {
                database
                    .prepare("SELECT 1")
                    .first()
                    .unsafeCast<Promise<dynamic>>()
                    .await()
                response(
                    200,
                    buildJsonObject {
                        put("status", "ok")
                        put("version", version)
                    }.toString(),
                )
            } else {
                val ranking = GetWeeklyRanking(D1StartupRepository(database))()
                response(200, wireJson.encodeToString(ranking.toDto()))
            }
        } catch (error: Throwable) {
            console.error("API request failed", error)
            response(503, """{"error":"Data temporarily unavailable"}""")
        }
    }

private fun response(
    status: Int,
    body: String,
): dynamic {
    val result = js("({})")
    result.status = status
    result.body = body
    return result
}

private val wireJson = Json { encodeDefaults = true }
