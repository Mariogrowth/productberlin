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
        category: NewsCategory,
        company: String,
        headline: String,
        date: String = "2026-10-07",
    ) = WeeklyStory(id, listOf(company), category, headline, "Publisher", "${date}T09:00:00.000Z", "https://news.example/$id")

    private val news =
        WeeklyNews(
            "2026-10-05 – 2026-10-11",
            listOf(
                story("f1", NewsCategory.Funding, "finmid", "finmid raises 17 million euros")
                    .copy(alsoIn = listOf("FinTech Global", "quasa.io")),
                story("t1", NewsCategory.Launches, "Trade Republic", "Trade Republic startet Altersvorsorgedepot", "2026-10-09")
                    .copy(translatedHeadline = "Trade Republic launches a retirement account"),
                story("n1", NewsCategory.People, "N26", "N26 hires a new CFO", "2026-10-08"),
                story("h1", NewsCategory.Trouble, "Helsing", "Approval of Helsing's drone appears delayed"),
                story("o1", NewsCategory.Other, "Langdock", "Langdock founders interviewed"),
            ),
        )

    private fun chips() = container.querySelectorAll(".pb-filter").let { list -> (0 until list.length).map { list.item(it)!!.textContent } }

    private fun choose(label: String) {
        val list = container.querySelectorAll(".pb-filter")
        val chip = (0 until list.length).map { list.item(it)!! }.single { it.textContent!!.startsWith(label) }
        flushSync { chip.unsafeCast<HTMLButtonElement>().click() }
    }

    @Test
    fun allShowsEveryCategoryButOtherUnderItsHeadingIncludingTrouble() {
        render(WeeklyNewsSection.create { this.news = this@WeeklyNewsSectionTest.news })
        assertEquals(
            listOf("All (4)", "Funding (1)", "Launches (1)", "People (1)", "Trouble (1)", "Other (1)"),
            chips(),
            "no chip for empty categories",
        )
        val headings = container.querySelectorAll(".weekly-news-group h3")
        assertEquals(listOf("Funding", "Launches", "People", "Trouble"), (0 until headings.length).map { headings.item(it)!!.textContent })
        assertTrue(!container.textContent.orEmpty().contains("Langdock founders interviewed"), "Other is hidden from All")
        assertEquals("2026-10-05 – 2026-10-11", container.querySelector(".weekly-news .ranking-meta span")?.textContent)
    }

    @Test
    fun aStoryShowsCompanyDateEnglishHeadlineLinkAndOtherOutlets() {
        render(WeeklyNewsSection.create { this.news = this@WeeklyNewsSectionTest.news })
        val stories = container.querySelectorAll(".weekly-story")
        val finmid = stories.item(0)!!
        assertEquals("finmid", finmid.querySelector(".weekly-story-company")?.textContent)
        assertEquals("Oct 7", finmid.querySelector("time")?.textContent)
        assertEquals("https://news.example/f1", finmid.querySelector("h4 a")?.getAttribute("href"))
        assertEquals("Publisher · also FinTech Global, quasa.io", finmid.querySelector(".weekly-story-source")?.textContent)
        val launch = stories.item(1)!!
        assertEquals("Trade Republic launches a retirement account", launch.querySelector("h4")?.textContent)
        assertEquals("Original: Trade Republic startet Altersvorsorgedepot", launch.querySelector("h4")?.getAttribute("title"))
    }

    @Test
    fun choosingACategoryShowsOnlyItsStoriesAndOtherIsReachable() {
        render(WeeklyNewsSection.create { this.news = this@WeeklyNewsSectionTest.news })
        choose("Trouble")
        assertEquals(1, container.querySelectorAll(".weekly-story").length)
        assertTrue(container.textContent.orEmpty().contains("Approval of Helsing's drone appears delayed"))
        assertNull(container.querySelector(".weekly-news-group h3"), "a single category needs no heading")
        assertEquals("Trouble (1)", container.querySelector(".pb-filter[aria-pressed='true']")?.textContent)
        choose("Other")
        assertTrue(container.textContent.orEmpty().contains("Langdock founders interviewed"))
        choose("All")
        assertEquals(4, container.querySelectorAll(".weekly-story").length)
    }

    @Test
    fun aWeekWithOnlyInterviewsSaysSoUnderAll() {
        render(
            WeeklyNewsSection.create { this.news = WeeklyNews("Week", listOf(story("o1", NewsCategory.Other, "Langdock", "Interview"))) },
        )
        assertTrue(container.textContent.orEmpty().contains("No company news this week beyond interviews and features."))
        assertEquals(listOf("All (0)", "Other (1)"), chips())
    }

    @Test
    fun eachStoryShowsTheCompanysLogoAndFallsBackToItsLetterMark() {
        val logo = "https://cdn.brandfetch.io/domain/finmid.com/w/80/h/80/fallback/404/type/icon?c=id"
        val withLogo = story("f1", NewsCategory.Funding, "finmid", "finmid raises €17M").copy(companyId = "finmid", logoUrl = logo)
        val withoutLogo = story("n1", NewsCategory.People, "N26", "N26 hires a CFO").copy(companyId = "n26")
        render(WeeklyNewsSection.create { this.news = WeeklyNews("Week", listOf(withLogo, withoutLogo)) })
        val marks = container.querySelectorAll(".weekly-story .company-mark.story-mark")
        assertEquals(2, marks.length)
        val first = marks.item(0)!!
        assertEquals(logo, first.querySelector("img")?.getAttribute("src"))
        assertEquals("", first.querySelector("img")?.getAttribute("alt"))
        assertEquals("true", first.getAttribute("aria-hidden"), "decorative: the company name follows")
        flushSync { first.querySelector("img")!!.dispatchEvent(web.events.Event(web.events.EventType("error"))) }
        assertEquals(
            "fi",
            container.querySelectorAll(".weekly-story .company-mark").item(0)?.textContent,
            "letter mark after a failed logo",
        )
        assertEquals("N26", marks.item(1)?.textContent)
    }

    @Test
    fun aStartupOutsideTheCatalogueIsMarkedNewWithItsLetterMark() {
        val procuros = story("p1", NewsCategory.Funding, "Procuros", "Berlin logistics startup Procuros raises €20M").copy(isNew = true)
        render(WeeklyNewsSection.create { this.news = WeeklyNews("Week", listOf(procuros)) })
        assertEquals("ProcurosNEW", container.querySelector(".weekly-story-company")?.textContent)
        assertEquals("Not in the Product.berlin catalogue yet", container.querySelector(".weekly-story-new")?.getAttribute("title"))
        assertEquals("Pr", container.querySelector(".weekly-story .company-mark")?.textContent)
    }
}
