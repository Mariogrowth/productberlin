package net.productberlin.contract

import kotlinx.serialization.Serializable

@Serializable
data class WeeklyStoryDto(
    val id: String,
    val companies: List<String>,
    val category: String,
    val headline: String,
    val source: String,
    val publishedAt: String,
    val url: String? = null,
    val translatedHeadline: String? = null,
    val alsoIn: List<String> = emptyList(),
)
