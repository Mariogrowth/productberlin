package net.productberlin.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    private fun days() = container.querySelectorAll(".event-day").let { l -> (0 until l.length).map { l.item(it)!! } }

    @Test
    fun aPlannerWithOneColumnPerBerlinDay() {
        render(EventsSection.create { this.events = this@EventsSectionTest.events })
        assertEquals("Events in Berlin", container.querySelector(".news-band-head h2")?.textContent, "a heading of its own when alone")
        val (wednesday, thursday) = days()
        assertEquals("21", wednesday.querySelector(".event-day-number")?.textContent)
        assertEquals("Wednesday · Oct", wednesday.querySelector(".event-day-name")?.textContent)
        val rows = wednesday.querySelectorAll(".event-row")
        assertEquals("18:00", rows.item(0)?.querySelector("time")?.textContent, "16:00 UTC is 18:00 in Berlin, in time order")
        assertEquals("PyData Berlin · Vinted · Tech & AI", rows.item(0)?.querySelector("p")?.textContent, "tech talks are marked")
        val angels = rows.item(1)!!
        assertEquals("19:00", angels.querySelector("time")?.textContent)
        assertEquals("https://www.meetup.com/x/", angels.querySelector("a")?.getAttribute("href"))
        assertEquals("Startups.Berlin", angels.querySelector("p")?.textContent)
        assertEquals("22", thursday.querySelector(".event-day-number")?.textContent)
        assertEquals("Merantix AI Campus", thursday.querySelector(".event-row p")?.textContent, "venue not repeated")
    }

    @Test
    fun titlesAreCleanedWithTheOriginalOnHover() {
        val event =
            StartupEvent(
                "r:1",
                "Founders Running Club :: Berlin",
                "2026-10-24T08:00:00.000Z",
                null,
                "Founders Running Club Berlin",
                EventKind.Startup,
                url = "https://luma.com/r",
            )
        render(EventsSection.create { this.events = listOf(event) })
        val link = container.querySelector(".event-row a")!!
        assertEquals("Founders Running Club", link.textContent)
        assertEquals("Founders Running Club :: Berlin", link.getAttribute("title"))
    }

    @Test
    fun aBusyDayShowsThreeFoundersEventsFirstAndUnfoldsTheRest() {
        val day =
            (1..4).map {
                StartupEvent(
                    "s:$it",
                    "Founder breakfast $it",
                    "2026-10-23T0${it + 5}:00:00.000Z",
                    null,
                    "Org $it",
                    EventKind.Startup,
                )
            } +
                StartupEvent("t:1", "AI meetup", "2026-10-23T05:00:00.000Z", null, "AI Berlin", EventKind.Tech)
        render(EventsSection.create { this.events = day })
        assertEquals(3, container.querySelectorAll(".event-row").length)
        assertTrue(
            !container.textContent.orEmpty().contains("AI meetup"),
            "the earliest event is a tech talk, but founders' events come first",
        )
        val more = container.querySelector(".event-day .news-ticker-count")!!.unsafeCast<HTMLButtonElement>()
        assertEquals("+2 more", more.textContent)
        flushSync { more.click() }
        assertEquals(5, container.querySelectorAll(".event-row").length)
        assertEquals("AI meetup", container.querySelector(".event-row a, .event-row span")?.textContent, "the whole day is in time order")
        assertEquals("true", more.getAttribute("aria-expanded"))
    }

    @Test
    fun underTheNewsItIsASubsection() {
        render(
            EventsSection.create {
                this.events = this@EventsSectionTest.events
                nested = true
            },
        )
        assertEquals("Events in Berlin", container.querySelector(".news-band-head h3")?.textContent)
        assertNull(container.querySelector("h2"))
    }
}
