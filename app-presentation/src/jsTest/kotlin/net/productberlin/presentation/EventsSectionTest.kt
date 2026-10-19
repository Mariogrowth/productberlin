package net.productberlin.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.productberlin.domain.entity.EventKind
import net.productberlin.domain.entity.StartupEvent
import net.productberlin.presentation.testing.ComponentTest
import react.create
import react.dom.flushSync
import web.html.HTMLButtonElement

class EventsSectionTest : ComponentTest() {
    private val events =
        listOf(
            StartupEvent(
                "a:1",
                "Angels & Beers",
                "2026-10-21T17:00:00.000Z",
                null,
                "Startups.Berlin",
                EventKind.Startup,
                url = "https://www.meetup.com/x/",
            ),
            StartupEvent(
                "b:1",
                "PyData Berlin October",
                "2026-10-21T16:00:00.000Z",
                null,
                "PyData Berlin",
                EventKind.Tech,
                "Vinted",
                "https://www.meetup.com/y/",
            ),
            StartupEvent(
                "c:1",
                "Demo Day #47",
                "2026-10-22T05:30:00.000Z",
                null,
                "Merantix AI Campus",
                EventKind.Startup,
                "Merantix AI Campus",
                "https://luma.com/z",
            ),
        )

    private fun choose(label: String) {
        val list = container.querySelectorAll(".pb-filter")
        val chip = (0 until list.length).map { list.item(it)!! }.single { it.textContent!!.startsWith(label) }
        flushSync { chip.unsafeCast<HTMLButtonElement>().click() }
    }

    @Test
    fun startupEventsShowFirstGroupedByBerlinDayWithBerlinTimes() {
        render(EventsSection.create { this.events = this@EventsSectionTest.events })
        val chips = container.querySelectorAll(".pb-filter").let { l -> (0 until l.length).map { l.item(it)!!.textContent } }
        assertEquals(listOf("Startups & founders (2)", "Tech & AI (1)", "All (3)"), chips)
        val days = container.querySelectorAll(".berlin-events-day h3").let { l -> (0 until l.length).map { l.item(it)!!.textContent } }
        assertEquals(listOf("Wednesday 21 Oct", "Thursday 22 Oct"), days.map { it!!.replace(",", "") })
        val first = container.querySelector(".berlin-event")!!
        assertEquals("19:00", first.querySelector("time")?.textContent, "17:00 UTC is 19:00 in Berlin")
        assertEquals("https://www.meetup.com/x/", first.querySelector("h4 a")?.getAttribute("href"))
        assertEquals("Startups.Berlin", first.querySelector("p")?.textContent)
        assertTrue(!container.textContent.orEmpty().contains("PyData"), "tech talks are one click away")
        assertEquals("07:30", container.querySelectorAll(".berlin-event time").item(1)?.textContent)
        assertEquals("Merantix AI Campus", container.querySelectorAll(".berlin-event p").item(1)?.textContent, "venue not repeated")
    }

    @Test
    fun theOtherFiltersShowTechTalksOrEverything() {
        render(EventsSection.create { this.events = this@EventsSectionTest.events })
        choose("Tech & AI")
        assertEquals("PyData Berlin · Vinted", container.querySelector(".berlin-event p")?.textContent)
        choose("All")
        assertEquals(3, container.querySelectorAll(".berlin-event").length)
    }
}
