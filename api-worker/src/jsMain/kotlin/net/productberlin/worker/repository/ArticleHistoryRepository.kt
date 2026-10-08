package net.productberlin.worker.repository

import net.productberlin.domain.entity.NewsArticle

/** Earlier weeks' articles, so an edition can score press momentum over four weeks. */
internal interface ArticleHistoryRepository {
    /** The stored weeks among [weekStarts] (Monday dates), keyed by week start. */
    suspend fun recorded(weekStarts: List<String>): Map<String, RecordedWeek>

    /** Stores a week once, in one transaction. False when the week was already stored (nothing changes). */
    suspend fun record(
        weekStart: String,
        articles: List<NewsArticle>,
        reviewed: Int,
        recordedAt: String,
    ): Boolean
}
