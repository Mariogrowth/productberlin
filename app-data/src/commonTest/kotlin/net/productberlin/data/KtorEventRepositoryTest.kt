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
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.productberlin.data.repository.KtorEventRepository
import net.productberlin.domain.entity.EventKind
import net.productberlin.domain.entity.StartupEvent

class KtorEventRepositoryTest {
    private fun repository(
        status: HttpStatusCode,
        body: String,
        seen: MutableList<String> = mutableListOf(),
    ) = KtorEventRepository(
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
    fun readsTheSameOriginEventsAndSkipsUnknownKinds() =
        runTest {
            val seen = mutableListOf<String>()
            val body =
                """{"events":[{"id":"a:1","title":"Demo Day","startsAt":"2026-10-21T16:00:00.000Z","organiser":"Merantix AI Campus",
                "kind":"Startup","venue":"Merantix AI Campus","url":"https://luma.com/x"},
                {"id":"a:2","title":"?","startsAt":"2026-10-21T16:00:00.000Z","organiser":"X","kind":"Party"}]}"""
            assertEquals(
                listOf(
                    StartupEvent(
                        "a:1",
                        "Demo Day",
                        "2026-10-21T16:00:00.000Z",
                        null,
                        "Merantix AI Campus",
                        EventKind.Startup,
                        "Merantix AI Campus",
                        "https://luma.com/x",
                    ),
                ),
                repository(HttpStatusCode.OK, body, seen).getUpcomingEvents(),
            )
            assertEquals(listOf("https://example.test/api/events"), seen)
            assertFailsWith<Exception> { repository(HttpStatusCode.ServiceUnavailable, "{}").getUpcomingEvents() }
        }
}
