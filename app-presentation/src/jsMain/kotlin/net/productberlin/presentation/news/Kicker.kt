package net.productberlin.presentation.news

import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.WeeklyStory

/**
 * The short line above a headline that says why it matters, built from data rather than written: the category plus
 * the amount ("FUNDING · €17M"), the deal ("EXIT") or the role ("PEOPLE · CFO"). Other coverage has no kicker.
 */
internal fun WeeklyStory.kicker(): String? {
    val text = (translatedHeadline ?: headline)
    val lower = text.lowercase()
    return when (category) {
        NewsCategory.Funding -> {
            listOfNotNull("FUNDING", amount()?.label).joinToString(" · ")
        }

        NewsCategory.Launches -> {
            "LAUNCH"
        }

        NewsCategory.Growth -> {
            if (EXIT.containsMatchIn(lower)) "EXIT" else "GROWTH"
        }

        NewsCategory.People -> {
            listOfNotNull(
                "PEOPLE",
                ROLE
                    .find(lower)
                    ?.value
                    ?.uppercase()
                    ?.let { if (it.startsWith("CO-CHEF")) "CO-CEO" else it },
            ).joinToString(" · ")
        }

        NewsCategory.Trouble -> {
            "TROUBLE"
        }

        NewsCategory.Other -> {
            null
        }
    }
}

/** An amount named in the headline, e.g. "€17 million", "17 Mio. Euro", "$200M", "€1.2 billion". */
internal data class Amount(
    val euros: Double,
    val label: String,
)

internal fun WeeklyStory.amount(): Amount? {
    for (text in listOfNotNull(translatedHeadline, headline)) {
        val lower = text.lowercase()
        SYMBOL_FIRST.find(lower)?.let { m -> return amount(m.groupValues[1], m.groupValues[2], m.groupValues[3]) }
        NUMBER_FIRST.find(lower)?.let { m -> return amount(m.groupValues[3].ifEmpty { "€" }, m.groupValues[1], m.groupValues[2]) }
    }
    return null
}

private fun amount(
    currency: String,
    number: String,
    unit: String,
): Amount? {
    val value = number.replace(',', '.').toDoubleOrNull() ?: return null
    val billions = unit.startsWith("b") || unit.startsWith("mrd") || unit.startsWith("milliard")
    val symbol = if (currency.contains("$") || currency.contains("dollar")) "$" else "€"
    val shown = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    return Amount(value * if (billions) 1_000.0 else 1.0, "$symbol$shown${if (billions) "B" else "M"}")
}

private val SYMBOL_FIRST = Regex("""([€$])\s?(\d+(?:[.,]\d+)?)\s?(m|mn|million|millionen|bn|b|billion)\b""")
private val NUMBER_FIRST =
    Regex("""(\d+(?:[.,]\d+)?)\s*(mio\.?|millionen|million|mrd\.?|milliarden|billion)\s*(euro|€|dollar|\$)?""")
private val EXIT = Regex("""(acquires|acquisition|acquired|übernimmt|übernahme|kauft|buys|sold to|verkauft an|exit)""")
private val ROLE = Regex("""\b(co-ceo|co-chef\w*|ceo|cfo|cto|coo|cpo)\b""")
