package net.productberlin.presentation.designsystem.layouts.news

data class NewsStory(
    val id: String,
    val headline: String,
    val publisher: String,
    val date: String,
    val url: String?,
    val summary: String? = null,
)
