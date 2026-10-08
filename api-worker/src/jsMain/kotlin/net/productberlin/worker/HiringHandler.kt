@file:OptIn(ExperimentalJsExport::class)

package net.productberlin.worker

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.Date
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import net.productberlin.worker.hiring.HiringEndpoint
import net.productberlin.worker.hiring.parseJobBoards
import net.productberlin.worker.repository.D1HiringRepository

/** Cloudflare boundary for the GitHub Actions hiring collector. */
@JsExport
fun handleHiring(
    method: String,
    authorization: String?,
    expectedToken: String?,
    body: String,
    database: dynamic,
    catalogueJson: String,
): Promise<dynamic> =
    CoroutineScope(EmptyCoroutineContext).promise {
        val response =
            HiringEndpoint(expectedToken, D1HiringRepository(database), parseJobBoards(catalogueJson), Date.now())
                .handle(method, authorization, body)
        val result = js("({})")
        result.status = response.status
        result.body = response.body
        result.allow = response.allow
        result
    }
