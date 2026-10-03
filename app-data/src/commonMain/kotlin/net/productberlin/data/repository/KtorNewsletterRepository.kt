package net.productberlin.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.productberlin.contract.SubscriptionRequestDto
import net.productberlin.domain.entity.EmailAddress
import net.productberlin.domain.entity.SubscriptionResult
import net.productberlin.domain.repository.NewsletterRepository

/** Sends sign-ups to the Worker, which holds the email provider credentials. */
class KtorNewsletterRepository(
    private val client: HttpClient,
    private val baseUrl: String,
) : NewsletterRepository {
    override suspend fun requestSubscription(email: EmailAddress): SubscriptionResult {
        val response =
            client.post("${baseUrl.trimEnd('/')}/api/subscriptions") {
                expectSuccess = false
                contentType(ContentType.Application.Json)
                setBody(SubscriptionRequestDto(email.value))
            }
        return when (response.status) {
            HttpStatusCode.Accepted -> SubscriptionResult.ConfirmationSent
            HttpStatusCode.BadRequest -> SubscriptionResult.InvalidEmail
            else -> SubscriptionResult.Unavailable
        }
    }
}
