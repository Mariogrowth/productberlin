package net.productberlin.presentation.news

import net.productberlin.domain.entity.WeeklyStory

/** The week's news arranged like a front page: one lead story, a row of teasers, and a compact ticker. */
internal data class FrontPage(
    val lead: WeeklyStory?,
    val teasers: List<WeeklyStory>,
    val ticker: List<TickerLine>,
)

/** One company's remaining stories of the week: the newest leads, the rest are "+N more". */
internal data class TickerLine(
    val company: String,
    val newest: WeeklyStory,
    val more: List<WeeklyStory>,
)
