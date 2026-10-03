@file:OptIn(ExperimentalJsExport::class)

package net.productberlin.worker

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import net.productberlin.worker.newsletter.BrevoNewsletterRepository
import net.productberlin.worker.newsletter.SubscriptionEndpoint

/** Cloudflare boundary for newsletter sign-ups. Confirmed subscribers return to the site that sent them. */
@JsExport
fun handleSubscription(
    method: String,
    origin: String?,
    requestOrigin: String,
    contentType: String?,
    body: String,
    brevoApiKey: String?,
    brevoListId: String?,
    brevoTemplateId: String?,
): Promise<dynamic> =
    CoroutineScope(EmptyCoroutineContext).promise {
        val listId = brevoListId?.toLongOrNull()?.takeIf { it > 0 }
        val templateId = brevoTemplateId?.toLongOrNull()?.takeIf { it > 0 }
        val apiKey = brevoApiKey?.takeIf { it.isNotBlank() }
        val client =
            HttpClient(Js) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 10_000
                    connectTimeoutMillis = 5_000
                }
            }
        try {
            val newsletter =
                if (apiKey != null && listId != null && templateId != null) {
                    BrevoNewsletterRepository(client, apiKey, listId, templateId, "$requestOrigin/?subscribed=1")
                } else {
                    if (method == "POST") console.error("Newsletter sign-up is not configured")
                    null
                }
            val response = SubscriptionEndpoint(newsletter).handle(method, origin, requestOrigin, contentType, body)
            val result = js("({})")
            result.status = response.status
            result.body = response.body
            result.allow = response.allow
            result
        } finally {
            client.close()
        }
    }
