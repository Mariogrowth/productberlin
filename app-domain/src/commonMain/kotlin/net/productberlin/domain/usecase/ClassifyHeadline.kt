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
            any(NEGATIVE) -> HeadlineEvent.Negative
            any(CONSUMER) -> HeadlineEvent.Consumer
            any(FUNDING) -> HeadlineEvent.Funding
            any(GROWTH) -> HeadlineEvent.Growth
            else -> HeadlineEvent.Other
        }
    }

    private companion object {
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
                        """data breach|steps down|rücktritt|resigns|verlässt)""",
                ),
            )
        val CONSUMER =
            listOf(Regex("""(promo code|gutschein|rabatt|vergleich|gebühren|kosten:|erfahrungen|lohnt sich|dieser broker|free meals)"""))
        val FUNDING =
            listOf(
                Regex(
                    """(raises?\b|raised|funding|series [a-e]\b|\bseed\b|finanzierung|sammelt|sammeln|eingesammelt|investoren|""" +
                        """bewertung|valuation|unicorn|einhorn|backed|kapitalspritze|investiert|invests|investment)""",
                ),
                // "erhält 6 Millionen Euro", "sichert sich 20 Mio.", "secures €10M".
                Regex("""(erhält|sichert sich|secures|lands|bags)[^,:;]*(\d[\d,.]*\s*(mio|millionen|million|mn|m)\b|millionen|million)"""),
            )
        val GROWTH =
            listOf(
                Regex(
                    """(launch|startet|lanciert|rolls out|expands|expansion|expandiert|übernimmt|übernahme|acquires|""" +
                        """acquisition|partnership|partnerschaft|kooperation|customers?\b|kunden|wins\b|gewinnt|award|""" +
                        """auszeichnung|opens|eröffnet|\barr\b|umsatz|revenue|profitab|growth|wachstum|wächst|rekord|""" +
                        """record|milestone|meilenstein|appoints|ernennt|co-chef|co-ceo|neue[rn]? (ceo|chef))""",
                ),
            )
    }
}
