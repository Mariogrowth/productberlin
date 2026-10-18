package net.productberlin.domain.repository

import net.productberlin.domain.entity.WeeklyNews

interface NewsRepository {
    suspend fun getWeeklyNews(): WeeklyNews
}
