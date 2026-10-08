package net.productberlin.worker.collection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.productberlin.domain.entity.NewsArticle

class MarketNotesTest {
    private fun article(
        headline: String,
        translated: String? = null,
    ) = NewsArticle("id", headline, "WELT", "2026-10-01T12:00:00.000Z", translatedHeadline = translated)

    @Test
    fun sharePriceAndMarketColumnHeadlinesAreMarketNotes() {
        listOf(
            "Zalando-Aktie: Gleichbleibend",
            "Zalando-Aktie: Kurs gibt nach",
            "Abwärtstrend beschleunigt: Hellofresh kappt Gewinnziel - Aktie sackt ab",
            "Der Börsen-Tag: Keine Dividende bei Zalando",
            "DAX-Check LIVE: Allianz, HelloFresh, Infineon im Fokus",
            "Delivery Hero-Aktie vorbörslich bei 36,45 Euro",
            "Goldman Sachs senkt Kursziel für Auto1",
            "HelloFresh shares fall after guidance cut",
            "Delivery Hero stock jumps on Uber offer",
        ).forEach { assertTrue(MarketNotes.isMarketNote(article(it)), it) }
    }

    @Test
    fun translatedMarketNotesAreCaughtToo() {
        assertTrue(MarketNotes.isMarketNote(article("Zalando: Kurs unverändert", translated = "Zalando share price unchanged")))
    }

    @Test
    fun companyNewsStays() {
        listOf(
            "\"Es ist wie tot da drin\": Was bleibt nach dem Zalando-Aus in Erfurt?",
            "Delivery Hero: Östberg bleibt CEO und führt Konzern durch Uber-Übernahme",
            "Langdock hits €50M ARR",
            "Trade Republic plant Börsengang",
            "Klarna opens office in Stockholm",
            "Helsing shares its new drone with investors",
        ).forEach { assertFalse(MarketNotes.isMarketNote(article(it)), it) }
    }
}
