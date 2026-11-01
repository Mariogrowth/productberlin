package net.productberlin.presentation.news

import net.productberlin.domain.entity.WeeklyStory

/**
 * The news arranged like a front page: one lead story, a row of teasers, the period's deals for the sidebar, and a
 * short band of earlier stories worth catching up on.
 */
internal data class FrontPage(
    val lead: WeeklyStory?,
    val teasers: List<WeeklyStory>,
    val deals: List<Deal>,
    val catchUp: List<WeeklyStory> = emptyList(),
)

/** One funding round, exit or bid in the sidebar: the company, what happened ("Funding", "Exit", "Bid") and how much. */
internal data class Deal(
    val company: String,
    val kind: String,
    val amount: String?,
    val story: WeeklyStory,
)
