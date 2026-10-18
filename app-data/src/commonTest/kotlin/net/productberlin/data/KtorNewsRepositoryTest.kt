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
import net.productberlin.data.repository.KtorNewsRepository
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.entity.WeeklyStory

class KtorNewsRepositoryTest {
    private fun repository(
        status: HttpStatusCode,
        body: String,
        seen: MutableList<String> = mutableListOf(),
    ) = KtorNewsRepository(
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
    fun readsTheSameOriginWeeklyNewsAndSkipsUnknownCategories() =
        runTest {
            val seen = mutableListOf<String>()
            val body =
                """{"weekLabel":"2026-10-05 – 2026-10-11","stories":[
                {"id":"f1","companies":["finmid"],"category":"Funding","headline":"finmid raises €17M","source":"Startbase",
                 "publishedAt":"2026-10-07T09:00:00.000Z","url":"https://news.example/f1","alsoIn":["quasa.io"]},
                {"id":"x","companies":["X"],"category":"FutureCategory","headline":"h","source":"s","publishedAt":"2026-10-07"}]}"""
            assertEquals(
                WeeklyNews(
                    "2026-10-05 – 2026-10-11",
                    listOf(
                        WeeklyStory(
                            "f1",
                            listOf("finmid"),
                            NewsCategory.Funding,
                            "finmid raises €17M",
                            "Startbase",
                            "2026-10-07T09:00:00.000Z",
                            "https://news.example/f1",
                            alsoIn = listOf("quasa.io"),
                        ),
                    ),
                ),
                repository(HttpStatusCode.OK, body, seen).getWeeklyNews(),
            )
            assertEquals(listOf("https://example.test/api/news/weekly"), seen)
        }

    @Test
    fun aFailingWorkerThrowsSoThePageCanHideTheSection() =
        runTest {
            assertFailsWith<Exception> { repository(HttpStatusCode.ServiceUnavailable, """{"error":"x"}""").getWeeklyNews() }
        }
}
