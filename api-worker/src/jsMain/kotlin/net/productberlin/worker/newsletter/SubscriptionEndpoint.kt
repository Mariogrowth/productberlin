package net.productberlin.worker.newsletter

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.productberlin.contract.SubscriptionRequestDto
import net.productberlin.domain.entity.EmailAddress
import net.productberlin.domain.entity.SubscriptionResult
import net.productberlin.domain.repository.NewsletterRepository
import net.productberlin.domain.usecase.SubscribeToNewsletter

internal data class EndpointResponse(
    val status: Int,
    val body: String,
    val allow: String? = null,
)

/**
 * `POST /api/subscriptions`. Only same-origin JSON requests from the website are accepted. [newsletter] is null when
 * the email provider is not configured, so sign-ups fail closed instead of pretending to succeed.
 */
internal class SubscriptionEndpoint(
    private val newsletter: NewsletterRepository?,
) {
    suspend fun handle(
        method: String,
        origin: String?,
        requestOrigin: String,
        contentType: String?,
        body: String,
    ): EndpointResponse {
        if (method != "POST") return EndpointResponse(405, error("method_not_allowed"), allow = "POST")
        if (origin != requestOrigin) return EndpointResponse(403, error("forbidden"))
        if (contentType?.substringBefore(';')?.trim()?.lowercase() != "application/json") {
            return EndpointResponse(415, error("unsupported_media_type"))
        }
        if (body.length > MAX_BODY) return EndpointResponse(413, error("payload_too_large"))
        val request =
            try {
                lenient.decodeFromString<SubscriptionRequestDto>(body)
            } catch (invalid: SerializationException) {
                return EndpointResponse(400, error("invalid_request"))
            } catch (invalid: IllegalArgumentException) {
                return EndpointResponse(400, error("invalid_request"))
            }
        // A typo is reported as such even when the provider is down or unconfigured.
        if (EmailAddress.parse(request.email) == null) return EndpointResponse(400, error("invalid_email"))
        val repository = newsletter ?: return EndpointResponse(503, error("unavailable"))
        return when (SubscribeToNewsletter(repository)(request.email)) {
            SubscriptionResult.ConfirmationSent -> EndpointResponse(202, """{"status":"confirmation_sent"}""")
            SubscriptionResult.InvalidEmail -> EndpointResponse(400, error("invalid_email"))
            SubscriptionResult.Unavailable -> EndpointResponse(503, error("unavailable"))
        }
    }

    private fun error(code: String) = """{"error":"$code"}"""

    private companion object {
        const val MAX_BODY = 1_024
        val lenient = Json { ignoreUnknownKeys = true }
    }
}
