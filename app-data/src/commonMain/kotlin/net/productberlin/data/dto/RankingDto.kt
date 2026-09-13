package net.productberlin.data.dto

import kotlinx.serialization.Serializable
import net.productberlin.domain.entity.WeeklyRanking

@Serializable
internal data class RankingDto(
    val weekLabel: String,
    val startups: List<StartupDto>,
) {
    fun toDomain() = WeeklyRanking(weekLabel, startups.map { it.toDomain() })
}
