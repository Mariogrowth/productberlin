package net.productberlin.presentation.news

import net.productberlin.domain.entity.DealKind
import net.productberlin.domain.entity.MoneyAmount
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.WeeklyStory
import net.productberlin.domain.usecase.ReadHeadlineFacts

/**
 * The short line above a headline that says why it matters, built from data rather than written: the category plus
 * the amount ("FUNDING · €17M"), the deal ("EXIT", "DEAL · $14.8B" for a bid) or the role ("PEOPLE · CFO").
 * Other coverage has no kicker.
 */
internal fun WeeklyStory.kicker(): String? {
    val facts = READ_FACTS(this)
    val amount = facts.amount?.label()
    return when (category) {
        NewsCategory.Funding -> {
            listOfNotNull("FUNDING", amount).joinToString(" · ")
        }

        NewsCategory.Launches -> {
            "LAUNCH"
        }

        NewsCategory.Growth -> {
            when (facts.deal) {
                DealKind.Exit -> listOfNotNull("EXIT", amount).joinToString(" · ")
                DealKind.Bid -> listOfNotNull("DEAL", amount).joinToString(" · ")
                null -> "GROWTH"
            }
        }

        NewsCategory.People -> {
            listOfNotNull("PEOPLE", facts.role).joinToString(" · ")
        }

        NewsCategory.Trouble -> {
            "TROUBLE"
        }

        NewsCategory.Other -> {
            null
        }
    }
}

/**
 * The story as a sidebar deal: a funding round, an exit or a bid, under the first company that is not named as the
 * investor; null for any other news, or when every company is the backer ("Zalando backs Sereact", Sereact not
 * catalogued).
 */
internal fun WeeklyStory.deal(): Deal? {
    val facts = READ_FACTS(this)
    val company = companies.firstOrNull { it !in facts.investors } ?: return null
    val kind =
        when {
            category == NewsCategory.Funding -> "Funding"
            category == NewsCategory.Growth && facts.deal == DealKind.Exit -> "Exit"
            category == NewsCategory.Growth && facts.deal == DealKind.Bid -> "Bid"
            else -> return null
        }
    return Deal(company, kind, facts.amount?.label(), this)
}

/** The amount in millions of euros or dollars, for comparing rounds; null when the headline names none. */
internal fun WeeklyStory.amountMillions(): Double? = READ_FACTS(this).amount?.millions

/** "€17M", "$200M", "€1.2B". */
internal fun MoneyAmount.label(): String {
    val billions = millions >= 1_000
    val value = if (billions) millions / 1_000 else millions
    val shown = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    return "$currency$shown${if (billions) "B" else "M"}"
}

private val READ_FACTS = ReadHeadlineFacts()
