package net.productberlin.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.productberlin.data.repository.KtorKeyScanRepository
import net.productberlin.domain.entity.KeyFinding
import net.productberlin.domain.entity.KeyScan
import net.productberlin.domain.entity.KeyScanResult
import net.productberlin.domain.entity.KeyType

class KtorKeyScanRepositoryTest {
    private fun repository(
        status: HttpStatusCode,
        body: String = "",
        seen: MutableList<String> = mutableListOf(),
    ) = KtorKeyScanRepository(
        HttpClient(
            MockEngine { request ->
                seen += request.url.toString()
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        },
        "https://example.test/",
    )

    @Test
    fun asksTheSameOriginWorkerAndMapsKnownFindings() =
        runTest {
            val seen = mutableListOf<String>()
            val body =
                """{"domain":"myapp.com","filesScanned":4,"sourceMaps":1,"findings":[
                {"type":"OpenAi","masked":"sk-proj-…dE4x","location":"/app.js"},
                {"type":"FutureKeyType","masked":"x…y","location":"/app.js"}]}"""
            val result = repository(HttpStatusCode.OK, body, seen).scan("myapp.com")
            assertEquals(listOf("https://example.test/api/key-check?domain=myapp.com"), seen)
            assertEquals(
                KeyScanResult.Completed(KeyScan("myapp.com", 4, 1, listOf(KeyFinding(KeyType.OpenAi, "sk-proj-…dE4x", "/app.js")))),
                result,
                "unknown types from a newer Worker are skipped",
            )
        }

    @Test
    fun workerRefusalsBecomeDomainResults() =
        runTest {
            assertEquals(KeyScanResult.InvalidDomain, repository(HttpStatusCode.BadRequest).scan("x.com"))
            assertEquals(KeyScanResult.Unreachable, repository(HttpStatusCode.BadGateway).scan("x.com"))
            assertEquals(KeyScanResult.RateLimited, repository(HttpStatusCode.TooManyRequests).scan("x.com"))
            assertEquals(KeyScanResult.Unavailable, repository(HttpStatusCode.ServiceUnavailable).scan("x.com"))
        }
}
