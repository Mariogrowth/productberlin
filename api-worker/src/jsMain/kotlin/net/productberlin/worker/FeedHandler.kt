@file:OptIn(ExperimentalJsExport::class)

package net.productberlin.worker

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.Date
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import net.productberlin.worker.collection.parseCatalogue
import net.productberlin.worker.feeds.FeedEndpoint
import net.productberlin.worker.feeds.parseFeeds
import net.productberlin.worker.repository.D1FeedRepository

/** Cloudflare boundary for the GitHub collector's publisher-feed step. */
@JsExport
fun handleFeeds(
    method: String,
    authorization: String?,
    expectedToken: String?,
    body: String,
    database: dynamic,
    catalogueJson: String,
    feedsJson: String,
): Promise<dynamic> =
    CoroutineScope(EmptyCoroutineContext).promise {
        val response =
            FeedEndpoint(expectedToken, D1FeedRepository(database), parseFeeds(feedsJson), parseCatalogue(catalogueJson), Date.now())
                .handle(method, authorization, body)
        val result = js("({})")
        result.status = response.status
        result.body = response.body
        result.allow = response.allow
        result
    }
