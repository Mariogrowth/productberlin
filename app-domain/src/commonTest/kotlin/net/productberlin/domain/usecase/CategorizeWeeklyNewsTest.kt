package net.productberlin.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.StartupCandidate

class CategorizeWeeklyNewsTest {
    private val catalogue =
        listOf(
            StartupCandidate("finmid", "finmid", "Embedded finance", "Fintech"),
            StartupCandidate("trade-republic", "Trade Republic", "Broker", "Fintech"),
            StartupCandidate("n26", "N26", "Bank", "Fintech"),
            StartupCandidate("helsing", "Helsing", "Defence AI", "Defence"),
            StartupCandidate("zalando", "Zalando", "Fashion", "Retail"),
        )
    private val categorize = CategorizeWeeklyNews()

    private fun article(
        id: String,
        headline: String,
        source: String,
        date: String,
        translated: String? = null,
    ) = NewsArticle(id, headline, source, "2026-10-${date}T09:00:00.000Z", "https://news.example/$id", translatedHeadline = translated)

    private val week =
        listOf(
            article("f1", "finmid raises 17 million euros", "Startbase", "07"),
            article("f2", "finmid bags €17m as it moves into vehicle financing", "FinTech Global", "08"),
            article("f3", "finmid: 17 Millionen Euro für Fahrzeugkredite", "quasa.io", "09", "finmid: €17 million for vehicle loans"),
            article("t1", "Trade Republic launches a free retirement account", "Handelsblatt", "13"),
            article("t2", "Trade Republic brings current accounts to Greece", "Finance Magnates", "10"),
            article("t3", "Trade Republic Hits €754 Million in Revenue as Profit Jumps Sevenfold", "Trending Topics", "12"),
            article("n1", "N26 Hires Former Postbank Finance Chief Marc Hess as C.F.O.", "Trending Topics", "12"),
            article(
                "n2",
                "N26 holt Ex-Postbank-Finanzchef Marc Heß als CFO",
                "Trending Topics DE",
                "12",
                "N26 hires former Postbank finance chief Marc Hess as CFO",
            ),
            article(
                "h1",
                "Approval of the Helsing HX-2 combat drone appears to have been delayed after Bundeswehr criticism",
                "Handelsblatt",
                "09",
            ),
            article("z1", "Zalando-Aktie: Kurs gibt nach", "WELT", "09"),
            article("t4", "Trade Republic Gebühren 2026 im Vergleich", "WiWo", "11"),
            article("t5", "Trade Republic im Interview: Was der Neobroker plant", "WiWo", "11"),
        )

    @Test
    fun theSameNewsFromSeveralOutletsIsOneStoryLedByTheFirstReport() {
        val finmid = categorize(catalogue, week).single { it.companies == listOf("finmid") }
        assertEquals(NewsCategory.Funding, finmid.category)
        assertEquals("f1", finmid.id)
        assertEquals("Startbase", finmid.source)
        assertEquals(listOf("FinTech Global", "quasa.io"), finmid.alsoIn)
        val n26 = categorize(catalogue, week).single { it.companies == listOf("N26") }
        assertEquals(NewsCategory.People, n26.category)
        assertEquals(listOf("Trending Topics DE"), n26.alsoIn, "the German and English versions are one story")
    }

    @Test
    fun differentNewsAboutOneCompanyStaysSeparate() {
        val tradeRepublic = categorize(catalogue, week).filter { it.companies == listOf("Trade Republic") }
        assertEquals(
            listOf(
                NewsCategory.Launches to "t1",
                NewsCategory.Growth to "t3",
                NewsCategory.Other to "t5",
                NewsCategory.Other to "t2",
            ),
            tradeRepublic.map { it.category to it.id },
        )
    }

    @Test
    fun storiesComeInCategoryOrderNewestFirstWithMarketAndConsumerContentLeftOut() {
        val stories = categorize(catalogue, week)
        assertEquals(
            listOf("f1", "t1", "t3", "n1", "h1", "t5", "t2"),
            stories.map { it.id },
            "Funding, Launches, Growth & partnerships, People, Trouble, Other",
        )
        assertEquals(NewsCategory.Trouble, stories.single { it.id == "h1" }.category)
        assertEquals(emptyList(), stories.filter { it.id == "z1" || it.id == "t4" }, "share-price note and fee comparison")
    }

    @Test
    fun articlesAboutNoCatalogueCompanyAreIgnored() {
        assertEquals(emptyList(), categorize(catalogue, listOf(article("x", "Another startup raises €5M", "EU-Startups", "10"))))
    }
}
