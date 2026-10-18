package net.productberlin.domain.entity

/**
 * One story of the week: the first article that reported it, the catalogue companies it is about, and the other
 * publishers that covered the same news.
 */
data class WeeklyStory(
    val id: String,
    val companies: List<String>,
    val category: NewsCategory,
    val headline: String,
    val source: String,
    val publishedAt: String,
    val url: String? = null,
    /** English translation of a non-English [headline], for display. */
    val translatedHeadline: String? = null,
    val alsoIn: List<String> = emptyList(),
)
