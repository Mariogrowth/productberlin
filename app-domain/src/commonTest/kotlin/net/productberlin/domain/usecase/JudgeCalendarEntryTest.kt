package net.productberlin.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.productberlin.domain.entity.CalendarEntry
import net.productberlin.domain.entity.EventKind
import net.productberlin.domain.entity.EventSource

class JudgeCalendarEntryTest {
    private val judge = JudgeCalendarEntry()
    private val local = EventSource("aicampus", "Merantix AI Campus", EventKind.Tech, berlinBased = true)
    private val global = EventSource("replit", "Replit", EventKind.Tech, berlinBased = false)

    private fun entry(
        title: String,
        location: String = "",
        url: String? = "https://luma.com/abc",
    ) = CalendarEntry("evt-1", title, "2026-10-21T16:00:00.000Z", "2026-10-21T18:00:00.000Z", location, url)

    @Test
    fun berlinOrganisersEventsCountUnlessTheyNameAnotherPlace() {
        val event =
            judge(
                entry("UNCONFERENCE: Engineering AI Together #11", "Merantix AI Campus Berlin, Max-Urich-Straße 3, 13355 Berlin, Germany"),
                local,
            )!!
        assertEquals("aicampus:evt-1", event.id)
        assertEquals("Merantix AI Campus Berlin", event.venue)
        assertEquals(EventKind.Tech, event.kind)
        assertEquals("Merantix AI Campus", event.organiser)
        assertEquals("Merantix AI Campus", judge(entry("Talk", ""), local)?.organiser, "Meetup feeds carry no address")
        assertNull(judge(entry("Talk", "PLUGGED, Shoreditch Exchange, London E2 8JF, UK"), local))
    }

    @Test
    fun internationalOrganisersNeedBerlinEvidence() {
        assertEquals(null, judge(entry("Replit Friday Showcase", "https://luma.com/event/evt-x"), global))
        assertEquals(null, judge(entry("Builders & Brews SF", "Motoring Coffee, 1525 Union St, San Francisco, CA 94123, USA"), global))
        assertEquals(
            "Replit",
            judge(
                entry("Replit × Black Forest Labs Pre-GTC Breakfast", "https://luma.com/event/evt-y", "https://luma.com/replit-gtc-berlin"),
                global,
            )?.organiser,
        )
        assertEquals("deepset GmbH", judge(entry("Hack Night", "deepset GmbH, Zinnowitzer Str. 1, 10115 Berlin, Germany"), global)?.venue)
        assertEquals(null, judge(entry("Open Haus Berlin", "https://luma.com/event/evt-z"), global)?.venue, "a link is not a venue")
    }

    @Test
    fun onlineSessionsAreLeftOut() {
        for (title in listOf("Investor Readiness Session (Online Workshop)", "Replit Office Hours (Discord)", "Webinar: Seed rounds")) {
            assertNull(judge(entry(title), local), title)
        }
    }

    @Test
    fun startupWordsInTheTitleMakeAStartupEvent() {
        for (title in listOf(
            "Angels & Beers - Investors only",
            "Data Science Retreat Demo Day #47",
            "Agentic AI Hack by Jamie",
            "Founders Running Club",
        )) {
            assertEquals(EventKind.Startup, judge(entry(title), local)?.kind, title)
        }
        assertEquals(EventKind.Tech, judge(entry("PyData Berlin 2026 October Meetup"), local)?.kind)
    }
}
