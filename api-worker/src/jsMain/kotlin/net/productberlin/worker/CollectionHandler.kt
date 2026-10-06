@file:OptIn(ExperimentalJsExport::class)

package net.productberlin.worker

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.Date
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import net.productberlin.worker.collection.CollectionEndpoint
import net.productberlin.worker.collection.RssParser
import net.productberlin.worker.collection.parseCatalogue
import net.productberlin.worker.collection.parsePublishers
import net.productberlin.worker.repository.D1CollectionRepository

/** Cloudflare boundary for the GitHub Actions news collector. */
@JsExport
fun handleCollection(
    method: String,
    authorization: String?,
    expectedToken: String?,
    body: String,
    database: dynamic,
    catalogueJson: String,
    publishersJson: String,
): Promise<dynamic> =
    CoroutineScope(EmptyCoroutineContext).promise {
        val parser = RssParser(parsePublishers(publishersJson)::allows) { error("The collector sends parsed feeds, not XML") }
        val response =
            CollectionEndpoint(
                expectedToken,
                D1CollectionRepository(database),
                parseCatalogue(catalogueJson),
                parser,
                Date.now(),
                newToken = { js("globalThis.crypto.randomUUID()").unsafeCast<String>() },
            ).handle(method, authorization, body)
        val result = js("({})")
        result.status = response.status
        result.body = response.body
        result.allow = response.allow
        result
    }
