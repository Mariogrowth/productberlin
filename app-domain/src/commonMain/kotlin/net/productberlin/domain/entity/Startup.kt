package net.productberlin.domain.entity

/** A ranked company, independent of transport and presentation frameworks. */
data class Startup(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val movement: Int?,
    val reason: String,
    val news: List<NewsArticle>,
)
