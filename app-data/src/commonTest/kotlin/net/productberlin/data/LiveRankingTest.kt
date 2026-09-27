package net.productberlin.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.Json
import net.productberlin.contract.RankingDto
import net.productberlin.data.mapper.toDomain

class LiveRankingTest {
    @Test
    fun preservesLiveMetadataMentionsAndLinksAcrossTheTransportBoundary() {
        val dto =
            Json.decodeFromString<RankingDto>(
                """{
              "weekLabel":"2026-09-21 – 2026-09-27", "isMock":false,
              "updatedAt":"2026-09-28T06:00:00.000Z", "searchQuery":"Berlin startup", "articleCount":23,
              "startups":[{"id":"mika","name":"mika","description":"Accounting","category":"Finance",
                "movement":null,"reason":"Two mentions","mentionCount":2,
                "news":[{"id":"news","headline":"mika raises funding","source":"Publisher",
                  "publishedAt":"2026-09-25T12:00:00.000Z","url":"https://news.google.com/rss/articles/one"}]}]
            }""",
            )
        val ranking = dto.toDomain()
        assertFalse(ranking.isMock)
        assertEquals(23, ranking.articleCount)
        assertEquals("2026-09-28T06:00:00.000Z", ranking.updatedAt)
        assertEquals("Berlin startup", ranking.searchQuery)
        assertEquals(2, ranking.startups.single().mentionCount)
        assertEquals(
            "https://news.google.com/rss/articles/one",
            ranking.startups
                .single()
                .news
                .single()
                .url,
        )
    }
}
