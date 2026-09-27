package net.productberlin.contract

import kotlinx.serialization.Serializable

@Serializable
data class RankingDto(
    val weekLabel: String,
    val startups: List<StartupDto>,
    val isMock: Boolean = true,
    val updatedAt: String? = null,
    val searchQuery: String? = null,
    val articleCount: Int? = null,
)
