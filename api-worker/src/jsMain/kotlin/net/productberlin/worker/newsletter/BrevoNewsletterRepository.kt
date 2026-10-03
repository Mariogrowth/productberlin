package net.productberlin.worker.newsletter

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.productberlin.domain.entity.EmailAddress
import net.productberlin.domain.entity.SubscriptionResult
import net.productberlin.domain.repository.NewsletterRepository

/**
 * Requests a Brevo double opt-in email. The contact joins the list only after clicking the confirmation link, which
 * returns them to [redirectionUrl]. Repeated or already-known addresses look the same as new ones to the caller.
 */
internal class BrevoNewsletterRepository(
    private val client: HttpClient,
    private val apiKey: String,
    private val listId: Long,
    private val templateId: Long,
    private val redirectionUrl: String,
) : NewsletterRepository {
    override suspend fun requestSubscription(email: EmailAddress): SubscriptionResult {
        val response =
            client.post("https://api.brevo.com/v3/contacts/doubleOptinConfirmation") {
                header("api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("email", email.value)
                        putJsonArray("includeListIds") { add(listId) }
                        put("templateId", templateId)
                        put("redirectionUrl", redirectionUrl)
                    }.toString(),
                )
            }
        val status = response.status.value
        if (status in 200..299) return SubscriptionResult.ConfirmationSent
        val error = runCatching { Json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull()
        val code = error?.get("code")?.jsonPrimitive?.content
        val message =
            error
                ?.get("message")
                ?.jsonPrimitive
                ?.content
                .orEmpty()
        return when {
            status == 400 && code in DUPLICATE_CODES -> {
                SubscriptionResult.ConfirmationSent
            }

            // Only an address Brevo rejects counts as invalid; other invalid parameters are our configuration.
            status == 400 && code == "invalid_parameter" && message.contains("email", ignoreCase = true) -> {
                SubscriptionResult.InvalidEmail
            }

            else -> {
                // Logged without the address; the visitor only learns that sign-ups are temporarily unavailable.
                val failure = "Brevo returned HTTP $status${code?.let { " ($it)" } ?: ""}"
                console.error(failure)
                error(failure)
            }
        }
    }

    private companion object {
        val DUPLICATE_CODES = setOf("duplicate_parameter", "duplicate_request")
    }
}
