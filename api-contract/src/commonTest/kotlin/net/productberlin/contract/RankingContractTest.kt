package net.productberlin.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RankingContractTest {
    private val legacy = """{"weekLabel":"Demo","startups":[
        {"id":"one","name":"One","description":"Description","category":"Tech","reason":"Reason"}]}"""

    @Test
    fun legacyPayloadKeepsMockDefaultsAndOptionalFields() {
        val ranking = Json.decodeFromString<RankingDto>(legacy)
        assertTrue(ranking.isMock)
        assertNull(ranking.articleCount)
        assertNull(ranking.updatedAt)
        assertNull(ranking.startups.single().movement)
        assertNull(ranking.startups.single().mentionCount)
        assertNull(ranking.startups.single().logoUrl)
        assertTrue(
            ranking.startups
                .single()
                .news
                .isEmpty(),
        )
    }

    @Test
    fun livePayloadRoundTripsUnicodeQuotesNewsAndMetadata() {
        val news =
            NewsDto(
                "news",
                "Gründer: \"AI\" & growth",
                "Publisher",
                "2026-09-25T12:00:00Z",
                "https://news.google.com/rss/articles/a?oc=5",
                "A summary",
            )
        val startup = StartupDto("one", "One", "Description", "Tech", -2, "Reason", listOf(news), 7, "https://cdn.example.com/one.webp")
        val ranking = RankingDto("Weekly", listOf(startup), false, "2026-09-28T06:00:00Z", "Berlin startup", 33)
        assertEquals(ranking, Json.decodeFromString<RankingDto>(Json.encodeToString(ranking)))
    }

    @Test
    fun requiredFieldsAndWrongTypesFailInsteadOfBecomingEmptyRankings() {
        for (payload in listOf(
            "{}",
            """{"weekLabel":"Week","startups":null}""",
            legacy.replace("\"one\"", "null"),
            legacy.replace("\"Demo\"", "[]"),
        )) {
            assertFailsWith<SerializationException> { Json.decodeFromString<RankingDto>(payload) }
        }
    }

    @Test
    fun tolerantClientCanReadFutureServerFields() {
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(Json.decodeFromString<RankingDto>(legacy), json.decodeFromString<RankingDto>(legacy.dropLast(1) + ",\"future\":42}"))
    }
}
