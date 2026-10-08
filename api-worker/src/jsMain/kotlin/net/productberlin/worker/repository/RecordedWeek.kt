package net.productberlin.worker.repository

import net.productberlin.domain.entity.NewsArticle

/** A stored week: its trusted articles naming catalogue companies, and how many trusted articles were reviewed. */
internal data class RecordedWeek(
    val articles: List<NewsArticle>,
    val reviewed: Int,
)
