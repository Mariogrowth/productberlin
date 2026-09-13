package net.productberlin.data.dto

import kotlinx.serialization.Serializable
import net.productberlin.domain.entity.NewsArticle

@Serializable
internal data class NewsDto(
    val id: String,
    val headline: String,
    val source: String,
    val publishedAt: String,
) {
    fun toDomain() = NewsArticle(id, headline, source, publishedAt)
}
