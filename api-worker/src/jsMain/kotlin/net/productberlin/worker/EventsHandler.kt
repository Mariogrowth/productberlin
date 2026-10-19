@file:OptIn(ExperimentalJsExport::class)

package net.productberlin.worker

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.Date
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import net.productberlin.worker.events.EventsEndpoint
import net.productberlin.worker.events.parseEventSources
import net.productberlin.worker.repository.D1EventRepository

/** Cloudflare boundary for the GitHub collector's events step. */
@JsExport
fun handleEvents(
    method: String,
    authorization: String?,
    expectedToken: String?,
    body: String,
    database: dynamic,
    sourcesJson: String,
): Promise<dynamic> =
    CoroutineScope(EmptyCoroutineContext).promise {
        val now = Date.now()
        val response =
            EventsEndpoint(expectedToken, D1EventRepository(database, now), parseEventSources(sourcesJson), now)
                .handle(method, authorization, body)
        val result = js("({})")
        result.status = response.status
        result.body = response.body
        result.allow = response.allow
        result
    }
