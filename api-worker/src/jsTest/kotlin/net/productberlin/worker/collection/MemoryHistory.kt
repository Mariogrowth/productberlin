package net.productberlin.worker.collection

import net.productberlin.domain.entity.NewsArticle
import net.productberlin.worker.repository.ArticleHistoryRepository
import net.productberlin.worker.repository.RecordedWeek

/** Stored weeks in memory, optionally prefilled as already-recorded quiet weeks. */
internal class MemoryHistory(
    prefilled: Collection<String> = emptyList(),
) : ArticleHistoryRepository {
    val weeks = prefilled.associateWith { RecordedWeek(emptyList(), 0) }.toMutableMap()

    override suspend fun recorded(weekStarts: List<String>) = weeks.filterKeys { it in weekStarts }

    override suspend fun record(
        weekStart: String,
        articles: List<NewsArticle>,
        reviewed: Int,
        recordedAt: String,
    ): Boolean {
        if (weekStart in weeks) return false
        weeks[weekStart] = RecordedWeek(articles, reviewed)
        return true
    }
}
