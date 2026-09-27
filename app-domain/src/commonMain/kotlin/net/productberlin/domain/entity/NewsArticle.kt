package net.productberlin.domain.entity

data class NewsArticle(
    val id: String,
    val headline: String,
    val source: String,
    val publishedAt: String,
    val url: String? = null,
    val summary: String? = null,
)
