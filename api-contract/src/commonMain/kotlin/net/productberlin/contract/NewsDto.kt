package net.productberlin.contract

import kotlinx.serialization.Serializable

@Serializable
data class NewsDto(
    val id: String,
    val headline: String,
    val source: String,
    val publishedAt: String,
    val url: String? = null,
    val summary: String? = null,
    val translatedHeadline: String? = null,
)
