package net.productberlin.worker.feeds

import net.productberlin.domain.entity.NewsCategory

/** A validated feed item, with the stories the model found in it (null: no verdict, keyword rules apply). */
internal data class FeedItem(
    val key: String,
    val feed: PublisherFeed,
    val title: String,
    val translatedTitle: String?,
    val link: String,
    val publishedAt: String,
    val stories: List<VerdictStory>?,
)

internal data class VerdictStory(
    val companyId: String?,
    val companyName: String,
    val category: NewsCategory,
    val headline: String,
)
