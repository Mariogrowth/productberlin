package net.productberlin.domain.entity

/**
 * The categorised news about catalogue companies around the published ranking's week, plus the weightier older
 * stories worth catching up on ([earlier], from the weeks before [weekLabel], labelled [earlierLabel]).
 */
data class WeeklyNews(
    val weekLabel: String,
    val stories: List<WeeklyStory>,
    val earlierLabel: String? = null,
    val earlier: List<WeeklyStory> = emptyList(),
)
