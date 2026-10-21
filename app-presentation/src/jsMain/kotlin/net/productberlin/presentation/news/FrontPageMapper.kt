package net.productberlin.presentation.news

import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.entity.WeeklyStory

/**
 * - **Lead:** the week's biggest funding round; without one, the most-covered story (most outlets), then the newest.
 * - **Teasers:** up to three more of the strongest stories, one per category and company, in category order.
 * - **Ticker:** everything else, newest first, one line per company, so a company in the news every day takes one line.
 */
internal fun WeeklyNews.toFrontPage(): FrontPage {
    val main = stories.filter { it.category != NewsCategory.Other }
    val strength = compareByDescending<WeeklyStory> { it.alsoIn.size }.thenByDescending { it.publishedAt }
    val lead =
        main
            .filter { it.category == NewsCategory.Funding && it.amount() != null }
            .maxByOrNull { it.amount()!!.euros }
            ?: main.sortedWith(strength).firstOrNull()
    val teasers = mutableListOf<WeeklyStory>()
    for (category in NewsCategory.entries.filter { it != NewsCategory.Other }) {
        if (teasers.size == MAX_TEASERS) break
        val used = (listOfNotNull(lead) + teasers).flatMap { it.companies }.toSet()
        main
            .filter { it.category == category && it != lead && it.companies.none { company -> company in used } }
            .sortedWith(strength)
            .firstOrNull()
            ?.let { teasers += it }
    }
    val shown = (listOfNotNull(lead) + teasers).toSet()
    val ticker =
        stories
            .filter { it !in shown }
            .sortedWith(compareByDescending<WeeklyStory> { it.publishedAt }.thenBy { it.id })
            .groupBy { it.companies.joinToString(" · ") }
            .map { (company, list) -> TickerLine(company, list.first(), list.drop(1)) }
    return FrontPage(lead, teasers, ticker)
}

private const val MAX_TEASERS = 3
