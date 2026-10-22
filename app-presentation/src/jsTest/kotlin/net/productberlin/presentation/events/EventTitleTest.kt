package net.productberlin.presentation.events

import kotlin.test.Test
import kotlin.test.assertEquals

class EventTitleTest {
    @Test
    fun dropsWhatTheSectionAlreadySaysAndDecoration() {
        assertEquals("FrFrFr - Freelance Frühstück Freitag", cleanEventTitle("FrFrFr - Freelance Frühstück Freitag - Berlin"))
        assertEquals("Founders Running Club", cleanEventTitle("Founders Running Club :: Berlin"))
        assertEquals(
            "Lenny’s Newsletter Meetup",
            cleanEventTitle("Lenny’s Newsletter Meetup in Berlin (with free drinks courtesy of PostHog)"),
        )
        assertEquals("Berlin Tech Mixer and Social", cleanEventTitle("Berlin Tech Mixer and Social (Tech / AI / Data / IT) ✨"))
        assertEquals("AI Café - Get Together", cleanEventTitle("AI Café - Get Together Berlin"))
        assertEquals(
            "Rewiring the Material Economy: AI in Commodities, Materials & Construction",
            cleanEventTitle("Rewiring the Material Economy: AI in Commodities, Materials & Construction."),
        )
    }

    @Test
    fun keepsTheStartMiddleAndShortTitles() {
        assertEquals("Handpicked Berlin Coworking @ The Delta", cleanEventTitle("Handpicked Berlin Coworking @ The Delta"))
        assertEquals("Coffee & Code #9", cleanEventTitle("Coffee & Code #9"))
        assertEquals("AI Berlin", cleanEventTitle("AI Berlin"), "too short without it")
        assertEquals("Demo Day (Batch 4)", cleanEventTitle("Demo Day (Batch 4)"), "an aside that says something stays")
    }
}
