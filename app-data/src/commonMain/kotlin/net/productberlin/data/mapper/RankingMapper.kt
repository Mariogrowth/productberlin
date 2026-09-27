package net.productberlin.data.mapper

import net.productberlin.contract.RankingDto
import net.productberlin.domain.entity.WeeklyRanking

internal fun RankingDto.toDomain() = WeeklyRanking(weekLabel, startups.map { it.toDomain() }, isMock, updatedAt, searchQuery, articleCount)
