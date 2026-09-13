package net.productberlin.domain.repository

import net.productberlin.domain.entity.WeeklyRanking

interface StartupRepository {
    suspend fun getWeeklyRanking(): WeeklyRanking
}
