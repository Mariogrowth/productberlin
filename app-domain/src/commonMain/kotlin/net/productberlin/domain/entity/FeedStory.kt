package net.productberlin.domain.entity

/**
 * A story about one Berlin startup from a startup publisher's feed, as judged by the classifier model and validated
 * by the Worker. [companyId] is the catalogue ID, or null for a startup not (yet) in the catalogue.
 */
data class FeedStory(
    val id: String,
    val companyId: String?,
    val companyName: String,
    val category: NewsCategory,
    /** English headline. */
    val headline: String,
    /** The publisher's own headline when it differs (another language, or several startups in one post). */
    val originalHeadline: String?,
    val source: String,
    val url: String?,
    val publishedAt: String,
)
