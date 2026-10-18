package net.productberlin.contract

import kotlinx.serialization.Serializable

/** `GET /api/news/weekly`: categorised stories for the same week as the published ranking. */
@Serializable
data class WeeklyNewsDto(
    val weekLabel: String,
    val stories: List<WeeklyStoryDto>,
)
