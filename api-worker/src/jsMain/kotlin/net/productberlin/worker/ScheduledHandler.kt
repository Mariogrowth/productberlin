@file:OptIn(ExperimentalJsExport::class)

package net.productberlin.worker

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.Date
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import net.productberlin.worker.collection.CollectionWindow
import net.productberlin.worker.collection.GoogleNewsSource
import net.productberlin.worker.collection.RssParser
import net.productberlin.worker.collection.WeeklyCollector
import net.productberlin.worker.collection.parseCatalogue
import net.productberlin.worker.repository.D1CollectionRepository

@JsExport
fun handleScheduled(
    database: dynamic,
    scheduledTime: Double,
    catalogueJson: String,
    parseXml: (String) -> dynamic,
): Promise<String> =
    CoroutineScope(EmptyCoroutineContext).promise {
        val client =
            HttpClient(Js) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 20_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 20_000
                }
            }
        try {
            val result =
                WeeklyCollector(GoogleNewsSource(client, RssParser(parseXml = parseXml)), D1CollectionRepository(database))
                    .refresh(
                        parseCatalogue(catalogueJson),
                        CollectionWindow.latestCompleteWeek(scheduledTime),
                        Date().toISOString(),
                        js("globalThis.crypto.randomUUID()").unsafeCast<String>(),
                    )
            console.log("Weekly Google News collection: $result")
            result
        } finally {
            client.close()
        }
    }
