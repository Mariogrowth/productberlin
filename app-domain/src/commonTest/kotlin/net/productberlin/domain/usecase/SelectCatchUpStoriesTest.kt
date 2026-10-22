package net.productberlin.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.WeeklyStory

class SelectCatchUpStoriesTest {
    private val select = SelectCatchUpStories()

    private fun story(
        id: String,
        company: String,
        category: NewsCategory,
        headline: String,
        outlets: Int = 0,
        date: String = "2026-09-23",
    ) = WeeklyStory(id, listOf(company), category, headline, "Publisher", "${date}T09:00:00.000Z", alsoIn = List(outlets) { "Outlet $it" })

    @Test
    fun exitsFirstThenTheLargestAmountThenCoverage() {
        val picked =
            select(
                listOf(
                    story("mika", "mika", NewsCategory.Funding, "mika raises €6M", outlets = 3),
                    story("pliant", "Pliant", NewsCategory.Funding, "Pliant sichert sich 6 Mio. Euro"),
                    story("noxtua", "Noxtua", NewsCategory.Funding, "Noxtua raises €100M Series C", outlets = 5),
                    story("dh", "Delivery Hero", NewsCategory.Growth, "Uber’s \$14.8 billion takeover bid for Delivery Hero"),
                    story("circula", "Circula", NewsCategory.Growth, "Personio acquires Circula"),
                    story("n26", "N26", NewsCategory.People, "N26 hires former Postbank finance chief as CFO"),
                ),
            )
        assertEquals(listOf("circula", "dh", "noxtua", "mika", "pliant", "n26"), picked.map { it.id })
    }

    @Test
    fun onlyStoriesThatAgeWellAndOnePerCompany() {
        val picked =
            select(
                listOf(
                    story("round", "finmid", NewsCategory.Funding, "finmid raises €17M"),
                    story("again", "finmid", NewsCategory.Funding, "finmid bags €17m for vehicle loans", date = "2026-09-24"),
                    story("seed", "Z", NewsCategory.Funding, "Z closes its seed round"),
                    story("arr", "Langdock", NewsCategory.Growth, "Langdock hits €50M ARR"),
                    story("launch", "Nox", NewsCategory.Launches, "Nox startet Nachtzug für 65 Mio. Fahrgäste"),
                    story("trouble", "Flink", NewsCategory.Trouble, "Flink: €10M loss after hacker attack"),
                    story("hire", "Ecosia", NewsCategory.People, "Ecosia hires a new head of design"),
                    story("other", "Helsing", NewsCategory.Other, "Helsing im Interview"),
                ),
            )
        assertEquals(listOf("again"), picked.map { it.id }, "the newer of two equal reports, no rounds without amount")
    }
}
