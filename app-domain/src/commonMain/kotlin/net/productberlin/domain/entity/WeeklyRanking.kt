package net.productberlin.domain.entity

data class WeeklyRanking(
    val weekLabel: String,
    val startups: List<Startup>,
)
