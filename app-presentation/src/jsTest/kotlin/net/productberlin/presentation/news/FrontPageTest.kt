package net.productberlin.presentation.news

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.entity.WeeklyStory

class FrontPageTest {
    private fun story(
        id: String,
        company: String,
        category: NewsCategory,
        headline: String,
        date: String = "2026-10-14",
        outlets: Int = 0,
        translated: String? = null,
    ) = WeeklyStory(
        id,
        listOf(company),
        category,
        headline,
        "Publisher",
        "${date}T09:00:00.000Z",
        translatedHeadline = translated,
        alsoIn = List(outlets) { "Outlet $it" },
    )

    @Test
    fun kickersSayWhyItMattersFromTheDataAlone() {
        assertEquals("FUNDING · €17M", story("a", "finmid", NewsCategory.Funding, "finmid raises €17M to push embedded lending").kicker())
        assertEquals(
            "FUNDING · €100M",
            story("b", "Noxtua", NewsCategory.Funding, "Noxtua: Über 100 Mio. Euro und C.H.BECK-Mehrheit").kicker(),
        )
        assertEquals("FUNDING · $200M", story("c", "X", NewsCategory.Funding, "X raises \$200 million").kicker())
        assertEquals("FUNDING · €1.2B", story("d", "Y", NewsCategory.Funding, "Y erhält 1,2 Milliarden Euro").kicker())
        assertEquals("FUNDING", story("e", "Z", NewsCategory.Funding, "Z closes its seed round").kicker())
        assertEquals("EXIT", story("f", "Circula", NewsCategory.Growth, "Personio übernimmt Berliner Startup Circula").kicker())
        assertEquals("GROWTH", story("g", "Langdock", NewsCategory.Growth, "Langdock hits €50M ARR").kicker())
        assertEquals(
            "DEAL · \$14.8B",
            story(
                "dh",
                "Delivery Hero",
                NewsCategory.Growth,
                "Delivery Hero Cancels CEO Exit Plans Due to Uber’s \$14.8 Billion Takeover Bid",
            ).kicker(),
            "a bid is not a completed exit",
        )
        assertEquals("PEOPLE · CFO", story("h", "N26", NewsCategory.People, "N26 hires former Postbank finance chief as CFO").kicker())
        assertEquals("PEOPLE · CO-CEO", story("i", "Langdock", NewsCategory.People, "Judith Dada wird Co-Chefin von Langdock").kicker())
        assertEquals("LAUNCH", story("j", "Nox", NewsCategory.Launches, "Nox startet Nachtzug").kicker())
        assertEquals("TROUBLE", story("k", "Helsing", NewsCategory.Trouble, "Helsing drone approval delayed").kicker())
        assertNull(story("l", "Ecosia", NewsCategory.Other, "Ecosia im Interview").kicker())
    }

    @Test
    fun theLeadIsTheBiggestRoundThenTeasersOnePerCategoryPreferringLessCoveredCompanies() {
        val page =
            WeeklyNews(
                "Week",
                listOf(
                    story("f1", "finmid", NewsCategory.Funding, "finmid raises €17M", outlets = 5),
                    story("n1", "Noxtua", NewsCategory.Funding, "Noxtua raises €100M Series C", outlets = 1),
                    story("t1", "Trade Republic", NewsCategory.Launches, "Trade Republic launches free pension account", outlets = 7),
                    story("t2", "Trade Republic", NewsCategory.Growth, "Trade Republic hits €754M revenue"),
                    story("c1", "Circula", NewsCategory.Growth, "Personio acquires Circula", outlets = 2),
                    story("c2", "Circula", NewsCategory.Other, "Circula im Interview"),
                    story("c3", "Circula", NewsCategory.Other, "Circula erklärt Spesen"),
                    story("x1", "N26", NewsCategory.Growth, "N26 expands to Spain"),
                    story("h1", "Helsing", NewsCategory.Trouble, "Helsing drone approval delayed"),
                    story("t3", "Trade Republic", NewsCategory.Trouble, "Trade Republic outage angers customers", outlets = 4),
                    story("e1", "Ecosia", NewsCategory.Other, "Ecosia im Interview", date = "2026-10-17"),
                ),
            ).toFrontPage()
        assertEquals("n1", page.lead?.id, "the biggest funding round leads, even with fewer outlets")
        assertEquals(
            listOf("c1", "t1", "h1", "f1"),
            page.teasers.map { it.id },
            "one per category in order (growth, people, launches, trouble, funding), each company once; an exit beats " +
                "expansion news from a less covered company",
        )
        assertEquals("Ecosia", page.ticker.first().company, "newest first")
        assertEquals(setOf("Ecosia", "Circula", "N26", "Trade Republic"), page.ticker.map { it.company }.toSet())
        assertEquals(
            listOf("t2", "t3"),
            page.ticker.single { it.company == "Trade Republic" }.let {
                listOf(it.newest.id) +
                    it.more.map { s -> s.id }
            },
        )
    }

    @Test
    fun withoutFundingTheMostCoveredStoryLeadsAndOneCompanyTakesOneTickerLine() {
        val page =
            WeeklyNews(
                "Week",
                listOf(
                    story("t1", "Trade Republic", NewsCategory.Launches, "Free pension account", "2026-10-13", outlets = 7),
                    story("t2", "Trade Republic", NewsCategory.Other, "Interview", "2026-10-15"),
                    story("t3", "Trade Republic", NewsCategory.Other, "Explainer", "2026-10-16"),
                    story("t4", "Trade Republic", NewsCategory.Other, "Comparison", "2026-10-14"),
                ),
            ).toFrontPage()
        assertEquals("t1", page.lead?.id)
        val line = page.ticker.single()
        assertEquals("t3", line.newest.id)
        assertEquals(listOf("t2", "t4"), line.more.map { it.id })
    }

    @Test
    fun troubleOnlyLeadsWhenThereIsNothingElse() {
        val trouble = story("t", "Trade Republic", NewsCategory.Trouble, "Interest row with consumer groups", outlets = 4)
        val launch = story("l", "Nox", NewsCategory.Launches, "Nox starts night trains")
        assertEquals("l", WeeklyNews("Week", listOf(trouble, launch)).toFrontPage().lead?.id)
        assertEquals("t", WeeklyNews("Week", listOf(trouble)).toFrontPage().lead?.id)
    }

    @Test
    fun catchUpSkipsFeaturedCompaniesAndHidesWhenThin() {
        val lead = story("f1", "finmid", NewsCategory.Funding, "finmid raises €17M")
        val earlier =
            listOf(
                story("e1", "finmid", NewsCategory.Funding, "finmid bags €17m", "2026-09-23"),
                story("e2", "Noxtua", NewsCategory.Funding, "Noxtua raises €100M", "2026-09-23"),
                story("e3", "mika", NewsCategory.Funding, "mika raises €6M", "2026-09-23"),
                story("e4", "A", NewsCategory.Funding, "A raises €5M", "2026-09-22"),
                story("e5", "B", NewsCategory.Funding, "B raises €4M", "2026-09-22"),
                story("e6", "C", NewsCategory.Funding, "C raises €3M", "2026-09-22"),
            )
        assertEquals(
            listOf("e2", "e3", "e4", "e5"),
            WeeklyNews("Week", listOf(lead), "Earlier", earlier).toFrontPage().catchUp.map { it.id },
            "up to four, never a company already on the page",
        )
        assertEquals(
            emptyList(),
            WeeklyNews("Week", listOf(lead), "Earlier", earlier.take(2)).toFrontPage().catchUp,
            "a single story is not worth a band",
        )
    }
}
