package net.productberlin.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import net.productberlin.domain.entity.DealKind
import net.productberlin.domain.entity.HeadlineFacts
import net.productberlin.domain.entity.MoneyAmount
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.WeeklyStory

class ReadHeadlineFactsTest {
    private val read = ReadHeadlineFacts()

    private fun facts(
        headline: String,
        translated: String? = null,
    ) = read(WeeklyStory("id", listOf("X"), NewsCategory.Other, headline, "Publisher", "2026-10-01T09:00:00.000Z", null, translated))

    @Test
    fun amountsInEuroDollarMillionsAndBillions() {
        assertEquals(MoneyAmount(17.0, "€"), facts("finmid raises €17M to push embedded lending").amount)
        assertEquals(MoneyAmount(100.0, "€"), facts("Noxtua: Über 100 Mio. Euro und C.H.BECK-Mehrheit").amount)
        assertEquals(MoneyAmount(200.0, "$"), facts("X raises \$200 million").amount)
        assertEquals(MoneyAmount(1_200.0, "€"), facts("Y erhält 1,2 Milliarden Euro").amount)
        assertEquals(null, facts("Z closes its seed round").amount)
    }

    @Test
    fun theTranslationIsReadFirst() =
        assertEquals(
            MoneyAmount(14.8 * 1_000, "$"),
            facts("Delivery Hero sagt CEO-Abgang ab", translated = "Delivery Hero cancels CEO exit over Uber’s \$14.8 billion bid").amount,
        )

    @Test
    fun bidsAreNotCompletedExits() {
        assertEquals(DealKind.Exit, facts("Personio acquires Circula").deal)
        assertEquals(DealKind.Exit, facts("Personio übernimmt Berliner Startup Circula").deal)
        assertEquals(DealKind.Bid, facts("Delivery Hero Cancels CEO Exit Plans Due to Uber’s \$14.8 Billion Takeover Bid").deal)
        assertEquals(DealKind.Bid, facts("Uber legt Übernahmeangebot für Delivery Hero vor").deal)
        assertEquals(null, facts("Langdock hits €50M ARR").deal)
    }

    @Test
    fun seniorRoles() {
        assertEquals("CFO", facts("N26 hires former Postbank finance chief as CFO").role)
        assertEquals("CO-CEO", facts("Judith Dada wird Co-Chefin von Langdock").role)
        assertEquals(HeadlineFacts(null, null, null), facts("Ecosia im Interview"))
    }
}
