package net.productberlin.contract

import kotlinx.serialization.Serializable

@Serializable
data class RankingDto(
    val weekLabel: String,
    val startups: List<StartupDto>,
)
