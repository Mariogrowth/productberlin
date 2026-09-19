package net.productberlin.data

import io.ktor.client.request.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.productberlin.data.mock.createMockHttpClient
import net.productberlin.data.repository.KtorStartupRepository

class MockRankingTest {
    @Test
    fun decodesMockTransportIntoOrderedDomainRanking() =
        runTest {
            val client = createMockHttpClient()
            try {
                val ranking = KtorStartupRepository(client, "https://productberlin.invalid").getWeeklyRanking()
                assertEquals(10, ranking.startups.size)
                assertEquals("almedia", ranking.startups.first().id)
                assertEquals("ecosia", ranking.startups.last().id)
                assertEquals(2, ranking.startups.first().movement)
                assertEquals(-2, ranking.startups.first { it.id == "sennder" }.movement)
                assertEquals(null, ranking.startups.first { it.id == "parloa" }.movement)
                assertEquals(0, ranking.startups.first { it.id == "deepset" }.movement)
                assertTrue(ranking.startups.all { it.news.isNotEmpty() && it.reason.isNotBlank() })
            } finally {
                client.close()
            }
        }

    @Test
    fun unconfiguredMockRoutesFailInsteadOfFallingBackToNetwork() =
        runTest {
            val client = createMockHttpClient()
            try {
                assertFailsWith<IllegalStateException> { client.get("https://productberlin.invalid/unconfigured") }
            } finally {
                client.close()
            }
        }
}
