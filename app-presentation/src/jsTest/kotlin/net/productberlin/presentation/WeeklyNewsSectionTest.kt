package net.productberlin.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.entity.WeeklyStory
import net.productberlin.presentation.testing.ComponentTest
import react.create
import react.dom.flushSync
import web.html.HTMLButtonElement

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

    private fun render() = render(WeeklyNewsSection.create { this.news = this@WeeklyNewsSectionTest.news })

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

    @Test
    fun theTickerShowsEightLinesThenEverything() {
        render()
        assertEquals("Ticker", container.querySelector(".news-ticker h3")?.textContent)
        assertEquals(8, container.querySelectorAll(".news-ticker-line").length)
        val more = container.querySelector(".news-ticker-more")!!.unsafeCast<HTMLButtonElement>()
        assertEquals("Show all 10", more.textContent)
        flushSync { more.click() }
        assertEquals(10, container.querySelectorAll(".news-ticker-line").length)
        assertEquals("true", more.getAttribute("aria-expanded"))
    }

    @Test
    fun aCompanyWithSeveralStoriesTakesOneTickerLineThatUnfolds() {
        val busy =
            WeeklyNews(
                "Week",
                listOf(
                    story("t1", "Trade Republic", NewsCategory.Other, "Interview", "2026-10-15"),
                    story("t2", "Trade Republic", NewsCategory.Other, "Explainer", "2026-10-16"),
                    story("t3", "Trade Republic", NewsCategory.Other, "Comparison", "2026-10-14"),
                ),
            )
        render(WeeklyNewsSection.create { this.news = busy })
        assertNull(container.querySelector(".news-lead"), "interviews never lead")
        val line = container.querySelector(".news-ticker-line")!!
        assertEquals(1, container.querySelectorAll(".news-ticker-line").length)
        val count = line.querySelector(".news-ticker-count")!!.unsafeCast<HTMLButtonElement>()
        assertEquals("+2 more", count.textContent)
        flushSync { count.click() }
        assertEquals(
            listOf("Interview", "Comparison"),
            line.querySelectorAll("ul li").let { l ->
                (0 until l.length).map { l.item(it)!!.textContent }
            },
        )
    }
}
