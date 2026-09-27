package net.productberlin.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.productberlin.data.repository.KtorStartupRepository

class KtorStartupRepositoryTest {
    @Test
    fun normalizesBaseUrlAndDecodesEmptyLiveRankingWithFutureFields() =
        runTest {
            for (base in listOf("https://example.test", "https://example.test/", "https://example.test///")) {
                val client =
                    client(
                        MockEngine { request ->
                            assertEquals(HttpMethod.Get, request.method)
                            assertEquals("https://example.test/api/rankings/weekly", request.url.toString())
                            respond("""{"weekLabel":"Quiet week","startups":[],"isMock":false,"future":true}""", headers = headers)
                        },
                    )
                try {
                    val ranking = KtorStartupRepository(client, base).getWeeklyRanking()
                    assertEquals("Quiet week", ranking.weekLabel)
                    assertEquals(false, ranking.isMock)
                    assertEquals(emptyList(), ranking.startups)
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun serverFailureIsNotTreatedAsSuccessfulEmptyRanking() =
        runTest {
            val client =
                client(MockEngine { respond("""{"error":"Data temporarily unavailable"}""", HttpStatusCode.ServiceUnavailable, headers) })
            try {
                assertFailsWith<ServerResponseException> { KtorStartupRepository(client, "https://example.test").getWeeklyRanking() }
            } finally {
                client.close()
            }
        }

    @Test
    fun missingApiRouteFails() =
        runTest {
            val client = client(MockEngine { respond("Not found", HttpStatusCode.NotFound) })
            try {
                assertFailsWith<ClientRequestException> { KtorStartupRepository(client, "https://example.test").getWeeklyRanking() }
            } finally {
                client.close()
            }
        }

    @Test
    fun malformedOrIncompleteResponsesFailDecoding() =
        runTest {
            for (body in listOf("<html>SPA fallback</html>", "{}", """{"weekLabel":"Week","startups":null}""")) {
                val client = client(MockEngine { respond(body, headers = headers) })
                try {
                    assertFailsWith<Exception> { KtorStartupRepository(client, "https://example.test").getWeeklyRanking() }
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun cancellationPropagatesToTheCallingScreen() =
        runTest {
            val client = client(MockEngine { throw CancellationException("Screen closed") })
            try {
                assertFailsWith<CancellationException> { KtorStartupRepository(client, "https://example.test").getWeeklyRanking() }
            } finally {
                client.close()
            }
        }

    private val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun client(engine: MockEngine) =
        HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
}
