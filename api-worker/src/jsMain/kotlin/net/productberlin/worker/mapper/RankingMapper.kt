package net.productberlin.worker.mapper

import net.productberlin.contract.RankingDto
import net.productberlin.domain.entity.WeeklyRanking

internal fun WeeklyRanking.toDto() = RankingDto(weekLabel, startups.map { it.toDto() }, isMock, updatedAt, searchQuery, articleCount)
