package net.productberlin.presentation.news

import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.entity.WeeklyStory

/**
 * - **Lead:** the week's biggest funding round; without one, the most-covered story (most outlets), then the newest,
 *   never trouble unless there is nothing else.
 * - **Teasers:** up to four, one per category (growth and exits, people, launches, trouble, funding) and company,
 *   preferring exits, then companies with fewer stories in the period.
 * - **Ticker:** everything else, newest first, one line per company, so a company in the news every day takes one line.
 * - **Catch up:** up to four of the earlier weeks' weightiest stories, skipping companies already featured; hidden
 *   with fewer than two, so a quiet month is not padded with weak news.
 */
internal fun WeeklyNews.toFrontPage(): FrontPage {
    val main = stories.filter { it.category != NewsCategory.Other }
    val strength = compareByDescending<WeeklyStory> { it.alsoIn.size }.thenByDescending { it.publishedAt }
    // Good news leads: trouble only when there is nothing else this week.
    val lead =
        main
            .filter { it.category == NewsCategory.Funding && it.amountMillions() != null }
            .maxByOrNull { it.amountMillions()!! }
            ?: main.filter { it.category != NewsCategory.Trouble }.sortedWith(strength).firstOrNull()
            ?: main.sortedWith(strength).firstOrNull()
    // One teaser per category, preferring companies that are not all over the page already, so the row shows the
    // breadth of the period: exits, people, launches, trouble.
    val storiesPerCompany = stories.flatMap { it.companies }.groupingBy { it }.eachCount()
    // An exit is the biggest growth story there is, so it beats expansion and revenue news whatever the coverage.
    val variety =
        compareBy<WeeklyStory> { it.kicker()?.startsWith("EXIT") != true }
            .thenBy { story -> story.companies.minOf { storiesPerCompany[it] ?: 0 } }
            .then(strength)
    val teasers = mutableListOf<WeeklyStory>()
    for (category in TEASER_ORDER) {
        if (teasers.size == MAX_TEASERS) break
        val used = (listOfNotNull(lead) + teasers).flatMap { it.companies }.toSet()
        main
            .filter { it.category == category && it != lead && it.companies.none { company -> company in used } }
            .sortedWith(variety)
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
    // Older stories worth catching up on, unless their company is already featured above.
    val featured = shown.flatMap { it.companies }.toSet()
    val catchUp =
        earlier
            .filter { story -> story.companies.none { it in featured } }
            .take(MAX_CATCH_UP)
            .takeIf { it.size >= MIN_CATCH_UP }
            .orEmpty()
    return FrontPage(lead, teasers, ticker, catchUp)
}

private const val MAX_TEASERS = 4
private const val MAX_CATCH_UP = 4
private const val MIN_CATCH_UP = 2

/** Exits and growth first, then people, launches, trouble, and a second funding round last. */
private val TEASER_ORDER =
    listOf(NewsCategory.Growth, NewsCategory.People, NewsCategory.Launches, NewsCategory.Trouble, NewsCategory.Funding)
