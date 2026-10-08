package net.productberlin.worker.collection

import net.productberlin.domain.entity.NewsArticle

/**
 * Share-price and market-column headlines ("Zalando-Aktie: Kurs gibt nach", "Der Börsen-Tag: …", "DAX-Check …",
 * "HelloFresh shares fall") are published almost daily for listed companies. They say nothing about the company's
 * work, so they neither count towards the ranking nor appear as news. Checked on the original and the translated
 * headline. Deliberately narrow: "Börsengang" (an IPO) and plain mentions of investors stay.
 */
internal object MarketNotes {
    private val patterns =
        listOf(
            // German: any "Aktie(n)" word or compound, trading venues and indices, and the market-column vocabulary.
            Regex("aktie"),
            Regex("""\b(börse|börsen|xetra|tradegate|vorbörslich|nachbörslich|kursziel|kursrutsch|kurssturz|kurssprung)\b"""),
            Regex("""\b(dax|mdax|sdax|tecdax)\b"""),
            // English: share-price moves and analyst targets.
            Regex("""\b(stock|stocks|share price|price target|nasdaq|nyse)\b"""),
            Regex(
                """\bshares? (fall|falls|fell|rise|rises|rose|drop|drops|dropped|jump|jumps|jumped|slump|slumps|slumped|surge|surges|surged|climb|climbs|climbed|slide|slides|slid|tumble|tumbles|tumbled|gain|gains|gained|plunge|plunges|plunged|sink|sinks|sank|soar|soars|soared|unchanged)\b""",
            ),
        )

    fun isMarketNote(article: NewsArticle): Boolean =
        listOfNotNull(article.headline, article.translatedHeadline).any { headline ->
            val text = headline.lowercase()
            patterns.any { it.containsMatchIn(text) }
        }
}
