package net.productberlin.domain.entity

/** The categorised news about catalogue companies for the same week as the published ranking. */
data class WeeklyNews(
    val weekLabel: String,
    val stories: List<WeeklyStory>,
)
