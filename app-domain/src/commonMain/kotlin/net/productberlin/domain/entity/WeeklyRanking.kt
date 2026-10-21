package net.productberlin.domain.entity

data class WeeklyRanking(
    val weekLabel: String,
    val startups: List<Startup>,
    val isMock: Boolean = true,
    val updatedAt: String? = null,
    val searchQuery: String? = null,
    val articleCount: Int? = null,
    /** Scaleups ranked by the same press momentum, shown separately so startups get the main list. */
    val scaleups: List<Startup> = emptyList(),
)
