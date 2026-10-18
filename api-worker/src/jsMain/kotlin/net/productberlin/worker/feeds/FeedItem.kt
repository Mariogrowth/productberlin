package net.productberlin.worker.feeds

/** A validated item from a publisher feed. */
internal data class FeedItem(
    val key: String,
    val feed: PublisherFeed,
    val title: String,
    val translatedTitle: String?,
    val link: String,
    val publishedAt: String,
)
