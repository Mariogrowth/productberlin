package net.productberlin.worker.collection

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class GoogleNewsSourceTest {
    @Test
    fun sendsEncodedQueryAndGermanLocaleAndPassesBodyToParser() =
        runTest {
            val query = "Berlin Gründer \"AI & health\" after:2026-09-21"
            var parsed = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        assertEquals(HttpMethod.Get, request.method)
                        assertEquals("https", request.url.protocol.name)
                        assertEquals("news.google.com", request.url.host)
                        assertEquals("/rss/search", request.url.encodedPath)
                        assertEquals(query, request.url.parameters["q"])
                        assertEquals("de", request.url.parameters["hl"])
                        assertEquals("DE", request.url.parameters["gl"])
                        assertEquals("DE:de", request.url.parameters["ceid"])
                        respond("<rss/>")
                    },
                )
            try {
                val parser =
                    RssParser { xml ->
                        parsed++
                        assertEquals("<rss/>", xml)
                        JSON.parse<dynamic>("""{"rss":{"channel":{}}}""")
                    }
                assertEquals(emptyList(), GoogleNewsSource(client, parser).search(NewsSearch(query, NewsLanguage.German)))
                assertEquals(1, parsed)
            } finally {
                client.close()
            }
        }

    @Test
    fun rateLimitsAndServerErrorsNeverReachTheParser() =
        runTest {
            for (status in listOf(429, 500, 503)) {
                val client = HttpClient(MockEngine { respond("Failure", HttpStatusCode.fromValue(status)) })
                try {
                    val failure =
                        assertFailsWith<IllegalStateException> {
                            GoogleNewsSource(
                                client,
                                RssParser { error("Must not parse") },
                            ).search(NewsSearch("Berlin", NewsLanguage.English))
                        }
                    assertEquals("Google News returned HTTP $status", failure.message)
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun oversizedBodiesAreRejectedBeforeParsing() =
        runTest {
            val client = HttpClient(MockEngine { respond("x".repeat(2_000_001)) })
            try {
                assertFailsWith<IllegalArgumentException> {
                    GoogleNewsSource(client, RssParser { error("Must not parse") }).search(NewsSearch("Berlin", NewsLanguage.English))
                }
            } finally {
                client.close()
            }
        }

    @Test
    fun transportCancellationIsNotConvertedIntoAnEmptyFeed() =
        runTest {
            val client = HttpClient(MockEngine { throw CancellationException("Cancelled transport") })
            try {
                assertFailsWith<CancellationException> {
                    GoogleNewsSource(client, RssParser { error("Must not parse") }).search(NewsSearch("Berlin", NewsLanguage.English))
                }
            } finally {
                client.close()
            }
        }

    @Test
    fun malformedRssFailurePropagates() =
        runTest {
            val client = HttpClient(MockEngine { respond("broken") })
            try {
                val failure =
                    assertFailsWith<IllegalArgumentException> {
                        GoogleNewsSource(
                            client,
                            RssParser { throw IllegalArgumentException("Malformed XML") },
                        ).search(NewsSearch("Berlin", NewsLanguage.English))
                    }
                assertEquals("Malformed XML", failure.message)
            } finally {
                client.close()
            }
        }
}
