package net.productberlin.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import net.productberlin.domain.entity.HeadlineEvent
import net.productberlin.domain.entity.NewsArticle

class ClassifyHeadlineTest {
    private val classify = ClassifyHeadline()

    private fun event(
        headline: String,
        translated: String? = null,
    ) = classify(NewsArticle("id", headline, "Publisher", "2026-10-01T12:00:00.000Z", translatedHeadline = translated))

    private fun assertAll(
        expected: HeadlineEvent,
        vararg headlines: String,
    ) = headlines.forEach { assertEquals(expected, event(it), it) }

    @Test
    fun fundingRoundsCountMost() =
        assertAll(
            HeadlineEvent.Funding,
            "Noxtua raises €100M Series C as C.H.BECK takes majority stake in legal AI",
            "Noxtua erhält mehr als 100 Millionen Euro in Series C",
            "Berlin-based mika raises €6 million to scale its AI-native alternative to traditional tax firms",
            "Langdock sammelt 20 Millionen Euro ein",
            "mika erhält 6 Millionen Euro für KI-native Buchhaltung",
            "Pliant sichert sich 40 Mio. Euro",
            "Nox secures €10M for night trains",
            "finmid: €17 million for vehicle and dealer loans",
            "finmid: 17 Millionen Euro für Fahrzeugkredite",
        )

    @Test
    fun newProductsAndServicesAreLaunches() =
        assertAll(
            HeadlineEvent.Launch,
            "Newcomer Nox aus Berlin startet Nachtzug Hamburg–München: Tickets ab 65 Euro",
            "Trade Republic launches a free retirement account",
            "Langdock unveils agents for regulated industries",
        )

    @Test
    fun tractionExpansionAndPartnershipsAreGrowth() =
        assertAll(
            HeadlineEvent.Growth,
            "Langdock hits €50M ARR (≈\$55M)",
            "Unbemannter Kampfjet: Helsing-KI gewinnt Luftkämpfe gegen Menschen",
            "Pliant expands to the UK",
            "Trade Republic Hits €754 Million in Revenue as Profit Jumps Sevenfold",
            "Parloa partners with Swiss insurer",
            "Delivery Hero übernimmt Lieferdienst in Polen",
        )

    @Test
    fun seniorHiresAreAboutPeople() =
        assertAll(
            HeadlineEvent.People,
            "N26 Hires Former Postbank Finance Chief Marc Hess as C.F.O.",
            "N26 holt Ex-Postbank-Finanzchef Marc Heß als CFO",
            "Judith Dada wird Co-Chefin des KI-Start-ups Langdock in Berlin",
            "Taktile appoints new CTO",
        )

    @Test
    fun ordinaryCoverageCountsOnce() =
        assertAll(
            HeadlineEvent.Other,
            "Künstliche Intelligenz: Warum Langdock seinen Rechtssitz nach Deutschland verlegt",
            "Digitalminister besucht das Drohnen- und KI-Unternehmen Helsing",
            "Trade Republic: Bald auch mit Altersvorsorgedepot",
            "Helsing erhält Besuch vom Digitalminister 2026",
        )

    @Test
    fun sharePriceNotesAndMarketColumnsNeverCount() =
        assertAll(
            HeadlineEvent.Market,
            "Zalando-Aktie: Gleichbleibend",
            "Zalando-Aktie: Kurs gibt nach",
            "Der Börsen-Tag: Keine Dividende bei Zalando",
            "DAX-Check LIVE: Allianz, HelloFresh, Infineon im Fokus",
            "Delivery Hero-Aktie vorbörslich bei 36,45 Euro",
            "Goldman Sachs senkt Kursziel für Auto1",
            "HelloFresh shares fall after guidance cut",
            "Delivery Hero stock jumps on Uber offer",
            // Market wording wins over growth or funding wording.
            "Abwärtstrend beschleunigt: Hellofresh kappt Gewinnziel - Aktie sackt ab",
        )

    @Test
    fun troubleIsReportedButNeverAReasonToRank() =
        assertAll(
            HeadlineEvent.Negative,
            "\"Es ist wie tot da drin\": Was bleibt nach dem Zalando-Aus in Erfurt?",
            "Zalando-Logistikzentrum Erfurt: Millionen-Sozialplan unterschrieben",
            "Berlin: Kunden werden nach Hackerangriff auf Lieferdienst Flink erpresst",
            "\"Müssen sich Betroffene nicht bieten lassen\": Neuer Zins-Ärger bei Trade Republic",
            "Startup cuts jobs after funding dries up",
            "German Armed Forces: Approval of the Helsing HX-2 combat drone appears to have been delayed",
            "Bundeswehr: Zulassung der Helsing-Drohne verzögert sich",
        )

    @Test
    fun promoCodesAndComparisonsAreConsumerContent() =
        assertAll(
            HeadlineEvent.Consumer,
            "HelloFresh Promo Codes: 55% Off for October 2026",
            "Trade Republic Gebühren 2026 im Vergleich",
            "Trotz Zinserhöhung bei Trade Republic – dieser Broker bietet mehr",
        )

    @Test
    fun theTranslationIsCheckedToo() {
        assertEquals(HeadlineEvent.Market, event("Zalando: Kurs unverändert", translated = "Zalando share price unchanged"))
        assertEquals(HeadlineEvent.Funding, event("Noxtua: Über 100 Mio. Euro für Rechts-KI", translated = "Noxtua raises over €100M"))
    }

    @Test
    fun companyNewsWithMarketLookalikesStaysCounted() =
        listOf(
            "Trade Republic plant Börsengang" to HeadlineEvent.Other,
            "Klarna opens office in Stockholm" to HeadlineEvent.Growth,
        ).forEach { (headline, expected) -> assertEquals(expected, event(headline), headline) }

    @Test
    fun weightsFollowTheEvent() =
        assertEquals(
            listOf(3, 2, 2, 2, 1, 0, 0, 0),
            listOf(
                HeadlineEvent.Funding,
                HeadlineEvent.Launch,
                HeadlineEvent.Growth,
                HeadlineEvent.People,
                HeadlineEvent.Other,
                HeadlineEvent.Negative,
                HeadlineEvent.Market,
                HeadlineEvent.Consumer,
            ).map { it.weight },
        )
}
