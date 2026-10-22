package net.productberlin.contract

import kotlinx.serialization.Serializable

/**
 * `GET /api/news/weekly`: categorised stories around the published ranking's week, plus the older stories worth
 * catching up on (`earlier`, absent from older Workers).
 */
@Serializable
data class WeeklyNewsDto(
    val weekLabel: String,
    val stories: List<WeeklyStoryDto>,
    val earlierLabel: String? = null,
    val earlier: List<WeeklyStoryDto> = emptyList(),
)
