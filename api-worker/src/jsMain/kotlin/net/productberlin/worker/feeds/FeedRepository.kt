package net.productberlin.worker.feeds

internal interface FeedRepository {
    /** Keys of items read since [since] (ISO-8601), so the collector never classifies an item twice. */
    suspend fun knownKeys(since: String): List<String>

    /** Stores new items and their stories in one transaction; items already stored are left unchanged. */
    suspend fun store(
        items: List<FeedItem>,
        classifiedAt: String,
    ): Int
}
