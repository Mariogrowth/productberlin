package net.productberlin.data.mock

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** The only installed engine is in-memory. No fetch, DNS, or external requests. */
fun createMockHttpClient(): HttpClient =
    HttpClient(
        MockEngine { request ->
            check(request.method == HttpMethod.Get && request.url.encodedPath == "/api/rankings/weekly") {
                "No mock response configured for ${request.method.value} ${request.url.encodedPath}"
            }
            respond(MockRanking.json, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        },
    ) {
        expectSuccess = true
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
