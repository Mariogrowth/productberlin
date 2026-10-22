package net.productberlin.domain.usecase

import net.productberlin.domain.entity.DealKind
import net.productberlin.domain.entity.HeadlineFacts
import net.productberlin.domain.entity.MoneyAmount
import net.productberlin.domain.entity.WeeklyStory

/**
 * Reads the amount, deal and senior role from a story's English translation first, then its original headline, in
 * English and German. Deterministic keyword rules, like [ClassifyHeadline].
 */
class ReadHeadlineFacts {
    operator fun invoke(story: WeeklyStory): HeadlineFacts {
        val texts = listOfNotNull(story.translatedHeadline, story.headline).map { it.lowercase() }
        return HeadlineFacts(
            amount = texts.firstNotNullOfOrNull { amount(it) },
            deal =
                when {
                    texts.any { BID.containsMatchIn(it) } -> DealKind.Bid
                    texts.any { EXIT.containsMatchIn(it) } -> DealKind.Exit
                    else -> null
                },
            role =
                texts.firstNotNullOfOrNull { ROLE.find(it)?.value }?.uppercase()?.let {
                    if (it.startsWith("CO-CHEF")) "CO-CEO" else it
                },
        )
    }

    private fun amount(text: String): MoneyAmount? {
        SYMBOL_FIRST.find(text)?.let { m -> return amount(m.groupValues[1], m.groupValues[2], m.groupValues[3]) }
        NUMBER_FIRST.find(text)?.let { m -> return amount(m.groupValues[3].ifEmpty { "€" }, m.groupValues[1], m.groupValues[2]) }
        return null
    }

    private fun amount(
        currency: String,
        number: String,
        unit: String,
    ): MoneyAmount? {
        val value = number.replace(',', '.').toDoubleOrNull() ?: return null
        val billions = unit.startsWith("b") || unit.startsWith("mrd") || unit.startsWith("milliard")
        val symbol = if (currency.contains("$") || currency.contains("dollar")) "$" else "€"
        return MoneyAmount(value * if (billions) 1_000.0 else 1.0, symbol)
    }

    private companion object {
        val SYMBOL_FIRST = Regex("""([€$])\s?(\d+(?:[.,]\d+)?)\s?(m|mn|million|millionen|bn|b|billion)\b""")
        val NUMBER_FIRST =
            Regex("""(\d+(?:[.,]\d+)?)\s*(mio\.?|millionen|million|mrd\.?|milliarden|billion)\s*(euro|€|dollar|\$)?""")

        // Checked first: "takeover bid" also contains "takeover".
        val BID = Regex("""(takeover bid|takeover offer|bid for|in talks to (buy|acquire)|übernahmeangebot|übernahmegespräche)""")
        val EXIT = Regex("""(acquires|acquisition|acquired|takeover|übernimmt|übernahme|kauft|buys|sold to|verkauft an|exit)""")
        val ROLE = Regex("""\b(co-ceo|co-chef\w*|ceo|cfo|cto|coo|cpo)\b""")
    }
}
