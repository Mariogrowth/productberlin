package net.productberlin.worker.newsletter

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import net.productberlin.domain.entity.EmailAddress
import net.productberlin.domain.entity.SubscriptionResult

class BrevoNewsletterRepositoryTest {
    private val email = requireNotNull(EmailAddress.parse("name@domain.de"))

    private fun repository(engine: MockEngine) =
        BrevoNewsletterRepository(HttpClient(engine), "secret-key", 3, 1, "https://site.test/?subscribed=1")

    @Test
    fun requestsDoubleOptInForTheListTemplateAndReturnUrl() =
        runTest {
            val result =
                repository(
                    MockEngine { request ->
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals("https://api.brevo.com/v3/contacts/doubleOptinConfirmation", request.url.toString())
                        assertEquals("secret-key", request.headers["api-key"])
                        assertEquals(
                            "application/json",
                            request.body.contentType
                                ?.withoutParameters()
                                ?.toString(),
                        )
                        assertEquals(
                            Json.parseToJsonElement(
                                """{"email":"name@domain.de","includeListIds":[3],"templateId":1,"redirectionUrl":"https://site.test/?subscribed=1"}""",
                            ),
                            Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject,
                        )
                        respond("", HttpStatusCode.Created)
                    },
                ).requestSubscription(email)
            assertEquals(SubscriptionResult.ConfirmationSent, result)
        }

    @Test
    fun duplicatesLookLikeNewSignUpsAndOnlyRejectedAddressesAreInvalid() =
        runTest {
            for ((body, expected) in listOf(
                """{"code":"duplicate_parameter","message":"Contact already exist"}""" to SubscriptionResult.ConfirmationSent,
                """{"code":"duplicate_request","message":"Request already received"}""" to SubscriptionResult.ConfirmationSent,
                """{"code":"invalid_parameter","message":"Invalid email address"}""" to SubscriptionResult.InvalidEmail,
            )) {
                assertEquals(expected, repository(MockEngine { respond(body, HttpStatusCode.BadRequest) }).requestSubscription(email), body)
            }
            assertEquals(
                SubscriptionResult.ConfirmationSent,
                repository(
                    MockEngine {
                        respond("", HttpStatusCode.NoContent)
                    },
                ).requestSubscription(email),
            )
        }

    @Test
    fun configurationAuthAndProviderFailuresThrowSoTheVisitorCanRetry() =
        runTest {
            for ((status, body) in listOf(
                HttpStatusCode.BadRequest to """{"code":"invalid_parameter","message":"templateId is invalid"}""",
                HttpStatusCode.BadRequest to "not json",
                HttpStatusCode.Unauthorized to """{"code":"unauthorized","message":"Key not found"}""",
                HttpStatusCode.TooManyRequests to "",
                HttpStatusCode.InternalServerError to "",
            )) {
                assertFailsWith<IllegalStateException>(
                    "$status $body",
                ) { repository(MockEngine { respond(body, status) }).requestSubscription(email) }
            }
        }

    @Test
    fun failuresCarryBrevosExplanationWithoutTheAddress() =
        runTest {
            val failure =
                assertFailsWith<IllegalStateException> {
                    repository(
                        MockEngine {
                            respond(
                                """{"code":"invalid_parameter","message":"Template 1 is inactive for Name@Domain.de"}""",
                                HttpStatusCode.BadRequest,
                            )
                        },
                    ).requestSubscription(email)
                }
            assertEquals("Brevo returned HTTP 400 (invalid_parameter): Template 1 is inactive for [email]", failure.message)
        }
}
