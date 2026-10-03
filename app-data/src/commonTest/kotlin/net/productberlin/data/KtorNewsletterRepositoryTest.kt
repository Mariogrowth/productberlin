package net.productberlin.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.productberlin.data.repository.KtorNewsletterRepository
import net.productberlin.domain.entity.EmailAddress
import net.productberlin.domain.entity.SubscriptionResult

class KtorNewsletterRepositoryTest {
    private val email = requireNotNull(EmailAddress.parse("name@domain.de"))

    private fun client(engine: MockEngine) =
        HttpClient(engine) {
            // Mirrors the browser client: unexpected statuses throw unless a request opts out.
            expectSuccess = true
            install(ContentNegotiation) { json(Json) }
        }

    @Test
    fun postsOnlyTheAddressAsJsonToTheSameOriginEndpoint() =
        runTest {
            val client =
                client(
                    MockEngine { request ->
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals("https://example.test/api/subscriptions", request.url.toString())
                        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
                        assertEquals("""{"email":"name@domain.de"}""", request.body.toByteArray().decodeToString())
                        respond("", HttpStatusCode.Accepted)
                    },
                )
            assertEquals(
                SubscriptionResult.ConfirmationSent,
                KtorNewsletterRepository(client, "https://example.test/").requestSubscription(email),
            )
        }

    @Test
    fun mapsRejectedAddressesAndEveryOtherStatusWithoutThrowing() =
        runTest {
            for ((status, expected) in listOf(
                HttpStatusCode.BadRequest to SubscriptionResult.InvalidEmail,
                HttpStatusCode.Forbidden to SubscriptionResult.Unavailable,
                HttpStatusCode.TooManyRequests to SubscriptionResult.Unavailable,
                HttpStatusCode.ServiceUnavailable to SubscriptionResult.Unavailable,
                HttpStatusCode.OK to SubscriptionResult.Unavailable,
            )) {
                val client = client(MockEngine { respond("", status) })
                assertEquals(
                    expected,
                    KtorNewsletterRepository(client, "https://example.test").requestSubscription(email),
                    status.toString(),
                )
            }
        }
}
