package net.productberlin.worker.newsletter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import net.productberlin.domain.entity.EmailAddress
import net.productberlin.domain.entity.SubscriptionResult
import net.productberlin.domain.repository.NewsletterRepository

class SubscriptionEndpointTest {
    private class Recording(
        private val result: SubscriptionResult = SubscriptionResult.ConfirmationSent,
    ) : NewsletterRepository {
        val requested = mutableListOf<String>()

        override suspend fun requestSubscription(email: EmailAddress): SubscriptionResult {
            requested += email.value
            return result
        }
    }

    private val site = "https://site.test"
    private val json = "application/json; charset=utf-8"

    @Test
    fun acceptsSameOriginJsonAndRequestsConfirmation() =
        runTest {
            val newsletter = Recording()
            val response = SubscriptionEndpoint(newsletter).handle("POST", site, site, json, """{"email":" name@domain.de ","extra":1}""")
            assertEquals(202, response.status)
            assertEquals(listOf("name@domain.de"), newsletter.requested)
        }

    @Test
    fun rejectsWrongMethodsOriginsMediaTypesSizesAndBodiesBeforeCallingTheProvider() =
        runTest {
            val newsletter = Recording()
            val endpoint = SubscriptionEndpoint(newsletter)
            val ok = """{"email":"name@domain.de"}"""
            assertEquals(EndpointResponse(405, """{"error":"method_not_allowed"}""", "POST"), endpoint.handle("GET", site, site, json, ""))
            for (origin in listOf(null, "https://evil.test", "http://site.test", "null")) {
                assertEquals(403, endpoint.handle("POST", origin, site, json, ok).status, origin.toString())
            }
            for (type in listOf(null, "text/plain", "application/x-www-form-urlencoded", "multipart/form-data")) {
                assertEquals(415, endpoint.handle("POST", site, site, type, ok).status, type.toString())
            }
            assertEquals(413, endpoint.handle("POST", site, site, json, """{"email":"${"a".repeat(1_100)}@b.de"}""").status)
            for (body in listOf("", "{", "[]", "{}", """{"email":null}""", """{"email":42}""")) {
                assertEquals(
                    EndpointResponse(400, """{"error":"invalid_request"}"""),
                    endpoint.handle("POST", site, site, json, body),
                    body,
                )
            }
            assertEquals(
                EndpointResponse(400, """{"error":"invalid_email"}"""),
                endpoint.handle("POST", site, site, json, """{"email":"name@"}"""),
            )
            assertEquals(emptyList(), newsletter.requested)
        }

    @Test
    fun providerOutcomesAndMissingConfigurationMapToStableResponses() =
        runTest {
            val ok = """{"email":"name@domain.de"}"""
            assertEquals(400, SubscriptionEndpoint(Recording(SubscriptionResult.InvalidEmail)).handle("POST", site, site, json, ok).status)
            assertEquals(503, SubscriptionEndpoint(Recording(SubscriptionResult.Unavailable)).handle("POST", site, site, json, ok).status)
            assertEquals(
                EndpointResponse(503, """{"error":"unavailable"}"""),
                SubscriptionEndpoint(null).handle("POST", site, site, json, ok),
            )
            assertEquals(400, SubscriptionEndpoint(null).handle("POST", site, site, json, """{"email":"name@"}""").status)
        }
}
