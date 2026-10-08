package net.productberlin.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.StartupCandidate

class RankStartupMomentumTest {
    private val catalogue =
        listOf(
            StartupCandidate("noxtua", "Noxtua", "Legal AI", "Legal"),
            StartupCandidate("nox", "Nox", "Night trains", "Mobility"),
            StartupCandidate("zalando", "Zalando", "Fashion", "Retail"),
            StartupCandidate("langdock", "Langdock", "AI workspace", "AI"),
        )
    private val rank = RankStartupMomentum()
    private var next = 0

    private fun article(
        headline: String,
        source: String,
        date: String = "2026-10-01T12:00:00.000Z",
    ) = NewsArticle("a${next++}", headline, source, date)

    @Test
    fun aFundingRoundTwoWeeksAgoOutranksThisWeeksPassingMentions() {
        val thisWeek =
            listOf(
                article("Nox im Interview", "WELT"),
                article("Nox auf der Innotrans", "Spiegel"),
            )
        val twoWeeksAgo =
            listOf(
                article("Noxtua raises €100M Series C", "Dealroom", "2026-09-23T12:00:00.000Z"),
                article("Noxtua erhält 100 Millionen Euro in Series C", "FAZ", "2026-09-23T13:00:00.000Z"),
            )
        val ranked = rank(catalogue, listOf(thisWeek, emptyList(), twoWeeksAgo))
        // Noxtua: two publishers × funding 3 × 50 % = 3.0. Nox: two publishers × other 1 × 100 % = 2.0.
        assertEquals(listOf("noxtua" to 3.0, "nox" to 2.0), ranked.map { it.company.id to it.score })
    }

    @Test
    fun eachPublisherCountsOncePerWeekWithItsStrongestHeadline() {
        val week =
            listOf(
                article("Langdock hits €50M ARR", "Dealroom"),
                article("Langdock: interview with the founders", "Dealroom"),
                article("Langdock raises €20M", "Dealroom"),
                article("Langdock moves its seat to Berlin", "Spiegel"),
            )
        val entry = rank(catalogue, listOf(week)).single()
        // Dealroom: strongest is funding (3); Spiegel: other coverage (1).
        assertEquals(4.0, entry.score)
        assertEquals(2, entry.publishers)
        assertEquals(4, entry.articles.size, "every counted article is kept for display")
    }

    @Test
    fun oneOutletAloneNeverRanksAndUncountedNewsGivesNoPublisher() {
        val week =
            listOf(
                article("Nox raises €10M", "WELT"),
                article("Nox launches Hamburg route", "WELT"),
                article("Zalando-Aktie: Kurs gibt nach", "WELT"),
                article("Was bleibt nach dem Zalando-Aus in Erfurt?", "MDR"),
                article("Zalando opens new hub", "Handelsblatt"),
            )
        assertTrue(rank(catalogue, listOf(week)).isEmpty(), "Nox has one publisher; Zalando one counted publisher")
    }

    @Test
    fun olderWeeksCountLessAndWeeksBeyondFourAreIgnored() {
        fun pair(date: String) = listOf(article("Nox im Interview", "WELT", date), article("Nox im Gespräch", "Spiegel", date))
        val weeks = (0 until 5).map { pair("2026-10-0${5 - it}T12:00:00.000Z") }
        // 2 × (1 + 0.75 + 0.5 + 0.25); the fifth week adds nothing.
        assertEquals(5.0, rank(catalogue, weeks).single().score)
        assertEquals(0.5, rank(catalogue, listOf(emptyList(), emptyList(), emptyList(), pair("2026-09-10T12:00:00.000Z"))).single().score)
    }

    @Test
    fun tiesGoToTheMostRecentCountedArticleThenTheId() {
        val week =
            listOf(
                article("Nox im Interview", "WELT", "2026-10-01T10:00:00.000Z"),
                article("Nox im Gespräch", "Spiegel", "2026-10-01T10:00:00.000Z"),
                article("Langdock im Interview", "WELT", "2026-10-02T10:00:00.000Z"),
                article("Langdock im Gespräch", "Spiegel", "2026-10-01T09:00:00.000Z"),
                article("Noxtua im Interview", "WELT", "2026-10-01T10:00:00.000Z"),
                article("Noxtua im Gespräch", "Spiegel", "2026-10-01T10:00:00.000Z"),
            )
        assertEquals(listOf("langdock", "nox", "noxtua"), rank(catalogue, listOf(week)).map { it.company.id })
    }

    @Test
    fun countedArticlesAreNewestFirstAndAtMostTenCompaniesRank() {
        val many = (1..12).map { StartupCandidate("c$it", "Company$it", "d", "c") }
        val week = many.flatMap { listOf(article("${it.name} im Interview", "WELT"), article("${it.name} im Gespräch", "FAZ")) }
        assertEquals(10, rank(many, listOf(week)).size)
        val entry =
            rank(
                catalogue,
                listOf(
                    listOf(article("Nox im Interview", "WELT", "2026-10-01T10:00:00.000Z")),
                    listOf(article("Nox im Gespräch", "Spiegel", "2026-09-24T10:00:00.000Z")),
                ),
            ).single()
        assertEquals(listOf("2026-10-01T10:00:00.000Z", "2026-09-24T10:00:00.000Z"), entry.articles.map { it.publishedAt })
        assertEquals(1.75, entry.score)
    }
}
