package net.productberlin.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.StartupCandidate
import net.productberlin.domain.usecase.RankStartupMentions

class RankStartupMentionsTest {
    private val rank = RankStartupMentions()

    private fun company(
        id: String,
        aliases: List<String> = emptyList(),
    ) = StartupCandidate(id, id, "Description", "Category", aliases)

    private fun article(
        id: String,
        title: String,
        publisher: String = "Publisher",
        publishedAt: String = "2026-09-25T12:00:00.000Z",
    ) = NewsArticle(id, title, publisher, publishedAt)

    @Test
    fun countsArticlesOnceAndMatchesAliasesWithoutCountingPublisherOrSubstring() {
        val results =
            rank(
                listOf(company("mika"), company("recovery-cat", listOf("Recovery Cat"))),
                listOf(
                    article("1", "MIKA raises funding; mika expands"),
                    article("1", "MIKA raises funding; mika expands"),
                    article("duplicate-title", "MIKA raises funding; mika expands"),
                    article("2", "Kamikaze drones grow"),
                    article("3", "Berlin funding news", "mika"),
                    article("4", "Recovery Cat partners with Mika"),
                ),
            )
        assertEquals(listOf("mika", "recovery-cat"), results.map { it.company.id })
        assertEquals(listOf(2, 1), results.map { it.mentionCount })
    }

    @Test
    fun ambiguousNamesRequireCompanyContextInTheHeadline() {
        val mika = company("mika").copy(contextKeywords = listOf("accounting", "fintech", "AI"))
        val results =
            rank(
                listOf(mika),
                listOf(
                    article("football", "Football: Mika named semifinalist for Campbell Trophy"),
                    article("music", "Mika announces a tour", "Fintech Weekly"),
                    article("funding", "Mika AI raises funding"),
                    article("product", "Mika launches accounting tools"),
                ),
            )
        assertEquals(listOf("funding", "product"), results.single().articles.map { it.id })
    }

    @Test
    fun limitsToTenPositiveCountsAndBreaksTiesDeterministically() {
        val companies = (0..11).map { company("company${it.toString().padStart(2, '0')}") }
        val articles = companies.map { article(it.id, "${it.name} grows") }
        assertEquals(companies.take(10).map { it.id }, rank(companies.reversed(), articles.reversed()).map { it.company.id })
        assertEquals(emptyList(), rank(companies, emptyList()))
    }

    @Test
    fun tiesGoToTheCompanyWithTheMostRecentArticle() {
        val companies = listOf(company("alpha"), company("nox"), company("zeta"))
        val articles =
            listOf(
                article("a", "alpha grows", publishedAt = "2026-09-29T08:00:00.000Z"),
                article("n", "nox grows", publishedAt = "2026-10-02T09:00:00.000Z"),
                article("z1", "zeta grows", publishedAt = "2026-09-28T07:00:00.000Z"),
                article("z2", "zeta expands", publishedAt = "2026-10-03T10:00:00.000Z"),
            )
        // zeta has two articles; nox and alpha tie on one, and nox's is newer.
        assertEquals(listOf("zeta", "nox", "alpha"), rank(companies, articles).map { it.company.id })
    }

    @Test
    fun duplicateCatalogueIdsAreRejected() {
        assertFailsWith<IllegalArgumentException> { rank(listOf(company("one"), company("one")), emptyList()) }
    }

    @Test
    fun normalizesPunctuationUnicodeAndWhitespaceButRequiresWholeWords() {
        val candidate = company("recovery-cat", listOf("Recovery Cat", "   "))
        assertTrue(rank.mentions(candidate, article("one", "RECOVERY—CAT: Gründer expand")))
        assertTrue(rank.mentions(company("größe"), article("two", "Größe raises funds")))
        assertFalse(rank.mentions(candidate, article("three", "Recovery catchment expands")))
        assertFalse(rank.mentions(candidate, article("four", "   ")))
    }

    @Test
    fun sameHeadlineFromDifferentPublishersCountsAsDistinctCoverage() {
        val articles = listOf(article("a", "one grows", "Publisher A"), article("b", "one grows", "Publisher B"))
        assertEquals(2, rank(listOf(company("one")), articles).single().mentionCount)
    }
}
