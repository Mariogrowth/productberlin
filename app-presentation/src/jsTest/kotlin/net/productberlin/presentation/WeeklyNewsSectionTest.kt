package net.productberlin.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.Startup
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.entity.WeeklyStory
import net.productberlin.presentation.testing.ComponentTest
import react.create

class WeeklyNewsSectionTest : ComponentTest() {
    private fun story(
        id: String,
        company: String,
        category: NewsCategory,
        headline: String,
        date: String = "2026-10-14",
    ) = WeeklyStory(
        id,
        listOf(company),
        category,
        headline,
        "Publisher",
        "${date}T09:00:00.000Z",
        "https://news.example/$id",
        companyId = company.lowercase(),
    )

    private val news =
        WeeklyNews(
            "2026-10-12 – 2026-10-18",
            listOf(
                story("f1", "finmid", NewsCategory.Funding, "finmid raises €17M").copy(alsoIn = listOf("Startbase", "Dealroom")),
                story("p1", "N26", NewsCategory.People, "N26 holt neuen CFO").copy(translatedHeadline = "N26 hires a new CFO"),
                story("h1", "Helsing", NewsCategory.Trouble, "Helsing drone approval delayed"),
            ) + (1..10).map { story("o$it", "Company $it", NewsCategory.Other, "Interview $it", "2026-10-1${it % 8}") },
        )

    private fun render(news: WeeklyNews = this.news) = render(WeeklyNewsSection.create { this.news = news })

    @Test
    fun earlierStoriesWorthCatchingUpOnGetTheirOwnBand() {
        val earlier =
            listOf(
                story("n1", "Noxtua", NewsCategory.Funding, "Noxtua raises €100M Series C", "2026-09-23").copy(alsoIn = listOf("FAZ")),
                story("d1", "Delivery Hero", NewsCategory.Growth, "Uber’s \$14.8 billion takeover bid for Delivery Hero", "2026-09-24"),
            )
        render(news.copy(earlierLabel = "2026-09-14 – 2026-10-04", earlier = earlier))
        val band = container.querySelector(".news-catch-up")!!
        assertEquals("Catch up", band.querySelector("h3")?.textContent)
        assertTrue(band.textContent!!.contains("2026-09-14 – 2026-10-04"))
        val rows = band.querySelectorAll("li")
        assertEquals(2, rows.length)
        assertEquals("FUNDING · €100M", rows.item(0)?.querySelector(".news-kicker")?.textContent)
        assertEquals("https://news.example/n1", rows.item(0)?.querySelector("a")?.getAttribute("href"))
        assertTrue(
            rows
                .item(0)!!
                .querySelector(".news-byline")!!
                .textContent!!
                .contains("Noxtua · Publisher · +1 outlet · Sep 23"),
        )
        assertEquals("DEAL · \$14.8B", rows.item(1)?.querySelector(".news-kicker")?.textContent)
        assertEquals(
            2,
            band.querySelectorAll("li > .catch-up-label > .news-kicker + .company-mark.catch-up-mark").length,
            "each row has its company's mark, below the category",
        )
    }

    @Test
    fun noBandWithoutEarlierStories() {
        render()
        assertNull(container.querySelector(".news-catch-up"))
    }

    @Test
    fun theLeadStoryHasItsKickerHeadlineLinkAndOutlets() {
        render()
        val lead = container.querySelector(".news-lead")!!
        assertEquals("FUNDING · €17M", lead.querySelector(".news-kicker")?.textContent)
        assertEquals("finmid raises €17M", lead.querySelector("h3 a")?.textContent)
        assertEquals("https://news.example/f1", lead.querySelector("h3 a")?.getAttribute("href"))
        assertTrue(lead.querySelector(".news-byline")!!.textContent!!.contains("finmid · Publisher · also 2 more outlets"))
        assertEquals("Oct 14", lead.querySelector("time")?.textContent)
        assertEquals("true", lead.querySelector(".company-mark")?.getAttribute("aria-hidden"))
    }

    @Test
    fun teasersShowTheEnglishHeadlineWithTheOriginalOnHover() {
        render()
        val teasers = container.querySelectorAll(".news-teaser")
        assertEquals(2, teasers.length)
        val people = teasers.item(0)!!
        assertEquals("PEOPLE · CFO", people.querySelector(".news-kicker")?.textContent)
        assertEquals("N26 hires a new CFO", people.querySelector("h3 a")?.textContent)
        assertEquals("Original: N26 holt neuen CFO", people.querySelector("h3 a")?.getAttribute("title"))
        assertEquals("TROUBLE", teasers.item(1)?.querySelector(".news-kicker")?.textContent)
    }

    private fun startup(
        id: String,
        name: String,
        movement: Int?,
    ) = Startup(id, name, "Description", "Fintech", movement, "Reason", emptyList())

    @Test
    fun theSidebarShowsTheRankingsTopFiveWithMovementAndLinksToTheFullList() {
        val ranking =
            listOf(
                startup("finmid", "finmid", null),
                startup("noxtua", "Noxtua", 0),
                startup("mika", "mika", null),
                startup("circula", "Circula", -1),
                startup("langdock", "Langdock", 4),
                startup("nox", "Nox", 1),
            )
        render(
            WeeklyNewsSection.create {
                this.news = this@WeeklyNewsSectionTest.news
                this.ranking = ranking
                linkToRankings = true
            },
        )
        val top = container.querySelector(".news-sidebar .sidebar-top")!!
        assertEquals("Top 5 this week", top.querySelector("h3")?.textContent)
        assertEquals("#rankings", top.querySelector(".news-band-head a")?.getAttribute("href"))
        val rows = top.querySelectorAll("li")
        assertEquals(5, rows.length, "the top five only")
        assertEquals(
            listOf("1 finmid NEW", "2 Noxtua —", "3 mika NEW", "4 Circula ▾ 1", "5 Langdock ▴ 4"),
            (0 until rows.length).map { i ->
                listOf(".rank", ".news-company", ".movement").joinToString(" ") {
                    rows
                        .item(i)!!
                        .querySelector(it)
                        ?.textContent
                        .orEmpty()
                }
            },
        )
        assertEquals(5, top.querySelectorAll("li .company-mark.sidebar-mark").length, "each with its logo or letter mark")
        assertEquals("Up 4 places", rows.item(4)?.querySelector(".movement")?.getAttribute("aria-label"))
    }

    @Test
    fun theSidebarListsDealsWithAmountsAndLeavesOutOtherCoverage() {
        render()
        val deals = container.querySelector(".news-sidebar .sidebar-deals")!!
        val rows = deals.querySelectorAll("li a")
        assertEquals(1, rows.length, "interviews and people news are not deals")
        assertEquals("finmidFunding€17M", rows.item(0)?.textContent)
        assertEquals("https://news.example/f1", rows.item(0)?.getAttribute("href"))
        assertEquals("finmid raises €17M", rows.item(0)?.getAttribute("title"))
        assertNull(container.querySelector(".sidebar-top"), "no ranking, no top five")
        assertTrue(!container.textContent!!.contains("Interview 1"), "other coverage is not shown")
    }

    @Test
    fun aWeekWithoutDealsOrRankingHasNoSidebar() {
        val quiet = WeeklyNews("Week", listOf(story("h1", "Helsing", NewsCategory.Trouble, "Helsing drone approval delayed")))
        render(WeeklyNewsSection.create { this.news = quiet })
        assertNull(container.querySelector(".news-sidebar"))
    }
}
