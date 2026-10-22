package net.productberlin.domain.usecase

import net.productberlin.domain.entity.HeadlineEvent
import net.productberlin.domain.entity.NewsArticle

/**
 * Keyword rules, in English and German, for what a headline reports. Both the original and the English translation
 * are checked. Anything that makes a headline not count (market, trouble, consumer content) wins over funding or
 * growth wording, so "Aktie stürzt nach Finanzierungsrunde ab" counts nothing. Deliberately simple and
 * deterministic: a headline that matches nothing counts as ordinary coverage.
 */
class ClassifyHeadline {
    operator fun invoke(article: NewsArticle): HeadlineEvent {
        val texts = listOfNotNull(article.headline, article.translatedHeadline).map { it.lowercase() }

        fun any(patterns: List<Regex>) = texts.any { text -> patterns.any { it.containsMatchIn(text) } }
        return when {
            any(MARKET) -> HeadlineEvent.Market

            // Opinion columns and commentary mention companies in passing: ordinary coverage, never a category story.
            any(OPINION) -> HeadlineEvent.Other

            any(NEGATIVE) -> HeadlineEvent.Negative

            any(CONSUMER) -> HeadlineEvent.Consumer

            any(FUNDING) -> HeadlineEvent.Funding

            any(LAUNCH) -> HeadlineEvent.Launch

            any(PEOPLE) -> HeadlineEvent.People

            any(GROWTH) -> HeadlineEvent.Growth

            else -> HeadlineEvent.Other
        }
    }

    private companion object {
        val OPINION = listOf(Regex("""^\s*(meinung|opinion|kommentar|kolumne|op-ed)\s*:"""))
        val MARKET =
            listOf(
                // Any "Aktie(n)" word or compound, trading venues and indices, and market-column vocabulary.
                Regex("aktie"),
                Regex("""\b(börse|börsen|xetra|tradegate|vorbörslich|nachbörslich|kursziel|kursrutsch|kurssturz|kurssprung)\b"""),
                Regex("""\b(dax|mdax|sdax|tecdax)\b"""),
                Regex("""\b(stock|stocks|share price|price target|nasdaq|nyse)\b"""),
                Regex(
                    """\bshares? (fall|falls|fell|rise|rises|rose|drop|drops|dropped|jump|jumps|jumped|slump|slumps|slumped|surge|""" +
                        """surges|surged|climb|climbs|climbed|slide|slides|slid|tumble|tumbles|tumbled|gain|gains|gained|plunge|""" +
                        """plunges|plunged|sink|sinks|sank|soar|soars|soared|unchanged)\b""",
                ),
            )
        val NEGATIVE =
            listOf(
                Regex(
                    """(layoff|lay off|lays off|job cuts|cuts? jobs|entlass|stellenabbau|streicht .*stellen|insolven|bankrupt|""" +
                        """pleite|schließt|schliesst|schließung|closes|closure|shuts? down|-aus\b|\baus für\b|sozialplan|""" +
                        """lawsuit|klage|verklagt|\bsued\b|fraud|betrug|razzia|bußgeld|ermittl|scandal|skandal|verlust|""" +
                        """\blosses\b|kritik|criticism|ärger|rückruf|recall|hacker|hacked|cyberangriff|erpress|datenleck|""" +
                        """data breach|steps down|rücktritt|resigns|verlässt|delayed|delays|verzögert|verzögerung|streit|dispute|dismissals)""",
                ),
            )
        val CONSUMER =
            listOf(Regex("""(promo code|gutschein|rabatt|vergleich|gebühren|kosten:|erfahrungen|lohnt sich|dieser broker|free meals)"""))
        val FUNDING =
            listOf(
                Regex(
                    // Only words that mean raising money: "investment", "investors" or "backed" alone also appear in product
                    // news ("free investment accounts") and disputes ("dispute with investors").
                    """(\braises?\b|\braised\b|\bfunding\b|series [a-e]\b|\bseed\b|seed-runde|seedrunde|finanzierungsrunde|""" +
                        """\bfinanzierung\b|sammel\w* [^,:;]*(\bein\b|millionen|mio\b)|eingesammelt|kapitalspritze|investment round|""" +
                        """investitionsrunde|valuation|milliarden-bewertung|bewertung von|unicorn|einhorn)""",
                ),
                // "17 Millionen Euro für Fahrzeugkredite", "€17 million for vehicle loans".
                // "17 Mio. Euro für": the dot after "Mio" ends the word, so the unit carries its own boundary.
                Regex("""\d[\d,.]*\s*(mio\.|mio\b|millionen\b|million\b|mn\b|m\b)\s*(euro|dollar)?\s*(für|for)\b"""),
                // "erhält 6 Millionen Euro", "sichert sich 20 Mio.", "holt sich 17 Millionen", "secures €10M".
                Regex(
                    """(erhält|sichert sich|\bholt\b|secures|lands|bags)[^,:;]*(\d[\d,.]*\s*(mio|millionen|million|mn|m)\b|millionen|million)""",
                ),
            )
        val LAUNCH =
            listOf(Regex("""(launch|startet|lanciert|rolls out|unveils|introduces|präsentiert|bringt [^,:;]*(auf den markt|heraus))"""))
        val PEOPLE =
            listOf(
                Regex(
                    """(appoints|ernennt|co-chef|co-ceo|neue[rn]? (ceo|chef|cfo|cto)|\bhires\b|\bholt\b[^,:;]* als|""" +
                        """\bjoins as\b|\bnamed (ceo|cfo|cto)\b|\bas (ceo|cfo|cto|coo)\b)""",
                ),
            )
        val GROWTH =
            listOf(
                Regex(
                    """(expands|expansion|expandiert|übernimmt|übernahme|acquires|acquisition|takeover|partnership|partnerschaft|""" +
                        """partners with|kooperation|customers?\b|kunden|wins\b|gewinnt|award|auszeichnung|opens|eröffnet|""" +
                        """sold to|verkauft an|\bkauft\b|\bbuys\b|investiert [^,:;]* in\b|invests [^,:;]* in\b|""" +
                        """\barr\b|umsatz|revenue|profitab|growth|wachstum|wächst|rekord|record|milestone|meilenstein|deal with|signs)""",
                ),
            )
    }
}
